package io.github.moreirasantos.pgkn

import io.github.moreirasantos.pgkn.exception.ConnectionClosedException
import io.github.moreirasantos.pgkn.exception.InvalidDataAccessApiUsageException
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
import io.github.kormium.KormiumConfig
import io.github.kormium.PinnedConnection
import io.github.kormium.WriteListeners
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

@OptIn(ExperimentalForeignApi::class)
fun FPostgresDriver(
    host: String,
    port: Int = 5432,
    database: String,
    user: String,
    password: String,
    poolSize: Int = 10,
    config: KormiumConfig = KormiumConfig(),
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
    override val config: KormiumConfig,
) : PostgresDriver, SuspendDatabase<Nothing> {

    init {
        require(poolSize >= 1) { "poolSize must be >= 1, was $poolSize" }
    }

    private val dialect: Dialect = PostgresDialect
    private val typeMapper: TypeMapper = StandardTypeMapper
    override val writeListeners: WriteListeners = WriteListeners()

    // A single libpq connection is not thread-safe: it runs one command at a time
    // and concurrent use is undefined behaviour. We therefore keep a fixed set of
    // connections and hand each out to exactly one caller at a time through a
    // Channel that doubles as a blocking "free connection" queue.
    //   poolSize = 1 serialises everything — the right setting behind PgBouncer.
    //   poolSize = N allows N concurrent statements, mirroring the JVM HikariCP pool.
    private val connections: List<CPointer<PGconn>> = List(poolSize) {
        openConnection(host, port, database, user, password)
    }

    // Parsed-statement cache, one per connection: the pool hands a connection to exactly
    // one caller at a time, so its cache is only ever touched by the holder — no locking.
    // (The Channel's send/receive provides the happens-before edge between holders.)
    private val statementCaches: Map<CPointer<PGconn>, StatementCache> =
        connections.associateWith { StatementCache() }

    private val pool = Channel<CPointer<PGconn>>(poolSize).also { channel ->
        connections.forEach { channel.trySend(it) }
    }

    // Held terminally by the first close() caller so the pool is torn down once.
    private val closeLock = Mutex()

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

        // Presize to the row count so a multi-row read doesn't repeatedly grow + copy the
        // backing array (libpq already has the full result buffered, so the count is known).
        val list: MutableList<T> = ArrayList(rs.rowCount)
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
        val prepared = prepareStatement(statementCaches.getValue(connection), sql, paramSource)
            ?: return doExecuteUncached(connection, sql, paramSource)
        val values = Array(prepared.parameterNames.size) { i ->
            val name = prepared.parameterNames[i]
            try {
                paramSource.getValue(name)?.toString()
            } catch (ex: IllegalArgumentException) {
                throw InvalidDataAccessApiUsageException("No value supplied for the SQL parameter '$name'", ex)
            }
        }
        return execParams(connection, prepared.sql, values)
    }

    // Parse + substitution is identical across repeated calls of the same statement — cache it
    // per connection. Returns null when the statement must take the per-call path instead:
    // '?'-style positional parameters (not bound by name), or Iterable/Array values, whose
    // placeholder expansion depends on the value's size.
    private fun prepareStatement(
        cache: StatementCache,
        sql: String,
        paramSource: SqlParameterSource,
    ): PreparedSql? {
        cache.get(sql)?.let { hit ->
            return if (hit.parameterNames.any { isExpandable(paramSource, it) }) null else hit
        }
        val parsedSql = parseSql(sql)
        if (parsedSql.unnamedParameterCount > 0) return null
        if (parsedSql.parameterNames.any { isExpandable(paramSource, it) }) return null
        val prepared = PreparedSql(substituteNamedParameters(parsedSql, null), parsedSql.parameterNames.toList())
        cache.put(sql, prepared)
        return prepared
    }

    private fun isExpandable(paramSource: SqlParameterSource, name: String): Boolean {
        if (!paramSource.hasValue(name)) return false
        val value = paramSource.getValue(name)
        return value is Iterable<*> || value is Array<*>
    }

    // The pre-cache execution path, kept for statements prepareStatement can't handle:
    // expansion and substitution are recomputed against the actual values on every call.
    private fun doExecuteUncached(
        connection: CPointer<PGconn>,
        sql: String,
        paramSource: SqlParameterSource,
    ): CPointer<PGresult> {
        val parsedSql = parseSql(sql)
        val sqlToUse: String = substituteNamedParameters(parsedSql, paramSource)
        val params: Array<Any?> = buildValueArray(parsedSql, paramSource)
        return execParams(connection, sqlToUse, Array(params.size) { params[it]?.toString() })
    }

    private fun execParams(
        connection: CPointer<PGconn>,
        sql: String,
        values: Array<String?>,
    ): CPointer<PGresult> = memScoped {
        PQexecParams(
            connection,
            command = sql,
            nParams = values.size,
            paramValues = createValues(values.size) {
                value = values[it]?.cstr?.getPointer(this@memScoped)
            },
            // Text-format parameters are null-terminated, so lengths are ignored; null
            // formats means "all text". Null types binds every parameter as unspecified
            // (oid 0) so the server infers each type from its column context — declaring
            // text(25) for a numeric/timestamp column would fail, oid 0 lets Postgres
            // cast. Mirrors the JVM driver's stringtype=unspecified.
            paramLengths = null,
            paramFormats = null,
            paramTypes = null,
            resultFormat = TEXT_RESULT_FORMAT
        )
    }.check(connection)

    // Parameter-less statements (BEGIN/COMMIT/ROLLBACK around every transaction, DDL, SET,
    // bare SELECTs) go through the simple query protocol via PQexec: a single Query message
    // instead of PQexecParams' Parse/Bind/Describe/Execute/Sync exchange, and no memScoped /
    // createValues allocation. Same one round trip, less protocol and server-side overhead.
    private fun doExecute(connection: CPointer<PGconn>, sql: String) =
        PQexec(connection, sql).check(connection)

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
): CPointer<PGconn> = memScoped {
    // PQconnectdbParams (over the older PQsetdbLogin) lets us set production-grade
    // connection defaults: connect_timeout bounds a dead host instead of hanging
    // indefinitely, keepalives detect a silently dropped TCP connection, and
    // application_name labels the backend in pg_stat_activity. Both arrays must be
    // NULL-terminated, hence the trailing null entry.
    val keywords = listOf(
        "host", "port", "dbname", "user", "password",
        "connect_timeout", "keepalives", "application_name",
    )
    val values = listOf(
        host, port.toString(), database, user, password,
        "10", "1", "kormium",
    )
    val keywordsArray = allocArrayOf(keywords.map { it.cstr.getPointer(this) } + listOf<CPointer<ByteVar>?>(null))
    val valuesArray = allocArrayOf(values.map { it.cstr.getPointer(this) } + listOf<CPointer<ByteVar>?>(null))

    val connection = PQconnectdbParams(keywordsArray, valuesArray, 0)
    requireNotNull(connection) { "Failed to allocate a Postgres connection" }
    if (ConnStatusType.CONNECTION_OK != PQstatus(connection)) {
        val message = PQerrorMessage(connection)?.toKString()?.trim().orEmpty()
        PQfinish(connection)
        throw QueryExecutionException(message.ifEmpty { "Failed to connect to Postgres" })
    }
    connection
}

// A statement ready for PQexecParams: the `$n`-substituted SQL and the named-parameter
// occurrence order used to build the positional value array.
private class PreparedSql(val sql: String, val parameterNames: List<String>)

/**
 * A small LRU of [PreparedSql] keyed by the original SQL text. LinkedHashMap keeps
 * insertion order, so a hit re-inserts the entry to move it to the tail and eviction
 * drops the head — the least recently used statement. Bounded so one-off SQL (large
 * IN-lists, ad-hoc queries) cannot grow it without limit.
 */
private class StatementCache(private val maxEntries: Int = 128) {
    private val map = LinkedHashMap<String, PreparedSql>()

    fun get(sql: String): PreparedSql? {
        val hit = map.remove(sql) ?: return null
        map[sql] = hit
        return hit
    }

    fun put(sql: String, prepared: PreparedSql) {
        if (map.size >= maxEntries) map.remove(map.keys.first())
        map[sql] = prepared
    }
}

private const val TEXT_RESULT_FORMAT = 0

@Suppress("UnusedPrivateProperty")
private const val BINARY_RESULT_FORMAT = 1
