package io.github.moreirasantos.pgkn.async

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import libpq.PGconn
import libpq.PGRES_COMMAND_OK
import libpq.PGRES_TUPLES_OK
import libpq.PQconsumeInput
import libpq.PQerrorMessage
import libpq.PQflush
import libpq.PQgetResult
import libpq.PQgetvalue
import libpq.PQisBusy
import libpq.PQntuples
import libpq.PQresultStatus
import libpq.PQsendQuery
import libpq.PQsocket
import libpq.PQclear

/**
 * Runs [sql] on [conn] through libpq's asynchronous API, suspending on [reactor] (never
 * blocking the calling thread) while waiting for the socket. The connection must be in
 * non-blocking mode (`PQsetnonblocking`). Drains every result and returns the rows of the
 * last one as the text values of column 0 — enough for the M2 concurrency proof; the full
 * driver integration (M3) maps rows through the existing ResultSet path instead.
 */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun asyncQueryFirstColumn(
    conn: CPointer<PGconn>,
    sql: String,
    reactor: SocketReactor,
): List<String?> {
    val sock = PQsocket(conn)
    check(PQsendQuery(conn, sql) == 1) { "send failed: " + PQerrorMessage(conn)?.toKString() }

    // Flush the outgoing buffer; 1 = bytes remain (wait until writable), 0 = done, -1 = error.
    while (true) {
        when (PQflush(conn)) {
            0 -> break
            -1 -> error("flush failed: " + PQerrorMessage(conn)?.toKString())
            else -> reactor.awaitWritable(sock)
        }
    }

    var rows: List<String?> = emptyList()
    while (true) {
        // Wait until a full result is buffered so PQgetResult never blocks.
        while (PQisBusy(conn) == 1) {
            reactor.awaitReadable(sock)
            check(PQconsumeInput(conn) == 1) { "consume failed: " + PQerrorMessage(conn)?.toKString() }
        }
        val res = PQgetResult(conn) ?: break
        val status = PQresultStatus(res)
        check(status == PGRES_TUPLES_OK || status == PGRES_COMMAND_OK) {
            val msg = PQerrorMessage(conn)?.toKString().orEmpty()
            PQclear(res)
            msg
        }
        val n = PQntuples(res)
        rows = (0 until n).map { PQgetvalue(res, it, 0)?.toKString() }
        PQclear(res)
    }
    return rows
}
