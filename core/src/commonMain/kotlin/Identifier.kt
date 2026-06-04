package io.github.knyazevs.korm

/**
 * Quotes a SQL identifier (column / table / schema name) so mixed-case names and
 * reserved words survive intact. Without quoting, Postgres folds unquoted
 * identifiers to lower case, which silently breaks camelCase columns. Embedded
 * double quotes are escaped by doubling, per the SQL standard.
 */
internal fun quoteIdentifier(identifier: String): String =
    "\"${identifier.replace("\"", "\"\"")}\""
