package io.github.knyazevs.korm

import java.sql.*


class NamedParamStatement(conn: Connection, sql: String)  {
    val preparedStatement: PreparedStatement
    private val fields: MutableList<String> = ArrayList()

    init {
        preparedStatement = conn.prepareStatement(sql)
    }

    @Throws(SQLException::class)
    fun close() {
        preparedStatement.close()
    }

    @Throws(SQLException::class)
    fun executeQuery(): ResultSet {
        return preparedStatement.executeQuery()
    }

    @Throws(SQLException::class)
    fun executeUpdate(): Int {
        return preparedStatement.executeUpdate()
    }

    @Throws(SQLException::class)
    fun setInt(name: String, value: Int) {
        preparedStatement.setInt(getIndex(name), value)
    }

    @Throws(SQLException::class)
    fun setString(name: String, value: String) {
        preparedStatement.setString(getIndex(name), value)
    }

    fun setAny(name: String, value: Any?) {
        val index = getIndex(name)
        when (value) {
            // todo implement it
            //null -> preparedStatement.setNull(index, 0)
            is Boolean -> preparedStatement.setBoolean(index, value)
            is Byte -> preparedStatement.setByte(index, value)
            is Short -> preparedStatement.setShort(index, value)
            is Int -> preparedStatement.setInt(index, value)
            is Long -> preparedStatement.setLong(index, value)
            is Float -> preparedStatement.setFloat(index, value)
            is Double -> preparedStatement.setDouble(index, value)
            is Date -> preparedStatement.setDate(index, value)
            is Time -> preparedStatement.setTime(index, value)
            is Timestamp -> preparedStatement.setTimestamp(index, value)
            is String -> preparedStatement.setString(index, value)

        }
    }

    private fun getIndex(name: String): Int {
        return fields.indexOf(name) + 1
    }
}
