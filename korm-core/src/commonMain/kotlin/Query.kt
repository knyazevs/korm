package io.github.knyazevs.korm


data class Query(
    val whereExpression: Expression? = null,
    val limit: UInt = UInt.MAX_VALUE,
    val offset: UInt = 0u,
    val orderBy: Map<Column<*, *, *>, AscDescOrder>? = null
) {
    /**
     * Renders this query's clauses to SQL, registering any compared values as
     * bind parameters on [builder] instead of inlining them. Identifier quoting and
     * LIMIT/OFFSET rendering go through the builder's [Dialect].
     */
    fun toSql(builder: ParamBuilder): String {
        val whereStr = whereExpression?.let { "WHERE ${it.toSql(builder)} " } ?: ""
        val orderByStr = orderBy?.let { "ORDER BY ${prepareOrderBy(it, builder)} " } ?: ""
        val limitStr = builder.dialect.renderLimit(limit)
        val offsetStr = builder.dialect.renderOffset(offset)
        return "$whereStr$orderByStr$limitStr$offsetStr"
    }

    // Debug-friendly rendering; placeholders are emitted in place of values.
    override fun toString(): String = toSql(ParamBuilder(StandardDialect, StandardTypeMapper))

    private fun prepareOrderBy(orderBy: Map<Column<*, *, *>, AscDescOrder>, builder: ParamBuilder): String =
        orderBy.entries.joinToString(",") { (key, value) -> "${builder.dialect.quoteIdentifier(key.name)} ${value.name}" }
}

enum class AscDescOrder{
    ASC, DESC
}
