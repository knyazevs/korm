import s.knyazev.SqlParameterSource
import s.knyazev.database.Database
import s.knyazev.resultset.ResultSet

class DatabaseMock: Database {

    var result: Any? = null
    var internalSql: String = ""
    override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> {
        internalSql = sql
        return (result as List<T>?) ?: listOf()
    }

    override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> {
        internalSql = sql
        return (result as List<T>?) ?: listOf()
    }

    override fun execute(sql: String, namedParameters: Map<String, Any?>): Long {
        internalSql = sql
        return (result as Long?) ?: 0L
    }

    override fun execute(sql: String, paramSource: SqlParameterSource): Long {
        internalSql = sql
        return (result as Long?) ?: 0L
    }

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>) {
        internalSql = sql
    }
}