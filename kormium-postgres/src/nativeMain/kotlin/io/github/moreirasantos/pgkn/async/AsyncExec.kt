package io.github.moreirasantos.pgkn.async

import kotlinx.cinterop.*
import libpq.PGconn
import libpq.PGresult
import libpq.PQclear
import libpq.PQconsumeInput
import libpq.PQerrorMessage
import libpq.PQflush
import libpq.PQgetResult
import libpq.PQgetvalue
import libpq.PQisBusy
import libpq.PQntuples
import libpq.PQsendQuery
import libpq.PQsendQueryParams
import libpq.PQsocket

/**
 * Asynchronous execution over libpq: the query is sent, then the coroutine suspends on the
 * [SocketReactor] (never blocking its thread) until the socket drains the outgoing buffer and
 * delivers the result. The connection must be non-blocking (`PQsetnonblocking`). These mirror
 * the blocking `PQexec`/`PQexecParams` paths and return the raw `PGresult` for the caller to
 * validate and map — exactly as the blocking path feeds it to the shared ResultSet code.
 */

private const val TEXT_RESULT_FORMAT = 0

/** Async equivalent of the parameter-less `PQexec` path (BEGIN/COMMIT/ROLLBACK, DDL, SET). */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun asyncExecSimple(
    conn: CPointer<PGconn>,
    sql: String,
    reactor: SocketReactor,
): CPointer<PGresult>? {
    check(PQsendQuery(conn, sql) == 1) { "send failed: " + PQerrorMessage(conn)?.toKString() }
    return drainResult(conn, reactor)
}

/** Async equivalent of the `PQexecParams` path: text parameters, server-inferred types (oid 0). */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun asyncExecParams(
    conn: CPointer<PGconn>,
    sql: String,
    values: Array<String?>,
    reactor: SocketReactor,
): CPointer<PGresult>? {
    memScoped {
        val sent = PQsendQueryParams(
            conn,
            command = sql,
            nParams = values.size,
            paramTypes = null,
            paramValues = createValues(values.size) {
                value = values[it]?.cstr?.getPointer(this@memScoped)
            },
            paramLengths = null,
            paramFormats = null,
            resultFormat = TEXT_RESULT_FORMAT,
        )
        check(sent == 1) { "send failed: " + PQerrorMessage(conn)?.toKString() }
    }
    return drainResult(conn, reactor)
}

/**
 * Flushes the send buffer and collects results, suspending on the reactor at every wait. Returns
 * the first result (the data/command result for a single statement) and clears any extras so the
 * connection is left clean for the next statement.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun drainResult(conn: CPointer<PGconn>, reactor: SocketReactor): CPointer<PGresult>? {
    val sock = PQsocket(conn)
    while (true) {
        when (PQflush(conn)) {
            0 -> break
            -1 -> error("flush failed: " + PQerrorMessage(conn)?.toKString())
            else -> reactor.awaitWritable(sock)
        }
    }
    var first: CPointer<PGresult>? = null
    while (true) {
        while (PQisBusy(conn) == 1) {
            reactor.awaitReadable(sock)
            check(PQconsumeInput(conn) == 1) { "consume failed: " + PQerrorMessage(conn)?.toKString() }
        }
        val res = PQgetResult(conn) ?: break
        if (first == null) first = res else PQclear(res)
    }
    return first
}

/** Test helper: runs [sql] async and returns column 0 of the result as text. */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun asyncQueryFirstColumn(
    conn: CPointer<PGconn>,
    sql: String,
    reactor: SocketReactor,
): List<String?> {
    val res = asyncExecSimple(conn, sql, reactor) ?: return emptyList()
    val rows = (0 until PQntuples(res)).map { PQgetvalue(res, it, 0)?.toKString() }
    PQclear(res)
    return rows
}
