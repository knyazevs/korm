package com.github.knyazevs.korm

import com.github.knyazevs.korm.resultset.ResultSet
import java.sql.Connection
import java.sql.DriverManager


fun FPostgresDriver(
    host: String,
    port: Int = 5432,
    database: String,
    user: String,
    password: String,
): PostgresDriver = PostgresDriverImpl(
    host = host,
    port = port,
    database = database,
    user = user,
    password = password
)

private class PostgresDriverImpl(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
) : PostgresDriver {

    private val conn: Connection =
        DriverManager.getConnection("jdbc:postgresql://$host:$port/$database", user, password)

    override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> {
        val preparedStatement = NamedParamStatement(conn, sql)
        for ((key, value) in namedParameters) {
            preparedStatement.setAny(key, value)
        }
        val resultSet = PgResultSetWrapper(preparedStatement.executeQuery())
        return resultSet.handleResults(handler)
    }


    override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> {
        val preparedStatement = conn.prepareStatement(sql)
        val resultSet = preparedStatement.executeQuery() as ResultSet
        return resultSet.handleResults(handler)
    }

    override fun execute(sql: String, namedParameters: Map<String, Any?>): Long {
        val preparedStatement = NamedParamStatement(conn, sql)
        for ((key, value) in namedParameters) {
            preparedStatement.setAny(key, value)
        }
        val resultSet = preparedStatement.executeQuery() as ResultSet
        return resultSet.returnCount()
    }

    override fun execute(sql: String, paramSource: SqlParameterSource): Long {
        val preparedStatement = conn.prepareStatement(sql)
        val resultSet = preparedStatement.executeQuery() as ResultSet
        return resultSet.returnCount()
    }

    override fun executeUpdate(
        sql: String,
        namedParameters: Map<String, Any?>
    ) {
        val preparedStatement = NamedParamStatement(conn, sql)
        for ((key, value) in namedParameters) {
            preparedStatement.setAny(key, value)
        }
        preparedStatement.executeUpdate()
    }


    private fun ResultSet.returnCount(): Long {
        var size = 0
        while (this.next()) {
            size++
        }
        return size.toLong()
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

