package io.github.moreirasantos.pgkn

import io.github.moreirasantos.pgkn.exception.ConnectionClosedException
import io.github.moreirasantos.pgkn.exception.QueryExecutionException
import io.github.moreirasantos.pgkn.paramsource.MapSqlParameterSource
import io.github.moreirasantos.pgkn.sql.buildValueArray
import io.github.moreirasantos.pgkn.sql.parseSql
import io.github.moreirasantos.pgkn.sql.substituteNamedParameters
import io.github.moreirasantos.pgkn.resultset.PostgresResultSet
import kotlinx.cinterop.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import libpq.*
import io.github.kormium.ConnectionPool
import io.github.kormium.Dialect
import io.github.kormium.KormConfig
import io.github.kormium.PinnedConnection
import io.github.kormium.PostgresDialect
import io.github.kormium.PostgresDriver
import io.github.kormium.SqlExecutor
import io.github.kormium.SqlParameterSource
import io.github.kormium.StandardTypeMapper
import io.github.kormium.SuspendSqlExecutor
import io.github.kormium.TypeMapper
import io.github.kormium.database.SuspendDatabase
import io.github.kormium.resultset.ResultSet
import io.github.kormium.runConnection
import io.github.kormium.runPinned
import io.github.kormium.sqlException

private val logger = KLogger("io.github.moreirasantos.pgkn.PostgresDriverKt")

@OptIn(ExperimentalForeignApi::class)
fun FPostgresDriver(
    host: String,
    port: Int = 5432,
    database: String,
    user: String,
    password: String,
    poolSize: Int = 10,
    config: KormConfig = KormConfig(),
): PostgresDriver = PostgresDriverImpl(
    host = host,
    port = port,
    database = database,
    user = user,
    password = password,
    poolSize = poolSize,
    config = config,
)

@OptIn(ExperimentalForeignApi::class)
private class PostgresDriverImpl(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    private val poolSize: Int,
    override val config: KormConfig,
) : PostgresDriver, SuspendDatabase<Nothing> {

    init {
        require(poolSize >= 1) { "poolSize must be >= 1, was $poolSize" }
    }

    override val dialect: Dialect = PostgresDialect
    override val typeMapper: TypeMapper = StandardTypeMapper

    // A single libpq connection is not thread-safe: it runs one command at a time
    // and concurrent use is undefined behaviour. We therefore keep a fixed set of
    // connections and hand each out to exactly one caller at a time through a
    // Channel that doubles as a blocking "free connection" queue.
    //   poolSize = 1 serialises everything — the right setting behind PgBouncer.
    //   poolSize = N allows N concurrent statements, mirroring the JVM HikariCP pool.
    private val connections: List<CPointer<PGconn>> = List(poolSize) {
        openConnection(host, port, database, user, password)
    }

    private val pool = Channel<CPointer<PGconn>>(poolSize).also { channel ->
        connections.forEach { channel.trySend(it) }
    }

    // Held terminally by the first close() caller so the pool is torn down once.
    private val closeLock = Mutex()

    private inline fun <T> withConnection(block: (CPointer<PGconn>) -> T): T {
        // Fast path: take a free connection without spinning up a runBlocking loop.
        val connection = pool.tryReceive().getOrNull() ?: try {
            runBlocking { pool.receive() }
        } catch (_: ClosedReceiveChannelException) {
            throw ConnectionClosedException()
        }
        try {
            ensureAlive(connection)
            return block(connection)
        } finally {
            // Capacity == poolSize, so a borrowed connection always fits back in.
            pool.trySend(connection)
        }
    }

    // libpq marks a connection CONNECTION_BAD once it dies (e.g. the server restarts or
    // the network drops). Reset it before reuse so a transient outage doesn't permanently
    // poison the pooled connection. PQstatus is local (no round-trip); PQreset reconnects.
    private fun ensureAlive(connection: CPointer<PGconn>) {
        if (ConnStatusType.CONNECTION_OK == PQstatus(connection)) return
        PQreset(connection)
        if (ConnStatusType.CONNECTION_OK != PQstatus(connection)) {
            val message = PQerrorMessage(connection)?.toKString()?.trim().orEmpty()
            throw QueryExecutionException(message.ifEmpty { "Postgres connection is down and could not be reset" })
        }
    }

    override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> =
        withConnection { conn -> runQuery(conn, sql, namedParameters).handleResults(handler) }

    override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> =
        withConnection { conn -> doExecute(conn, sql, paramSource).handleResults(handler) }

    override fun execute(sql: String, namedParameters: Map<String, Any?>): Long =
        withConnection { conn -> runQuery(conn, sql, namedParameters).returnCount() }

    override fun execute(sql: String, paramSource: SqlParameterSource): Long =
        withConnection { conn -> doExecute(conn, sql, paramSource).returnCount() }

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>): Long =
        // returnCount() reads PQcmdTuples and PQclears the result, so nothing leaks.
        withConnection { conn -> runQuery(conn, sql, namedParameters).returnCount() }

    // One pool, two entry points (blocking usePinned + suspend useConnection). acquire mirrors
    // withConnection's borrow + liveness check; acquireSuspending overrides the default to take
    // the Channel's natural suspend path (pool.receive()) instead of offloading a blocking borrow.
    private val connectionPool = object : ConnectionPool {
        override fun acquire(): PinnedConnection {
            val connection = pool.tryReceive().getOrNull() ?: try {
                runBlocking { pool.receive() }
            } catch (_: ClosedReceiveChannelException) {
                throw ConnectionClosedException()
            }
            ensureAlive(connection)
            return PgPinnedConnection(connection)
        }

        override suspend fun acquireSuspending(): PinnedConnection {
            val connection = pool.tryReceive().getOrNull() ?: try {
                pool.receive()
            } catch (_: ClosedReceiveChannelException) {
                throw ConnectionClosedException()
            }
            ensureAlive(connection)
            return PgPinnedConnection(connection)
        }
    }

    private inner class PgPinnedConnection(private val conn: CPointer<PGconn>) : PinnedConnection {
        override val executor: SqlExecutor = NativeExecutor(conn)
        override fun begin() { PQclear(doExecute(conn, "BEGIN")) }
        override fun commit() { PQclear(doExecute(conn, "COMMIT")) }
        override fun rollback() { PQclear(doExecute(conn, "ROLLBACK")) }
        // Capacity == poolSize, so a borrowed connection always fits back in.
        override fun release() { pool.trySend(conn) }
    }

    override fun <R> usePinned(transactional: Boolean, block: (SqlExecutor) -> R): R =
        connectionPool.runPinned(transactional, block)

    override suspend fun <R> useConnection(transactional: Boolean, block: suspend (SuspendSqlExecutor) -> R): R =
        connectionPool.runConnection(transactional, block)

    // An SqlExecutor bound to the pinned connection for the duration of usePinned.
    private inner class NativeExecutor(private val conn: CPointer<PGconn>) : SqlExecutor {
        override val dialect = PostgresDialect
        override val typeMapper = StandardTypeMapper

        override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T) =
            runQuery(conn, sql, namedParameters).handleResults(handler)

        override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T) =
            doExecute(conn, sql, paramSource).handleResults(handler)

        override fun execute(sql: String, namedParameters: Map<String, Any?>) =
            runQuery(conn, sql, namedParameters).returnCount()

        override fun execute(sql: String, paramSource: SqlParameterSource) =
            doExecute(conn, sql, paramSource).returnCount()

        override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>): Long =
            runQuery(conn, sql, namedParameters).returnCount()
    }

    override fun close() {
        // tryLock() succeeds only for the first caller; the lock is never released,
        // making close() idempotent. Concurrent/later callers return immediately.
        if (!closeLock.tryLock()) return
        // Drain the pool before finishing: receiving every connection waits for any
        // in-flight statement to return its connection, so we never PQfinish one that
        // another thread is still using.
        runBlocking { repeat(poolSize) { PQfinish(pool.receive()) } }
        pool.close()
    }

    private fun runQuery(
        connection: CPointer<PGconn>,
        sql: String,
        namedParameters: Map<String, Any?>,
    ): CPointer<PGresult> =
        if (namedParameters.isEmpty()) doExecute(connection, sql)
        else doExecute(connection, sql, MapSqlParameterSource(namedParameters))

    private fun <T> CPointer<PGresult>.handleResults(handler: (ResultSet) -> T): List<T> {
        val rs = PostgresResultSet(this)

        val list: MutableList<T> = mutableListOf()
        while (rs.next()) {
            list.add(handler(rs))
        }

        PQclear(this)
        return list
    }

    private fun CPointer<PGresult>.returnCount(): Long {
        val rows = PQcmdTuples(this)!!.toKString()
        PQclear(this)
        return rows.toLongOrNull() ?: 0
    }

    private fun doExecute(
        connection: CPointer<PGconn>,
        sql: String,
        paramSource: SqlParameterSource,
    ): CPointer<PGresult> {
        val parsedSql = parseSql(sql)
        val sqlToUse: String = substituteNamedParameters(parsedSql, paramSource)
        val params: Array<Any?> = buildValueArray(parsedSql, paramSource)

        return memScoped {
            PQexecParams(
                connection,
                command = sqlToUse,
                nParams = params.size,
                paramValues = createValues(params.size) {
                    logger.trace { "param[$it]: ${params[it]}" }
                    value = params[it]?.toString()?.cstr?.getPointer(this@memScoped)
                },
                paramLengths = params.map { it?.toString()?.length ?: 0 }.toIntArray().refTo(0),
                paramFormats = IntArray(params.size) { TEXT_RESULT_FORMAT }.refTo(0),
                // Bind every parameter as unspecified type (oid 0) so the server infers
                // each type from its column context. Values are sent as text, so declaring
                // text(25) for a numeric/timestamp column fails; 0u lets Postgres cast.
                // Mirrors the JVM driver's stringtype=unspecified.
                paramTypes = UIntArray(params.size) { 0u }.refTo(0),
                resultFormat = TEXT_RESULT_FORMAT
            )
        }.check(connection)
    }

    private fun doExecute(connection: CPointer<PGconn>, sql: String) = memScoped {
        PQexecParams(
            connection,
            command = sql,
            nParams = 0,
            paramValues = createValues(0) {},
            paramLengths = createValues(0) {},
            paramFormats = createValues(0) {},
            paramTypes = createValues(0) {},
            resultFormat = TEXT_RESULT_FORMAT
        )
    }.check(connection)

    // Validates a statement result. On failure it frees the failed result and raises
    // an exception WITHOUT closing the connection: an ordinary query error (e.g. a
    // constraint violation) must leave the connection usable for the next caller.
    // Previously the error path called PQfinish, so a single bad statement
    // permanently broke the connection (and, being pooled/shared, every later call).
    private fun CPointer<PGresult>?.check(connection: CPointer<PGconn>): CPointer<PGresult> {
        val status = PQresultStatus(this)
        if (status != PGRES_TUPLES_OK && status != PGRES_COMMAND_OK && status != PGRES_COPY_IN) {
            // PG_DIAG_SQLSTATE is 'C'; map the SQLSTATE to a typed core exception.
            val sqlState = PQresultErrorField(this, 'C'.code)?.toKString()
            val message = PQerrorMessage(connection)?.toKString()?.trim().orEmpty()
            PQclear(this)
            throw sqlException(message.ifEmpty { "Postgres query failed" }, sqlState)
        }
        return this!!
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun openConnection(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
): CPointer<PGconn> {
    val connection = PQsetdbLogin(
        pghost = host,
        pgport = port.toString(),
        dbName = database,
        login = user,
        pwd = password,
        pgoptions = null,
        pgtty = null,
    )
    requireNotNull(connection) { "Failed to allocate a Postgres connection" }
    if (ConnStatusType.CONNECTION_OK != PQstatus(connection)) {
        val message = PQerrorMessage(connection)?.toKString()?.trim().orEmpty()
        PQfinish(connection)
        throw QueryExecutionException(message.ifEmpty { "Failed to connect to Postgres" })
    }
    return connection
}

private const val TEXT_RESULT_FORMAT = 0

@Suppress("UnusedPrivateProperty")
private const val BINARY_RESULT_FORMAT = 1
