package io.github.knyazevs.korm.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.knyazevs.korm.ConnectionPool
import io.github.knyazevs.korm.Dialect
import io.github.knyazevs.korm.PinnedConnection
import io.github.knyazevs.korm.SqlExecutor
import io.github.knyazevs.korm.SqlParameterSource
import io.github.knyazevs.korm.SuspendSqlExecutor
import io.github.knyazevs.korm.TypeMapper
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.database.SuspendDatabase
import io.github.knyazevs.korm.resultset.ResultSet
import io.github.knyazevs.korm.runConnection
import io.github.knyazevs.korm.runPinned
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
) : Database<Nothing>, SuspendDatabase<Nothing> {

    private val ds: HikariDataSource = HikariDataSource(HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        if (username != null) this.username = username
        if (password != null) this.password = password
        this.maximumPoolSize = poolSize
        if (connectionInitSql != null) this.connectionInitSql = connectionInitSql
    })

    // One pool, two entry points: usePinned (blocking) and useConnection (suspend) both
    // run on it. acquireSuspending uses the default (offload the blocking checkout).
    private val pool = object : ConnectionPool {
        override fun acquire(): PinnedConnection =
            JdbcPinnedConnection(ds.connection, dialect, typeMapper, wrap, translate)
    }

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

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>): Long =
        ds.connection.use { executor(it).executeUpdate(sql, namedParameters) }

    override fun <R> usePinned(transactional: Boolean, block: (SqlExecutor) -> R): R =
        pool.runPinned(transactional, block)

    override suspend fun <R> useConnection(transactional: Boolean, block: suspend (SuspendSqlExecutor) -> R): R =
        pool.runConnection(transactional, block)
}

/** A [PinnedConnection] over one borrowed JDBC connection. */
private class JdbcPinnedConnection(
    private val conn: Connection,
    dialect: Dialect,
    typeMapper: TypeMapper,
    wrap: ResultSetWrapper,
    private val translate: SqlExceptionTranslator,
) : PinnedConnection {
    override val executor: SqlExecutor = JdbcExecutor(conn, dialect, typeMapper, wrap, translate)
    private var previousAutoCommit = true

    override fun begin() {
        previousAutoCommit = conn.autoCommit
        conn.autoCommit = false
    }

    override fun commit() {
        try {
            conn.commit()
        } catch (e: SQLException) {
            throw translate(e)
        }
    }

    override fun rollback() {
        conn.rollback()
    }

    override fun release() {
        runCatching { conn.autoCommit = previousAutoCommit }
        conn.close()
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

    // Statements are closed after each call so pgjdbc can return the server-prepared
    // statement to its cache (enabling reuse on the next call) instead of leaking it —
    // a leaked statement forces a deferred CloseStatement + an extra protocol round-trip,
    // and defeats reuse (re-Parse) when the same query runs again on the connection.
    override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> =
        translateSql {
            NamedParamStatement(conn, sql).use { statement ->
                for ((key, value) in namedParameters) statement.setAny(key, value)
                wrap(statement.executeQuery()).handleResults(handler)
            }
        }

    override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> =
        translateSql {
            NamedParamStatement(conn, sql).use { statement ->
                statement.bind(paramSource)
                wrap(statement.executeQuery()).handleResults(handler)
            }
        }

    override fun execute(sql: String, namedParameters: Map<String, Any?>): Long = translateSql {
        NamedParamStatement(conn, sql).use { statement ->
            for ((key, value) in namedParameters) statement.setAny(key, value)
            statement.preparedStatement.runReturningCount()
        }
    }

    override fun execute(sql: String, paramSource: SqlParameterSource): Long = translateSql {
        NamedParamStatement(conn, sql).use { statement ->
            statement.bind(paramSource)
            statement.preparedStatement.runReturningCount()
        }
    }

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>): Long =
        translateSql {
            NamedParamStatement(conn, sql).use { statement ->
                for ((key, value) in namedParameters) statement.setAny(key, value)
                statement.executeUpdate().toLong()
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
