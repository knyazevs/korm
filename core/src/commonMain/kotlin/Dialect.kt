package io.github.knyazevs.korm

/**
 * Backend-specific SQL rendering. The agnostic core builds statements through a
 * [Dialect] instead of hardcoding one SQL flavour, so other backends (SQLite, ...)
 * can plug in their own rendering.
 */
interface Dialect {
    /** Quotes a column/table/schema identifier. */
    fun quoteIdentifier(name: String): String

    /** Renders the bind placeholder for [name], optionally casting based on [value]'s type. */
    fun renderBind(name: String, value: Any?): String

    /** Renders a `LIMIT` clause (trailing space included), or "" when unbounded. */
    fun renderLimit(limit: UInt): String

    /** Renders an `OFFSET` clause (trailing space included), or "" when zero. */
    fun renderOffset(offset: UInt): String

    /** The SQL column type for [type], e.g. "uuid", "numeric", "timestamptz". */
    fun sqlType(type: Column.ColumnNameEnum): String
}

/**
 * Standard-SQL rendering: double-quoted identifiers, `:name` placeholders, plain
 * `LIMIT`/`OFFSET`. Serves as the agnostic default (e.g. [Query] debug rendering)
 * and a base that backend dialects can delegate to.
 */
object StandardDialect : Dialect {
    override fun quoteIdentifier(name: String): String =
        "\"${name.replace("\"", "\"\"")}\""

    override fun renderBind(name: String, value: Any?): String = ":$name"

    override fun renderLimit(limit: UInt): String =
        if (limit != UInt.MAX_VALUE) "LIMIT $limit " else ""

    override fun renderOffset(offset: UInt): String =
        if (offset != 0u) "OFFSET $offset " else ""

    override fun sqlType(type: Column.ColumnNameEnum): String = when (type) {
        Column.ColumnNameEnum.UUID -> "uuid"
        Column.ColumnNameEnum.BigDecimal -> "numeric"
        Column.ColumnNameEnum.Double -> "double precision"
        Column.ColumnNameEnum.Int -> "integer"
        Column.ColumnNameEnum.Boolean -> "boolean"
        Column.ColumnNameEnum.String -> "text"
        Column.ColumnNameEnum.Instant -> "timestamp"
        Column.ColumnNameEnum.Json -> "json"
    }
}
