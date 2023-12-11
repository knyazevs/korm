package com.github.knyazevs.korm.database

import com.github.knyazevs.korm.PostgresDriver
import com.github.knyazevs.korm.SqlParameterSource
import com.github.knyazevs.korm.resultset.ResultSet


interface Database {
    fun <T> execute(sql: String, namedParameters: Map<String, Any?> = emptyMap(), handler: (ResultSet) -> T): List<T>
    fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T>
    fun execute(sql: String, namedParameters: Map<String, Any?> = emptyMap()): Long
    fun execute(sql: String, paramSource: SqlParameterSource): Long

    fun executeUpdate(sql: String, namedParameters: Map<String, Any?> = emptyMap())
}

expect fun createDatabase(host: String,
                          port: Int = 5432,
                          database: String,
                          user: String,
                          password: String): PostgresDriver
