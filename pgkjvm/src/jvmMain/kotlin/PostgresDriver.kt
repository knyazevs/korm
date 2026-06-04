package io.github.knyazevs.korm

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.knyazevs.korm.resultset.ResultSet
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
        this.setJdbcUrl("jdbc:postgresql://$host:$port/$database?stringtype=unspecified")
        this.username = user
        this.password = password
        this.maximumPoolSize = poolSize
        this.addDataSourceProperty("cachePrepStmts", "true")
        this.addDataSourceProperty("prepStmtCacheSize", "250")
        this.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
    }
    var ds = HikariDataSource(config)

    override fun close() = ds.close()

    override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> =
        ds.connection.use { conn ->
            val statement = NamedParamStatement(conn, sql)
            for ((key, value) in namedParameters) {
                statement.setAny(key, value)
            }
            PgResultSetWrapper(statement.executeQuery()).handleResults(handler)
        }

    override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> =
        ds.connection.use { conn ->
            val statement = NamedParamStatement(conn, sql)
            statement.bind(paramSource)
            PgResultSetWrapper(statement.executeQuery()).handleResults(handler)
        }

    override fun execute(sql: String, namedParameters: Map<String, Any?>): Long =
        ds.connection.use { conn ->
            val statement = NamedParamStatement(conn, sql)
            for ((key, value) in namedParameters) {
                statement.setAny(key, value)
            }
            statement.preparedStatement.runReturningCount()
        }

    override fun execute(sql: String, paramSource: SqlParameterSource): Long =
        ds.connection.use { conn ->
            val statement = NamedParamStatement(conn, sql)
            statement.bind(paramSource)
            statement.preparedStatement.runReturningCount()
        }

    override fun executeUpdate(
        sql: String,
        namedParameters: Map<String, Any?>
    ) {
        ds.connection.use { conn ->
            val statement = NamedParamStatement(conn, sql)
            for ((key, value) in namedParameters) {
                statement.setAny(key, value)
            }
            statement.executeUpdate()
        }
    }

    /**
     * Runs any statement (DDL, DML or query) and returns a row count for queries
     * or the update count otherwise. Uses [PreparedStatement.execute] so it works
     * for statements that do not produce a result set (e.g. CREATE TABLE).
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
        val rs = this

        val list: MutableList<T> = mutableListOf()
        while (rs.next()) {
            list.add(handler(rs))
        }

        return list
    }
}
