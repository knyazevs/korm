package io.github.kormium.r2dbc

/** A SQL string with its `:name` placeholders rewritten to Postgres `$N`, plus the names in order. */
internal class ParsedSql(val sql: String, val names: List<String>)

/**
 * Rewrites korm's Spring-style `:name` placeholders to r2dbc-postgresql's positional
 * `$1, $2, ...` and records the names in occurrence order so values can be bound by
 * index. `::` casts (Postgres) and quoted string literals are left untouched. This is
 * the same parse as the JDBC NamedParamStatement, differing only in the emitted marker.
 */
internal fun parseNamedParams(sql: String): ParsedSql {
    val out = StringBuilder(sql.length)
    val names = ArrayList<String>()
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
            c == '-' && i + 1 < sql.length && sql[i + 1] == '-' -> {
                // Single-line comment: copy verbatim to end of line so ':name' in it is not
                // treated as a parameter.
                while (i < sql.length && sql[i] != '\n') {
                    out.append(sql[i])
                    i++
                }
            }
            c == '/' && i + 1 < sql.length && sql[i + 1] == '*' -> {
                // Block comment: copy verbatim through the closing "*/" (or to the end if
                // unterminated) so ':name' inside it is not a parameter.
                out.append("/*")
                i += 2
                while (i < sql.length) {
                    if (sql[i] == '*' && i + 1 < sql.length && sql[i + 1] == '/') {
                        out.append("*/")
                        i += 2
                        break
                    }
                    out.append(sql[i])
                    i++
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
                names.add(sql.substring(i + 1, j))
                out.append('$').append(names.size) // $1, $2, ... (1-based)
                i = j
            }
            else -> {
                out.append(c)
                i++
            }
        }
    }
    return ParsedSql(out.toString(), names)
}
