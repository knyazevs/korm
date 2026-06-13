import kotlinx.cinterop.*
import libpq.*
import platform.posix.POLLIN
import platform.posix.POLLOUT
import platform.posix.getenv
import platform.posix.poll
import platform.posix.pollfd
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * M1 de-risk for the async (#2) reactor: proves the libpq async API works on Kotlin/Native
 * end to end. Drives `PQsendQuery` + socket `poll` + `PQconsumeInput`/`PQisBusy` -> `PQgetResult`
 * and checks the rows come back correct. The poll here blocks the calling thread on purpose —
 * M2 replaces it with a shared reactor thread so no coroutine thread is held during the wait.
 */
@OptIn(ExperimentalForeignApi::class)
class AsyncQueryPrototypeTest {

    private fun env(name: String) = getenv(name)?.toKString()

    private fun open(): CPointer<PGconn> {
        val conninfo = "host=${env("KORMIUM_DB_HOST") ?: "127.0.0.1"} " +
            "port=${env("KORMIUM_DB_PORT") ?: "5432"} " +
            "dbname=${env("KORMIUM_DB_NAME") ?: "postgres"} " +
            "user=${env("KORMIUM_DB_USER") ?: "postgres"} " +
            "password=${env("KORMIUM_DB_PASSWORD") ?: "password"}"
        val conn = PQconnectdb(conninfo)
        check(conn != null && PQstatus(conn) == ConnStatusType.CONNECTION_OK) {
            PQerrorMessage(conn)?.toKString().orEmpty()
        }
        // Non-blocking so a full send buffer reports back via PQflush instead of blocking.
        check(PQsetnonblocking(conn, 1) == 0)
        return conn
    }

    /** Blocks the current thread until [sock] is ready for the given poll [events]. */
    private fun waitFor(sock: Int, events: Int) = memScoped {
        val pfd = alloc<pollfd>()
        pfd.fd = sock
        pfd.events = events.toShort()
        poll(pfd.ptr, 1.convert(), -1)
        Unit
    }

    /** Runs one single-column bigint query through the async libpq path. */
    private fun asyncQueryLongs(conn: CPointer<PGconn>, sql: String): List<Long> {
        val sock = PQsocket(conn)
        check(PQsendQuery(conn, sql) == 1) { "send failed: " + PQerrorMessage(conn)?.toKString() }

        // Drain the outgoing buffer; PQflush returns 1 while bytes remain, 0 when done, -1 on error.
        while (true) {
            when (PQflush(conn)) {
                0 -> break
                -1 -> error("flush failed: " + PQerrorMessage(conn)?.toKString())
                else -> waitFor(sock, POLLOUT)
            }
        }

        val out = mutableListOf<Long>()
        while (true) {
            // Wait until a full result is buffered before PQgetResult, so it never blocks.
            while (PQisBusy(conn) == 1) {
                waitFor(sock, POLLIN)
                check(PQconsumeInput(conn) == 1) { "consume failed: " + PQerrorMessage(conn)?.toKString() }
            }
            val res = PQgetResult(conn) ?: break
            val status = PQresultStatus(res)
            check(status == PGRES_TUPLES_OK || status == PGRES_COMMAND_OK) {
                val msg = PQerrorMessage(conn)?.toKString().orEmpty()
                PQclear(res); msg
            }
            val rows = PQntuples(res)
            for (i in 0 until rows) out.add(PQgetvalue(res, i, 0)!!.toKString().toLong())
            PQclear(res)
        }
        return out
    }

    @Test
    fun asyncSelectReturnsCorrectRows() {
        if (env("KORMIUM_DB_HOST") == null) {
            println("KORMIUM_DB_HOST not set — skipping async prototype test")
            return
        }
        val conn = open()
        try {
            val rows = asyncQueryLongs(conn, "SELECT g FROM generate_series(1, 1000) g")
            assertEquals(1000, rows.size)
            assertEquals(500500L, rows.sum())
            // Second query on the same connection: the async loop must leave it reusable.
            val again = asyncQueryLongs(conn, "SELECT 42")
            assertEquals(listOf(42L), again)
        } finally {
            PQfinish(conn)
        }
    }
}
