package io.github.knyazevs.korm

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.knyazevs.korm.resultset.ResultSet
import java.sql.Connection
import java.sql.PreparedStatement


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

private class PostgresDriverImpl(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    poolSize: Int,
) : PostgresDriver {

    val config = HikariConfig().apply {
        // stringtype=unspecified lets the server infer a parameter's type from its
        // column context, so text-bound values (BigDecimal, uuid, timestamps, ...)
        // are accepted by numeric/uuid/timestamp columns — matching the native
        // (libpq) driver, which sends all parameters as untyped text.
        // prepareThreshold=1 makes pgjdbc use a server-side prepared statement from the first
        // execution (default is 5), so repeated statements skip re-parsing on the server.
        // (The MySQL-style cachePrepStmts/prepStmtCacheSize properties are ignored by pgjdbc.)
        this.setJdbcUrl("jdbc:postgresql://$host:$port/$database?stringtype=unspecified&prepareThreshold=1")
        this.username = user
        this.password = password
        this.maximumPoolSize = poolSize
    }
    var ds = HikariDataSource(config)

    override val dialect: Dialect = PostgresDialect
    override val typeMapper: TypeMapper = StandardTypeMapper

    override fun close() = ds.close()

    override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> =
        ds.connection.use { it.runQuery(sql, namedParameters, handler) }

    override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> =
        ds.connection.use { it.runQuery(sql, paramSource, handler) }

    override fun execute(sql: String, namedParameters: Map<String, Any?>): Long =
        ds.connection.use { it.runCount(sql, namedParameters) }

    override fun execute(sql: String, paramSource: SqlParameterSource): Long =
        ds.connection.use { it.runCount(sql, paramSource) }

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>) {
        ds.connection.use { it.runUpdate(sql, namedParameters) }
    }

    override fun <R> usePinned(transactional: Boolean, block: (SqlExecutor) -> R): R =
        ds.connection.use { conn ->
            val executor = JdbcExecutor(conn)
            if (!transactional) return@use block(executor)
            val previousAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                val result = block(executor)
                translateSql { conn.commit() }
                result
            } catch (e: Throwable) {
                runCatching { conn.rollback() }
                throw e
            } finally {
                runCatching { conn.autoCommit = previousAutoCommit }
            }
        }
}

/** An [SqlExecutor] bound to one already-open connection, used inside [usePinned]. */
private class JdbcExecutor(private val conn: Connection) : SqlExecutor {
    override val dialect: Dialect = PostgresDialect
    override val typeMapper: TypeMapper = StandardTypeMapper

    override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> =
        conn.runQuery(sql, namedParameters, handler)

    override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> =
        conn.runQuery(sql, paramSource, handler)

    override fun execute(sql: String, namedParameters: Map<String, Any?>): Long =
        conn.runCount(sql, namedParameters)

    override fun execute(sql: String, paramSource: SqlParameterSource): Long =
        conn.runCount(sql, paramSource)

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>) =
        conn.runUpdate(sql, namedParameters)
}

// Translates JDBC SQLExceptions into korm's typed exceptions, carrying the SQLSTATE.
private inline fun <T> translateSql(block: () -> T): T =
    try {
        block()
    } catch (e: java.sql.SQLException) {
        throw io.github.knyazevs.korm.sqlException(e.message ?: "SQL error", e.sqlState, e)
    }

private fun <T> Connection.runQuery(
    sql: String,
    namedParameters: Map<String, Any?>,
    handler: (ResultSet) -> T,
): List<T> = translateSql {
    val statement = NamedParamStatement(this, sql)
    for ((key, value) in namedParameters) statement.setAny(key, value)
    PgResultSetWrapper(statement.executeQuery()).handleResults(handler)
}

private fun <T> Connection.runQuery(
    sql: String,
    paramSource: SqlParameterSource,
    handler: (ResultSet) -> T,
): List<T> = translateSql {
    val statement = NamedParamStatement(this, sql)
    statement.bind(paramSource)
    PgResultSetWrapper(statement.executeQuery()).handleResults(handler)
}

private fun Connection.runCount(sql: String, namedParameters: Map<String, Any?>): Long = translateSql {
    val statement = NamedParamStatement(this, sql)
    for ((key, value) in namedParameters) statement.setAny(key, value)
    statement.preparedStatement.runReturningCount()
}

private fun Connection.runCount(sql: String, paramSource: SqlParameterSource): Long = translateSql {
    val statement = NamedParamStatement(this, sql)
    statement.bind(paramSource)
    statement.preparedStatement.runReturningCount()
}

private fun Connection.runUpdate(sql: String, namedParameters: Map<String, Any?>) = translateSql {
    val statement = NamedParamStatement(this, sql)
    for ((key, value) in namedParameters) statement.setAny(key, value)
    statement.executeUpdate()
    Unit
}

/**
 * Runs any statement (DDL, DML or query) and returns a row count for queries or the
 * update count otherwise. Uses [PreparedStatement.execute] so it works for statements
 * that do not produce a result set (e.g. CREATE TABLE).
 */
private fun PreparedStatement.runReturningCount(): Long =
    if (execute()) {
        resultSet.use { rs ->
            var size = 0L
            while (rs.next()) size++
            size
        }
    } else {
        updateCount.toLong()
    }

private fun <T> ResultSet.handleResults(handler: (ResultSet) -> T): List<T> {
    val list: MutableList<T> = mutableListOf()
    while (next()) {
        list.add(handler(this))
    }
    return list
}
