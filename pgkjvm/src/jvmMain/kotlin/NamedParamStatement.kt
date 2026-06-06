package io.github.knyazevs.korm

import java.sql.*


/**
 * A [PreparedStatement] wrapper that accepts Spring-style `:name` named
 * parameters. On construction the SQL is parsed: every `:name` placeholder is
 * replaced by a positional `?` and the parameter order is recorded so values can
 * later be bound by name. Postgres `::` casts and quoted string literals are left
 * untouched.
 */
class NamedParamStatement(conn: Connection, sql: String) {
    val preparedStatement: PreparedStatement
    private val fields: List<String>

    init {
        // Parsing (`:name` -> `?` plus the parameter order) depends only on the SQL text,
        // which is identical across repeated calls of the same statement — cache it.
        val parsed = parseCache.getOrPut(sql) { parse(sql) }
        fields = parsed.fields
        preparedStatement = conn.prepareStatement(parsed.sql)
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

    /** Binds every named placeholder in the statement from [paramSource], by name. */
    fun bind(paramSource: SqlParameterSource) {
        for (name in fields.toSet()) {
            require(paramSource.hasValue(name)) { "No value supplied for parameter \"$name\"" }
            setAny(name, paramSource.getValue(name))
        }
    }

    fun setAny(name: String, value: Any?) {
        for (index in indexesOf(name)) {
            when (value) {
                null -> preparedStatement.setObject(index, null)
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
                else -> preparedStatement.setObject(index, value)
            }
        }
    }

    // 1-based JDBC indexes of every placeholder that used this name.
    private fun indexesOf(name: String): List<Int> =
        fields.mapIndexedNotNull { index, field -> if (field == name) index + 1 else null }

    private class Parsed(val sql: String, val fields: List<String>)

    companion object {
        private val parseCache = java.util.concurrent.ConcurrentHashMap<String, Parsed>()

        private fun parse(sql: String): Parsed {
            val out = StringBuilder(sql.length)
            val fields = ArrayList<String>()
            var i = 0
            while (i < sql.length) {
                val c = sql[i]
                when {
                    c == '\'' || c == '"' -> {
                        // Copy a quoted literal verbatim so ':' inside it is not treated as a parameter.
                        out.append(c)
                        i++
                        while (i < sql.length) {
                            val ch = sql[i]
                            out.append(ch)
                            i++
                            if (ch == c) break
                        }
                    }
                    c == ':' && i + 1 < sql.length && sql[i + 1] == ':' -> {
                        // Postgres "::" cast operator, not a parameter.
                        out.append("::")
                        i += 2
                    }
                    c == ':' && i + 1 < sql.length && (sql[i + 1].isLetter() || sql[i + 1] == '_') -> {
                        var j = i + 1
                        while (j < sql.length && (sql[j].isLetterOrDigit() || sql[j] == '_')) j++
                        fields.add(sql.substring(i + 1, j))
                        out.append('?')
                        i = j
                    }
                    else -> {
                        out.append(c)
                        i++
                    }
                }
            }
            return Parsed(out.toString(), fields)
        }
    }
}
