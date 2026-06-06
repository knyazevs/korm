package io.github.knyazevs.korm

import io.github.knyazevs.korm.resultset.ResultSet
import io.github.knyazevs.korm.sql.getBigDecimal
import io.github.knyazevs.korm.sql.getJson
import io.github.knyazevs.korm.sql.getUUID

/** Backend-specific conversion of column values to/from the driver's wire form. */
interface TypeMapper {
    /** Converts [value] to the form bound as a parameter (e.g. UUID/BigDecimal → text). */
    fun toParameter(value: Any?): Any?

    /** Reads the column value of [type] at [index] from [rs]. */
    fun fromResult(rs: ResultSet, index: Int, type: Column.ColumnNameEnum): Any?
}

/**
 * Text-based mapping shared by drivers that bind and return values as text (libpq,
 * and JDBC with `stringtype=unspecified`). Non-primitive values are sent through
 * toString(); each column type is read via the matching [ResultSet] getter.
 */
object StandardTypeMapper : TypeMapper {
    override fun toParameter(value: Any?): Any? = when (value) {
        null, is Boolean, is Int, is Long, is Double, is String -> value
        else -> value.toString()
    }

    override fun fromResult(rs: ResultSet, index: Int, type: Column.ColumnNameEnum): Any? = when (type) {
        Column.ColumnNameEnum.UUID -> rs.getUUID(index)
        Column.ColumnNameEnum.BigDecimal -> rs.getBigDecimal(index)
        Column.ColumnNameEnum.Double -> rs.getDouble(index)
        Column.ColumnNameEnum.Int -> rs.getInt(index)
        Column.ColumnNameEnum.Boolean -> rs.getBoolean(index)
        Column.ColumnNameEnum.String -> rs.getString(index)
        Column.ColumnNameEnum.Instant -> rs.getInstant(index)
        Column.ColumnNameEnum.Json -> rs.getJson(index)
        Column.ColumnNameEnum.Long -> rs.getLong(index)
        Column.ColumnNameEnum.Float -> rs.getFloat(index)
        Column.ColumnNameEnum.Short -> rs.getShort(index)
        Column.ColumnNameEnum.LocalDate -> rs.getDate(index)
        Column.ColumnNameEnum.LocalTime -> rs.getTime(index)
        Column.ColumnNameEnum.LocalDateTime -> rs.getLocalDateTime(index)
    }
}
