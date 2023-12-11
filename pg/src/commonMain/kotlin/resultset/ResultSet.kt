package io.github.knyazevs.korm.resultset

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

@Suppress("TooManyFunctions")
interface ResultSet {

    val columns: Array<String>
    /**
     * Moves the cursor forward one row from its current position.
     * A {@code ResultSet} cursor is initially positioned
     * before the first row; the first call to the method
     * {@code next} makes the first row the current row; the
     * second call makes the second row the current row, and so on.
     * <p>
     *
     * @return {@code true} if the new current row is valid;
     * {@code false} if there are no more rows
     */
    fun next(): Boolean

    /**
     * Retrieves the value of the designated column in the current row
     * of this `ResultSet` object as
     * a `String` in the Kotlin programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL `NULL`, the
     * value returned is `null`
     * @throws SQLException if the columnIndex is not valid;
     * if a database access error occurs or this method is
     * called on a closed result set
     */

    fun getString(columnIndex: Int): String?

    /**
     * Retrieves the value of the designated column in the current row
     * of this `ResultSet` object as
     * a `Boolean` in the Kotlin programming language.
     *
     * // TODO: currently only checking "t"
     *
     * <P>If the designated column has a datatype of CHAR or VARCHAR
     * and contains a "0" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER or BIGINT
     * and contains  a 0, a value of `false` is returned.  If the designated column has a datatype
     * of CHAR or VARCHAR
     * and contains a "1" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER or BIGINT
     * and contains  a 1, a value of `true` is returned.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL `NULL`, the
     * value returned is `false`
     * @throws SQLException if the columnIndex is not valid;
     * if a database access error occurs or this method is
     * called on a closed result set
    </P> */
    @Suppress("ForbiddenComment")
    fun getBoolean(columnIndex: Int): Boolean?

    /**
     * Retrieves the value of the designated column in the current row
     * of this `ResultSet` object as
     * a `Short` in the Kotlin programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL `NULL`, the
     * value returned is `0`
     * @throws SQLException if the columnIndex is not valid;
     * if a database access error occurs or this method is
     * called on a closed result set
     */

    fun getShort(columnIndex: Int): Short?

    /**
     * Retrieves the value of the designated column in the current row
     * of this `ResultSet` object as
     * an `Int` in the Kotlin programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL `NULL`, the
     * value returned is `0`
     * @throws SQLException if the columnIndex is not valid;
     * if a database access error occurs or this method is
     * called on a closed result set
     */

    fun getInt(columnIndex: Int): Int?

    /**
     * Retrieves the value of the designated column in the current row
     * of this `ResultSet` object as
     * a `Long` in the Kotlin programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL `NULL`, the
     * value returned is `0`
     * @throws SQLException if the columnIndex is not valid;
     * if a database access error occurs or this method is
     * called on a closed result set
     */

    fun getLong(columnIndex: Int): Long?

    /**
     * Retrieves the value of the designated column in the current row
     * of this `ResultSet` object as
     * a `Float` in the Kotlin programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL `NULL`, the
     * value returned is `0`
     * @throws SQLException if the columnIndex is not valid;
     * if a database access error occurs or this method is
     * called on a closed result set
     */

    fun getFloat(columnIndex: Int): Float?

    /**
     * Retrieves the value of the designated column in the current row
     * of this `ResultSet` object as
     * a `Double` in the Kotlin programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL `NULL`, the
     * value returned is `0`
     * @throws SQLException if the columnIndex is not valid;
     * if a database access error occurs or this method is
     * called on a closed result set
     */

    fun getDouble(columnIndex: Int): Double?


    /**
     * Retrieves the value of the designated column in the current row
     * of this `ResultSet` object as
     * a `Byte` array in the Kotlin programming language.
     * The bytes represent the raw values returned by the driver.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL `NULL`, the
     * value returned is `null`
     * @throws SQLException if the columnIndex is not valid;
     * if a database access error occurs or this method is
     * called on a closed result set
     */

    fun getBytes(columnIndex: Int): ByteArray?

    /**
     * Retrieves the value of the designated column in the current row
     * of this `ResultSet` object as
     * a `LocalDate` object in the Kotlin programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL `NULL`, the
     * value returned is `null`
     * @throws SQLException if the columnIndex is not valid;
     * if a database access error occurs or this method is
     * called on a closed result set
     */

    fun getDate(columnIndex: Int): LocalDate?

    /**
     * Retrieves the value of the designated column in the current row
     * of this `ResultSet` object as
     * a `LocalTime` object in the Kotlin programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL `NULL`, the
     * value returned is `null`
     * @throws SQLException if the columnIndex is not valid;
     * if a database access error occurs or this method is
     * called on a closed result set
     */

    fun getTime(columnIndex: Int): LocalTime?

    /**
     * Retrieves the value of the designated column in the current row
     * of this `ResultSet` object as
     * a `Instant` object in the Kotlin programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL `NULL`, the
     * value returned is `null`
     * @throws SQLException if the columnIndex is not valid;
     * if a database access error occurs or this method is
     * called on a closed result set
     */

    fun getLocalDateTime(columnIndex: Int): LocalDateTime?

    /**
     * Retrieves the value of the designated column in the current row
     * of this `ResultSet` object as
     * a `Instant` object in the Kotlin programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL `NULL`, the
     * value returned is `null`
     * @throws SQLException if the columnIndex is not valid;
     * if a database access error occurs or this method is
     * called on a closed result set
     */

    fun getInstant(columnIndex: Int): Instant?

}
