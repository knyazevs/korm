package io.github.knyazevs.korm

import io.github.knyazevs.korm.resultset.ResultSet

// Aggregates are Selectable (appear in SELECT and are read from a ResultRow) and, since
// Selectable is an Expression, can also appear in `having(...)` (e.g. `total gt Value(100)`).

/** `COUNT(*)` — the number of rows in the group. Hold the result in a `val` to read it. */
fun count(): Selectable<Long> = object : Selectable<Long> {
    override fun toSql(builder: ParamBuilder) = "COUNT(*)"
    override fun read(rs: ResultSet, index: kotlin.Int, typeMapper: TypeMapper): Long? = rs.getLong(index)
}

/** `COUNT(column)` — the non-null values of the column in the group. */
fun Column<*, *, *>.count(): Selectable<Long> {
    val column = this
    return object : Selectable<Long> {
        override fun toSql(builder: ParamBuilder) = "COUNT(${column.toSql(builder)})"
        override fun read(rs: ResultSet, index: kotlin.Int, typeMapper: TypeMapper): Long? = rs.getLong(index)
    }
}

// MIN/MAX/SUM keep the column's type (read through its type mapping). SUM of an integer
// column is a bigint server-side but read back through the Int mapping, so very large sums
// can overflow — sum BigDecimal/Double columns when that matters.
fun <Z> Column<Z, *, *>.min(): Selectable<Z> = ColumnAggregate("MIN", this)
fun <Z> Column<Z, *, *>.max(): Selectable<Z> = ColumnAggregate("MAX", this)
fun <Z> Column<Z, *, *>.sum(): Selectable<Z> = ColumnAggregate("SUM", this)

private class ColumnAggregate<Z>(private val fn: String, private val column: Column<Z, *, *>) : Selectable<Z> {
    override fun toSql(builder: ParamBuilder) = "$fn(${column.toSql(builder)})"
    override fun read(rs: ResultSet, index: kotlin.Int, typeMapper: TypeMapper): Z? = column.read(rs, index, typeMapper)
}

/** `AVG(column)` as a Double. */
fun Column<*, *, *>.avg(): Selectable<Double> {
    val column = this
    return object : Selectable<Double> {
        override fun toSql(builder: ParamBuilder) = "AVG(${column.toSql(builder)})"
        override fun read(rs: ResultSet, index: kotlin.Int, typeMapper: TypeMapper): Double? = rs.getDouble(index)
    }
}
