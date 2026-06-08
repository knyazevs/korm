package io.github.knyazevs.korm.r2dbc

import io.github.knyazevs.korm.Dialect
import io.github.knyazevs.korm.SqlParameterSource
import io.github.knyazevs.korm.SuspendSqlExecutor
import io.github.knyazevs.korm.TypeMapper
import io.github.knyazevs.korm.resultset.ResultSet
import io.github.knyazevs.korm.sqlException
import io.r2dbc.spi.Connection
import io.r2dbc.spi.R2dbcException
import io.r2dbc.spi.Statement
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.asFlow

/**
 * A [SuspendSqlExecutor] bound to one r2dbc [Connection]. Unlike the offload backends
 * there is NO dispatcher hop: each call drives the reactive driver and bridges it to
 * suspend via kotlinx-coroutines-reactive. The SQL rendering ([Dialect]) and value
 * conversion ([TypeMapper]) are reused verbatim from korm-postgres; only the bind step
 * differs — `:name` is rewritten to `$N` and bound by index.
 */
internal class R2dbcExecutor(
    private val connection: Connection,
    override val dialect: Dialect,
    override val typeMapper: TypeMapper,
) : SuspendSqlExecutor {

    private fun prepare(sql: String, namedParameters: Map<String, Any?>): Statement {
        val parsed = parseNamedParams(sql)
        val statement = connection.createStatement(parsed.sql)
        parsed.names.forEachIndexed { index, name ->
            // A missing key is a typo, not an explicit null: reject it so raw SQL fails fast
            // instead of silently binding NULL. An explicit `null` value is still bound below.
            require(namedParameters.containsKey(name)) { "No value supplied for parameter \"$name\"" }
            when (val value = namedParameters[name]) {
                // korm's TypeMapper has already reduced values to String/primitive; a null
                // binds as text and any ::cast in the SQL turns it into a typed NULL.
                null -> statement.bindNull(index, String::class.java)
                else -> statement.bind(index, value)
            }
        }
        return statement
    }

    override suspend fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> =
        translating {
            val out = ArrayList<T>()
            prepare(sql, namedParameters).execute().asFlow().collect { result ->
                // Box the handler's result: it may be nullable, but asFlow requires a non-null
                // element type, and the box itself is always non-null.
                result.map { row, meta -> Box(handler(R2dbcResultSet(row, meta))) }.asFlow().collect { out.add(it.value) }
            }
            out
        }

    override suspend fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> =
        execute(sql, paramSource.toMap(), handler)

    override suspend fun execute(sql: String, namedParameters: Map<String, Any?>): Long =
        translating {
            var total = 0L
            prepare(sql, namedParameters).execute().asFlow().collect { result ->
                result.rowsUpdated.asFlow().collect { total += it }
            }
            total
        }

    override suspend fun execute(sql: String, paramSource: SqlParameterSource): Long =
        execute(sql, paramSource.toMap())

    override suspend fun executeUpdate(sql: String, namedParameters: Map<String, Any?>): Long =
        execute(sql, namedParameters)

    // Map r2dbc's exceptions to korm's typed ones via the same SQLSTATE helper the JDBC
    // backend uses (UniqueViolation 23505, ForeignKey 23503, ...).
    private suspend fun <T> translating(block: suspend () -> T): T =
        try {
            block()
        } catch (e: R2dbcException) {
            throw sqlException(e.message ?: "SQL error", e.sqlState, e)
        }
}

private fun SqlParameterSource.toMap(): Map<String, Any?> =
    (parameterNames ?: emptyArray()).associateWith { getValue(it) }

/** Non-null wrapper so a possibly-nullable handler result can flow through `Publisher.asFlow()`. */
private class Box<T>(val value: T)
