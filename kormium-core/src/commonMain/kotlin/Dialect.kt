package io.github.kormium

/**
 * Backend-specific SQL rendering. The agnostic core builds statements through a
 * [Dialect] instead of hardcoding one SQL flavour, so other backends (SQLite, ...)
 * can plug in their own rendering.
 */
interface Dialect {
    /** Quotes a column/table identifier. */
    fun quoteIdentifier(name: String): String

    /** Renders the bind placeholder for [name], optionally casting based on [value]'s type. */
    fun renderBind(name: String, value: Any?): String

    /** Renders a `LIMIT` clause (trailing space included), or "" when unbounded. */
    fun renderLimit(limit: UInt): String

    /** Renders an `OFFSET` clause (trailing space included), or "" when zero. */
    fun renderOffset(offset: UInt): String

    /**
     * SQL that acquires a transaction-scoped advisory lock keyed by [key], or `null` when the
     * backend has no such mechanism. Used by `kormium-migrate` to serialize a migration run across
     * concurrently-starting application instances; the lock is released automatically when the
     * surrounding transaction commits. The default is `null` (no lock).
     */
    fun advisoryLockSql(key: Long): String? = null
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
}
