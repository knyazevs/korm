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
import io.github.knyazevs.korm.Dialect
import io.github.knyazevs.korm.PostgresDialect
import io.github.knyazevs.korm.PostgresDriver
import io.github.knyazevs.korm.SqlParameterSource
import io.github.knyazevs.korm.StandardTypeMapper
import io.github.knyazevs.korm.TypeMapper
import io.github.knyazevs.korm.resultset.ResultSet

private val logger = KLogger("io.github.moreirasantos.pgkn.PostgresDriverKt")

@OptIn(ExperimentalForeignApi::class)
fun FPostgresDriver(
    host: String,
    port: Int = 5432,
    database: String,
    user: String,
    password: String,
    poolSize: Int = 10,
): PostgresDriver = PostgresDriverImpl(
    host = host,
    port = port,
    database = database,
    user = user,
    password = password,
    poolSize = poolSize,
)

@OptIn(ExperimentalForeignApi::class)
private class PostgresDriverImpl(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    private val poolSize: Int,
) : PostgresDriver {

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
            return block(connection)
        } finally {
            // Capacity == poolSize, so a borrowed connection always fits back in.
            pool.trySend(connection)
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

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>) =
        withConnection { conn ->
            // Free the result even though it is discarded — PQclear used to be skipped
            // here, leaking the PGresult for every parameterless update / DDL statement.
            PQclear(runQuery(conn, sql, namedParameters))
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
            val message = PQerrorMessage(connection)?.toKString()?.trim().orEmpty()
            PQclear(this)
            throw QueryExecutionException(message.ifEmpty { "Postgres query failed" })
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
