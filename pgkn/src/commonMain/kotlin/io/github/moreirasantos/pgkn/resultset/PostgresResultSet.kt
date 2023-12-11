package io.github.moreirasantos.pgkn.resultset

import io.github.moreirasantos.pgkn.KLogger
import io.github.moreirasantos.pgkn.exception.GetColumnValueException
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.datetime.*
import libpq.*
import com.github.knyazevs.korm.resultset.ResultSet

private val logger = KLogger("io.github.moreirasantos.pgkn.resultset.PostgresResultSetKt")

/**
 * To Fix ISO 8601, as postgres default is space not "T"
 * https://www.postgresql.org/docs/current/datatype-datetime.html#DATATYPE-DATETIME-OUTPUT
 */
@Suppress("MagicNumber")
private fun String.fixIso8601() = replaceRange(10, 11, "T")

@Suppress("TooManyFunctions")
@ExperimentalForeignApi
internal class PostgresResultSet(val internal: CPointer<PGresult>) : ResultSet {

    override val columns by lazy { internalGetColumns() }

    private fun internalGetColumns(): Array<String> {
        println("get columns")
        println("Columns count: ${PQnfields(internal)}")
        return (0..PQnfields(internal)).mapNotNull {
            PQfname(internal, it)?.toKString()
        }.toTypedArray()
    }

    private val rowCount: Int = PQntuples(internal)

    @Suppress("UnusedPrivateProperty")
    private val columnCount: Int = PQnfields(internal)

    private var currentRow = -1


    override fun next(): Boolean {
        if (currentRow > rowCount - 2) {
            return false
        }
        currentRow++
        return true
    }

    private fun isNull(columnIndex: Int): Boolean =
        PQgetisnull(res = internal, tup_num = currentRow, field_num = columnIndex) == 1

    private fun getPointer(columnIndex: Int): CPointer<ByteVar>? {
        if (isNull(columnIndex)) return null
        return PQgetvalue(res = internal, tup_num = currentRow, field_num = columnIndex)
            ?: throw GetColumnValueException(columnIndex)
    }

    /**
     * Are all non-binary columns returned as text?
     * https://www.postgresql.org/docs/9.5/libpq-exec.html#LIBPQ-EXEC-SELECT-INFO
     */
    override fun getString(columnIndex: Int): String? = getPointer(columnIndex)?.toKString()
        .also {
            logger.trace { "Value of column $columnIndex: $it" }
        }

    override fun getBoolean(columnIndex: Int): Boolean? = getString(columnIndex)?.equals("t")

    override fun getShort(columnIndex: Int): Short? = getString(columnIndex)?.toShort()


    override fun getInt(columnIndex: Int): Int? = getString(columnIndex)?.toInt()


    override fun getLong(columnIndex: Int): Long? = getString(columnIndex)?.toLong()


    override fun getFloat(columnIndex: Int): Float? = getString(columnIndex)?.toFloat()

    override fun getDouble(columnIndex: Int): Double? = getString(columnIndex)?.toDouble()

    override fun getBytes(columnIndex: Int): ByteArray? = getString(columnIndex)?.encodeToByteArray()

    override fun getDate(columnIndex: Int): LocalDate? = getString(columnIndex)?.toLocalDate()

    override fun getTime(columnIndex: Int): LocalTime? = getString(columnIndex)?.toLocalTime()
    override fun getLocalDateTime(columnIndex: Int): LocalDateTime? = getString(columnIndex)
        ?.fixIso8601()
        ?.toLocalDateTime()

    override fun getInstant(columnIndex: Int): Instant? = getString(columnIndex)
        ?.fixIso8601()
        ?.toInstant()
}
