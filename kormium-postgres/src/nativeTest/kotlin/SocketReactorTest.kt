import io.github.moreirasantos.pgkn.async.asyncQueryFirstColumn
import io.github.moreirasantos.pgkn.async.createSocketReactor
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import libpq.ConnStatusType
import libpq.PGconn
import libpq.PQconnectdb
import libpq.PQerrorMessage
import libpq.PQfinish
import libpq.PQsetnonblocking
import libpq.PQstatus
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalForeignApi::class, DelicateCoroutinesApi::class)
class SocketReactorTest {

    private fun env(name: String) = getenv(name)?.toKString()

    private fun openNonBlocking(): CPointer<PGconn> {
        val conninfo = "host=${env("KORMIUM_DB_HOST") ?: "127.0.0.1"} " +
            "port=${env("KORMIUM_DB_PORT") ?: "5432"} " +
            "dbname=${env("KORMIUM_DB_NAME") ?: "postgres"} " +
            "user=${env("KORMIUM_DB_USER") ?: "postgres"} " +
            "password=${env("KORMIUM_DB_PASSWORD") ?: "password"}"
        val conn = PQconnectdb(conninfo)
        check(conn != null && PQstatus(conn) == ConnStatusType.CONNECTION_OK) {
            PQerrorMessage(conn)?.toKString().orEmpty()
        }
        check(PQsetnonblocking(conn, 1) == 0)
        return conn
    }

    /** Isolates the reactor mechanics: one async query on the default dispatcher. */
    @Test
    fun singleAsyncQuery() {
        if (env("KORMIUM_DB_HOST") == null) {
            println("KORMIUM_DB_HOST not set — skipping reactor test"); return
        }
        val reactor = createSocketReactor()
        val conn = openNonBlocking()
        try {
            val result = runBlocking {
                withTimeout(5.seconds) { asyncQueryFirstColumn(conn, "SELECT 7", reactor) }
            }
            assertEquals(listOf("7"), result)
        } finally {
            PQfinish(conn); reactor.close()
        }
    }

    @Test
    fun concurrentQueriesReturnTheirOwnResults() {
        if (env("KORMIUM_DB_HOST") == null) {
            println("KORMIUM_DB_HOST not set — skipping reactor test"); return
        }
        val reactor = createSocketReactor()
        val conns = List(8) { openNonBlocking() }
        try {
            val results = runBlocking {
                withTimeout(10.seconds) {
                    coroutineScope {
                        conns.mapIndexed { i, c ->
                            async { i to asyncQueryFirstColumn(c, "SELECT ${i * 10}", reactor).single() }
                        }.awaitAll()
                    }
                }
            }
            results.forEach { (i, value) -> assertEquals("${i * 10}", value) }
        } finally {
            conns.forEach { PQfinish(it) }; reactor.close()
        }
    }

    /** The payoff: eight 0.3s sleeps on ONE client thread overlap (~0.3s) instead of serialising (~2.4s). */
    @Test
    fun concurrentSleepsOverlapOnOneThread() {
        if (env("KORMIUM_DB_HOST") == null) {
            println("KORMIUM_DB_HOST not set — skipping reactor test"); return
        }
        val reactor = createSocketReactor()
        val conns = List(8) { openNonBlocking() }
        val client = newSingleThreadContext("reactor-test-client")
        try {
            val mark = TimeSource.Monotonic.markNow()
            runBlocking(client) {
                withTimeout(10.seconds) {
                    coroutineScope {
                        conns.map { c -> async { asyncQueryFirstColumn(c, "SELECT pg_sleep(0.3)", reactor) } }.awaitAll()
                    }
                }
            }
            val elapsed = mark.elapsedNow()
            assertTrue(elapsed < 1.5.seconds, "8 concurrent sleeps took $elapsed; expected overlap (<1.5s)")
        } finally {
            client.close(); conns.forEach { PQfinish(it) }; reactor.close()
        }
    }
}
