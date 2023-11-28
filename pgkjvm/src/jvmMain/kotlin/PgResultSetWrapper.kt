package s.knyazev

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toInstant
import s.knyazev.resultset.ResultSet
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

    override fun getBoolean(columnIndex: Int): Boolean? = pgResultSet.getBoolean(columnIndex + 1)

    override fun getShort(columnIndex: Int): Short? = pgResultSet.getShort(columnIndex + 1)

    override fun getInt(columnIndex: Int): Int?= pgResultSet.getInt(columnIndex + 1)

    override fun getLong(columnIndex: Int): Long? = pgResultSet.getLong(columnIndex + 1)

    override fun getFloat(columnIndex: Int): Float? = pgResultSet.getFloat(columnIndex + 1)

    override fun getDouble(columnIndex: Int): Double? = pgResultSet.getDouble(columnIndex + 1)

    override fun getBytes(columnIndex: Int): ByteArray? = pgResultSet.getBytes(columnIndex + 1)

    override fun getDate(columnIndex: Int): LocalDate? {
        val localDate = pgResultSet.getDate(columnIndex + 1) ?: return null
        return LocalDate.parse(formatDate(localDate))
    }

    override fun getTime(columnIndex: Int): kotlinx.datetime.LocalTime? {
        TODO("Not implemented yet")
        //return pgResultSet.getTime(columnIndex + 1)
    }

    override fun getLocalDateTime(columnIndex: Int): kotlinx.datetime.LocalDateTime? {
        TODO("Not implemented yet")
        //return pgResultSet.getLocalDateTime(columnIndex + 1)
    }

    override fun getInstant(columnIndex: Int): kotlinx.datetime.Instant? {
        return pgResultSet.getString(columnIndex + 1)?.fixIso8601()?.toInstant()
    }

    private fun formatDate(date: Date): String {
        val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
        return dateFormat.format(date)
    }

    private fun String.fixIso8601() = replaceRange(10, 11, "T")
}
