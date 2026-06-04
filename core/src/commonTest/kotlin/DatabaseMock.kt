import io.github.knyazevs.korm.SqlParameterSource
import io.github.knyazevs.korm.StandardDialect
import io.github.knyazevs.korm.StandardTypeMapper
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.resultset.ResultSet

class DatabaseMock: Database {

    override val dialect = StandardDialect
    override val typeMapper = StandardTypeMapper

    var result: Any? = null
    var internalSql: String = ""
    var internalParams: Map<String, Any?> = emptyMap()

    override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> {
        internalSql = sql
        internalParams = namedParameters
        @Suppress("UNCHECKED_CAST")
        return (result as List<T>?) ?: listOf()
    }

    override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> {
        internalSql = sql
        @Suppress("UNCHECKED_CAST")
        return (result as List<T>?) ?: listOf()
    }

    override fun execute(sql: String, namedParameters: Map<String, Any?>): Long {
        internalSql = sql
        internalParams = namedParameters
        return (result as Long?) ?: 0L
    }

    override fun execute(sql: String, paramSource: SqlParameterSource): Long {
        internalSql = sql
        return (result as Long?) ?: 0L
    }

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>) {
        internalSql = sql
        internalParams = namedParameters
    }
}
