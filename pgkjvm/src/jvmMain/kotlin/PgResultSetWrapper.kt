package io.github.knyazevs.korm

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import io.github.knyazevs.korm.resultset.ResultSet
import java.sql.ResultSetMetaData
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class PgResultSetWrapper(private val pgResultSet: java.sql.ResultSet) : ResultSet {
    override val columns: Array<String> = internalGetColumns()

    private fun internalGetColumns(): Array<String> {
        val md: ResultSetMetaData = pgResultSet.metaData
        val numCols = md.columnCount
        return (0..<numCols).map { md.getColumnName(it + 1) }.toTypedArray()
    }

    override fun next(): Boolean = pgResultSet.next()

    override fun getString(columnIndex: Int): String? = pgResultSet.getString(columnIndex + 1)

    // JDBC's primitive getters return 0/false for SQL NULL, so wasNull() must be
    // consulted to distinguish a real value from NULL on nullable columns.
    override fun getBoolean(columnIndex: Int): Boolean? = pgResultSet.getBoolean(columnIndex + 1).orNull()

    override fun getShort(columnIndex: Int): Short? = pgResultSet.getShort(columnIndex + 1).orNull()

    override fun getInt(columnIndex: Int): Int? = pgResultSet.getInt(columnIndex + 1).orNull()

    override fun getLong(columnIndex: Int): Long? = pgResultSet.getLong(columnIndex + 1).orNull()

    override fun getFloat(columnIndex: Int): Float? = pgResultSet.getFloat(columnIndex + 1).orNull()

    override fun getDouble(columnIndex: Int): Double? = pgResultSet.getDouble(columnIndex + 1).orNull()

    private fun <T> T.orNull(): T? = if (pgResultSet.wasNull()) null else this

    override fun getBytes(columnIndex: Int): ByteArray? = pgResultSet.getBytes(columnIndex + 1)

    override fun getDate(columnIndex: Int): LocalDate? =
        pgResultSet.getString(columnIndex + 1)?.let { LocalDate.parse(it) }

    override fun getTime(columnIndex: Int): kotlinx.datetime.LocalTime? =
        pgResultSet.getString(columnIndex + 1)?.let { kotlinx.datetime.LocalTime.parse(it) }

    // Postgres returns a "yyyy-MM-dd HH:mm:ss" timestamp; the space at index 10 becomes 'T'.
    override fun getLocalDateTime(columnIndex: Int): kotlinx.datetime.LocalDateTime? =
        pgResultSet.getString(columnIndex + 1)?.fixIso8601()?.let { kotlinx.datetime.LocalDateTime.parse(it) }

    override fun getInstant(columnIndex: Int): kotlinx.datetime.Instant? {
        return pgResultSet.getString(columnIndex + 1)?.fixIso8601()?.let { Instant.parse(it) }
    }

    private fun String.fixIso8601() = replaceRange(10, 11, "T")
}
