import io.github.knyazevs.korm.SqlExecutor
import io.github.knyazevs.korm.SqlParameterSource
import io.github.knyazevs.korm.StandardDialect
import io.github.knyazevs.korm.StandardTypeMapper
import io.github.knyazevs.korm.SuspendSqlExecutor
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.database.SuspendDatabase
import io.github.knyazevs.korm.resultset.ResultSet

class DatabaseMock: Database<Nothing>, SuspendDatabase<Nothing> {

    override val dialect = StandardDialect
    override val typeMapper = StandardTypeMapper

    // The mock records SQL rather than pinning a real connection, so it just runs
    // the block against itself (BEGIN/COMMIT are no-ops here).
    override fun <R> usePinned(transactional: Boolean, block: (SqlExecutor) -> R): R = block(this)

    // Suspend path: hand the block a tiny SuspendSqlExecutor that delegates to this mock's
    // recording. A separate object (not `this`) because a class can't implement both
    // SqlExecutor and SuspendSqlExecutor — their execute() overloads clash.
    override suspend fun <R> useConnection(transactional: Boolean, block: suspend (SuspendSqlExecutor) -> R): R =
        block(SuspendMockExecutor(this))

    override fun close() = Unit

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

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>): Long {
        internalSql = sql
        internalParams = namedParameters
        return (result as Long?) ?: 0L
    }
}

// Mirrors DatabaseMock onto the suspend executor surface by delegating to its blocking methods.
private class SuspendMockExecutor(private val mock: DatabaseMock) : SuspendSqlExecutor {
    override val dialect get() = mock.dialect
    override val typeMapper get() = mock.typeMapper

    override suspend fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> =
        mock.execute(sql, namedParameters, handler)

    override suspend fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> =
        mock.execute(sql, paramSource, handler)

    override suspend fun execute(sql: String, namedParameters: Map<String, Any?>): Long =
        mock.execute(sql, namedParameters)

    override suspend fun execute(sql: String, paramSource: SqlParameterSource): Long =
        mock.execute(sql, paramSource)

    override suspend fun executeUpdate(sql: String, namedParameters: Map<String, Any?>) =
        mock.executeUpdate(sql, namedParameters)
}
