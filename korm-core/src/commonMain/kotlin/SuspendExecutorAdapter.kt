package io.github.knyazevs.korm

import io.github.knyazevs.korm.resultset.ResultSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Presents a blocking [SqlExecutor] as a [SuspendSqlExecutor] by offloading each call
 * to [dispatcher] (the offload backend's strategy B). The driver stays blocking; this
 * just keeps the calling coroutine from blocking its thread. A truly async backend
 * (r2dbc) implements [SuspendSqlExecutor] natively instead of going through this.
 */
internal class SuspendExecutorAdapter(
    private val sync: SqlExecutor,
    private val dispatcher: CoroutineDispatcher,
) : SuspendSqlExecutor {
    override val dialect get() = sync.dialect
    override val typeMapper get() = sync.typeMapper

    override suspend fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> =
        withContext(dispatcher) { sync.execute(sql, namedParameters, handler) }

    override suspend fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> =
        withContext(dispatcher) { sync.execute(sql, paramSource, handler) }

    override suspend fun execute(sql: String, namedParameters: Map<String, Any?>): Long =
        withContext(dispatcher) { sync.execute(sql, namedParameters) }

    override suspend fun execute(sql: String, paramSource: SqlParameterSource): Long =
        withContext(dispatcher) { sync.execute(sql, paramSource) }

    override suspend fun executeUpdate(sql: String, namedParameters: Map<String, Any?>) =
        withContext(dispatcher) { sync.executeUpdate(sql, namedParameters) }
}
