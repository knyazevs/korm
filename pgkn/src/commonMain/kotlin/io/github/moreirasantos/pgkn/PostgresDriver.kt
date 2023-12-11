package io.github.moreirasantos.pgkn

import io.github.moreirasantos.pgkn.paramsource.MapSqlParameterSource
import io.github.moreirasantos.pgkn.sql.buildValueArray
import io.github.moreirasantos.pgkn.sql.parseSql
import io.github.moreirasantos.pgkn.sql.substituteNamedParameters
import io.github.moreirasantos.pgkn.resultset.PostgresResultSet
import kotlinx.cinterop.*
import libpq.*
import com.github.knyazevs.korm.PostgresDriver
import com.github.knyazevs.korm.SqlParameterSource
import com.github.knyazevs.korm.resultset.ResultSet

@OptIn(ExperimentalForeignApi::class)
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

@ExperimentalForeignApi
private class PostgresDriverImpl(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
) : PostgresDriver {

    private val connection = PQsetdbLogin(
        pghost = host,
        pgport = port.toString(),
        dbName = database,
        login = user,
        pwd = password,
        pgoptions = null,
        pgtty = null
    ).apply { require(ConnStatusType.CONNECTION_OK == PQstatus(this)) }!!

    override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T) =
        if (namedParameters.isEmpty()) doExecute(sql).handleResults(handler)
        else execute(sql, MapSqlParameterSource(namedParameters), handler)

    override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T) =
        doExecute(sql, paramSource).handleResults(handler)

    override fun execute(sql: String, namedParameters: Map<String, Any?>) =
        if (namedParameters.isEmpty()) doExecute(sql).returnCount()
        else execute(sql, MapSqlParameterSource(namedParameters))

    override fun execute(sql: String, paramSource: SqlParameterSource) =
        doExecute(sql, paramSource).returnCount()

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>) {
        if (namedParameters.isEmpty()) doExecute(sql)
        else execute(sql, MapSqlParameterSource(namedParameters))
    }

    private fun <T> CPointer<PGresult>.handleResults(handler: (ResultSet) -> T): List<T> {
        val rs = PostgresResultSet(this)

        val list: MutableList<T> = mutableListOf()
        while (rs.next()) {
            list.add(handler(rs))
        }

        PQclear(this)
        return list
    }

    private fun CPointer<PGresult>.returnCount(): Long {
        val rows = PQcmdTuples(this)!!.toKString()
        PQclear(this)
        return rows.toLongOrNull() ?: 0
    }

    private fun doExecute(sql: String, paramSource: SqlParameterSource): CPointer<PGresult> {
        val parsedSql = parseSql(sql)
        val sqlToUse: String = substituteNamedParameters(parsedSql, paramSource)
        val params: Array<Any?> = buildValueArray(parsedSql, paramSource)

        return memScoped {
            PQexecParams(
                connection,
                command = sqlToUse,
                nParams = params.size,
                paramValues = createValues(params.size) {
                    println(params[it]?.toString()?.cstr)
                    value = params[it]?.toString()?.cstr?.getPointer(this@memScoped)
                },
                paramLengths = params.map { it?.toString()?.length ?: 0 }.toIntArray().refTo(0),
                paramFormats = IntArray(params.size) { TEXT_RESULT_FORMAT }.refTo(0),
                paramTypes = parsedSql.parameterNames.map(paramSource::getSqlType).toUIntArray().refTo(0),
                resultFormat = TEXT_RESULT_FORMAT
            )
        }.check()
    }

    private fun doExecute(sql: String) = memScoped {
        PQexecParams(
            connection,
            command = sql,
            nParams = 0,
            paramValues = createValues(0) {},
            paramLengths = createValues(0) {},
            paramFormats = createValues(0) {},
            paramTypes = createValues(0) {},
            resultFormat = TEXT_RESULT_FORMAT
        )
    }.check()

    private fun CPointer<PGresult>?.check(): CPointer<PGresult> {
        val status = PQresultStatus(this)
        check(status == PGRES_TUPLES_OK || status == PGRES_COMMAND_OK || status == PGRES_COPY_IN) {
            connection.error()
        }
        return this!!
    }
}

@ExperimentalForeignApi
private fun CPointer<PGconn>?.error(): String = PQerrorMessage(this)!!.toKString().also { PQfinish(this) }

private const val TEXT_RESULT_FORMAT = 0

@Suppress("UnusedPrivateProperty")
private const val BINARY_RESULT_FORMAT = 1
