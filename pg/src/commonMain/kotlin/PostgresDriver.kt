package s.knyazev

import s.knyazev.resultset.ResultSet

/**
 * Executes given query with given named parameters.
 * If you pass a handler, you will receive a list of result data.
 * You can pass an [SqlParameterSource] to register your own Postgres types.
 */
interface PostgresDriver {
    fun <T> execute(sql: String, namedParameters: Map<String, Any?> = emptyMap(), handler: (ResultSet) -> T): List<T>
    fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T>
    fun execute(sql: String, namedParameters: Map<String, Any?> = emptyMap()): Long
    fun execute(sql: String, paramSource: SqlParameterSource): Long


    fun executeUpdate(sql: String, namedParameters: Map<String, Any?> = emptyMap())
}
