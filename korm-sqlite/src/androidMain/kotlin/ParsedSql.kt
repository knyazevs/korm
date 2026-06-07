package io.github.knyazevs.korm

import java.util.concurrent.ConcurrentHashMap

/**
 * SQL with its Spring-style `:name` placeholders rewritten to positional `?` (the form
 * androidx.sqlite binds), plus [fields] — the parameter names in placeholder order, so a
 * caller can bind each `?` by the value of its name. `::` casts and quoted string literals
 * are left untouched. This mirrors the JVM driver's `NamedParamStatement` parser; androidx
 * has no parameter-name introspection of its own, so the names must be recovered here.
 */
internal class ParsedSql private constructor(val sql: String, val fields: List<String>) {
    companion object {
        // Parsing depends only on the SQL text, which repeats across calls of the same
        // statement — cache it. ConcurrentHashMap keeps it safe across the connection pool.
        private val cache = ConcurrentHashMap<String, ParsedSql>()

        fun of(sql: String): ParsedSql = cache.getOrPut(sql) { parse(sql) }

        private fun parse(sql: String): ParsedSql {
            val out = StringBuilder(sql.length)
            val fields = ArrayList<String>()
            var i = 0
            while (i < sql.length) {
                val c = sql[i]
                when {
                    c == '\'' || c == '"' -> {
                        // Copy a quoted literal verbatim so ':' inside it is not a parameter.
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
            return ParsedSql(out.toString(), fields)
        }
    }
}
