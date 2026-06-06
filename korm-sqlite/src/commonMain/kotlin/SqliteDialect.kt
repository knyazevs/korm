package io.github.knyazevs.korm

/**
 * SQLite dialect: standard SQL rendering (double-quoted identifiers, `:name`
 * placeholders, plain `LIMIT`/`OFFSET`) with SQLite's storage classes for column
 * types. SQLite has dynamic typing with type *affinity*, so non-native values
 * (UUID, BigDecimal, JSON, temporals) are stored as `TEXT` and parsed back by the
 * result-set wrapper.
 */
object SqliteDialect : Dialect by StandardDialect {
    override fun sqlType(type: Column.ColumnNameEnum): String = when (type) {
        Column.ColumnNameEnum.Int,
        Column.ColumnNameEnum.Short,
        Column.ColumnNameEnum.Long,
        Column.ColumnNameEnum.Boolean -> "INTEGER"

        Column.ColumnNameEnum.Double,
        Column.ColumnNameEnum.Float -> "REAL"

        Column.ColumnNameEnum.String,
        Column.ColumnNameEnum.UUID,
        Column.ColumnNameEnum.Json,
        Column.ColumnNameEnum.BigDecimal,
        Column.ColumnNameEnum.Instant,
        Column.ColumnNameEnum.LocalDate,
        Column.ColumnNameEnum.LocalTime,
        Column.ColumnNameEnum.LocalDateTime -> "TEXT"
    }
}
