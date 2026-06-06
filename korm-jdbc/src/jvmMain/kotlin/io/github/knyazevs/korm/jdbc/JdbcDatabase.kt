package io.github.knyazevs.korm.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.knyazevs.korm.Dialect
import io.github.knyazevs.korm.SqlExecutor
import io.github.knyazevs.korm.SqlParameterSource
import io.github.knyazevs.korm.TypeMapper
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.resultset.ResultSet
import io.github.knyazevs.korm.sqlException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException

/** Wraps a driver [java.sql.ResultSet] in core's backend-agnostic [ResultSet]. */
typealias ResultSetWrapper = (java.sql.ResultSet) -> ResultSet

/**
 * Translates a JDBC [SQLException] into a korm exception. Backends differ in how
 * they report constraint violations (Postgres via SQLSTATE, SQLite via result
 * codes), so each supplies its own mapping. The default maps the standard SQLSTATE.
 */
typealias SqlExceptionTranslator = (SQLException) -> Throwable

/** The default translator: maps the JDBC SQLSTATE to a typed core exception. */
val StandardSqlExceptionTranslator: SqlExceptionTranslator =
    { e -> sqlException(e.message ?: "SQL error", e.sqlState, e) }

/**
 * A generic JDBC-backed [Database], shared by every JDBC backend. It owns a HikariCP
 * connection pool and routes statements through a [JdbcExecutor]; the backend-specific
 * pieces — [dialect], [typeMapper], the [ResultSet] wrapper and the exception
 * [translate]or — are supplied by the concrete driver (Postgres, SQLite, ...).
 *
 * Open so a backend can subclass it purely to add its marker interface (e.g.
 * `class PostgresJdbcDriver(...) : JdbcDatabase(...), PostgresDriver`).
 */
open class JdbcDatabase(
    jdbcUrl: String,
    username: String? = null,
    password: String? = null,
    poolSize: Int,
    override val dialect: Dialect,
    override val typeMapper: TypeMapper,
    private val wrap: ResultSetWrapper,
    private val translate: SqlExceptionTranslator = StandardSqlExceptionTranslator,
    connectionInitSql: String? = null,
) : Database<Nothing> {

    private val ds: HikariDataSource = HikariDataSource(HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        if (username != null) this.username = username
        if (password != null) this.password = password
        this.maximumPoolSize = poolSize
        if (connectionInitSql != null) this.connectionInitSql = connectionInitSql
    })

    override fun close() = ds.close()

    // Each pool checkout gets a transient executor bound to that connection; all the
    // statement logic lives in JdbcExecutor so the pooled and pinned paths share it.
    private fun executor(conn: Connection) = JdbcExecutor(conn, dialect, typeMapper, wrap, translate)

    override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> =
        ds.connection.use { executor(it).execute(sql, namedParameters, handler) }

    override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> =
        ds.connection.use { executor(it).execute(sql, paramSource, handler) }

    override fun execute(sql: String, namedParameters: Map<String, Any?>): Long =
        ds.connection.use { executor(it).execute(sql, namedParameters) }

    override fun execute(sql: String, paramSource: SqlParameterSource): Long =
        ds.connection.use { executor(it).execute(sql, paramSource) }

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>) =
        ds.connection.use { executor(it).executeUpdate(sql, namedParameters) }

    override fun <R> usePinned(transactional: Boolean, block: (SqlExecutor) -> R): R =
        ds.connection.use { conn ->
            val exec = executor(conn)
            if (!transactional) return@use block(exec)
            val previousAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                val result = block(exec)
                translateSql { conn.commit() }
                result
            } catch (e: Throwable) {
                runCatching { conn.rollback() }
                throw e
            } finally {
                runCatching { conn.autoCommit = previousAutoCommit }
            }
        }

    private inline fun <T> translateSql(block: () -> T): T =
        try {
            block()
        } catch (e: SQLException) {
            throw translate(e)
        }
}

/** An [SqlExecutor] bound to one already-open JDBC connection. */
class JdbcExecutor(
    private val conn: Connection,
    override val dialect: Dialect,
    override val typeMapper: TypeMapper,
    private val wrap: ResultSetWrapper,
    private val translate: SqlExceptionTranslator = StandardSqlExceptionTranslator,
) : SqlExecutor {

    private inline fun <T> translateSql(block: () -> T): T =
        try {
            block()
        } catch (e: SQLException) {
            throw translate(e)
        }

    override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> =
        translateSql {
            val statement = NamedParamStatement(conn, sql)
            for ((key, value) in namedParameters) statement.setAny(key, value)
            wrap(statement.executeQuery()).handleResults(handler)
        }

    override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> =
        translateSql {
            val statement = NamedParamStatement(conn, sql)
            statement.bind(paramSource)
            wrap(statement.executeQuery()).handleResults(handler)
        }

    override fun execute(sql: String, namedParameters: Map<String, Any?>): Long = translateSql {
        val statement = NamedParamStatement(conn, sql)
        for ((key, value) in namedParameters) statement.setAny(key, value)
        statement.preparedStatement.runReturningCount()
    }

    override fun execute(sql: String, paramSource: SqlParameterSource): Long = translateSql {
        val statement = NamedParamStatement(conn, sql)
        statement.bind(paramSource)
        statement.preparedStatement.runReturningCount()
    }

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>) {
        translateSql {
            val statement = NamedParamStatement(conn, sql)
            for ((key, value) in namedParameters) statement.setAny(key, value)
            statement.executeUpdate()
        }
    }
}

/**
 * Runs any statement (DDL, DML or query) and returns the row count for queries or the
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
