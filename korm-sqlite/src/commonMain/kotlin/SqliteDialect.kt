package io.github.knyazevs.korm

/**
 * SQLite dialect: standard SQL rendering (double-quoted identifiers, `:name`
 * placeholders, plain `LIMIT`/`OFFSET`). SQLite has dynamic typing with type
 * *affinity*, so non-native values (UUID, BigDecimal, JSON, temporals) are stored as
 * `TEXT` and parsed back by the result-set wrapper. Korm does not own schema DDL, so
 * the dialect carries no column-type mapping.
 */
object SqliteDialect : Dialect by StandardDialect
