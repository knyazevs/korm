package io.github.knyazevs.korm


data class Query(
    val whereExpression: Expression? = null,
    val limit: UInt = UInt.MAX_VALUE,
    val offset: UInt = 0u,
    val orderBy: Map<Column<*, *, *>, AscDescOrder>? = null
) {
    /**
     * Renders this query's clauses to SQL, registering any compared values as
     * bind parameters on [builder] instead of inlining them.
     */
    fun toSql(builder: ParamBuilder): String {
        val whereStr = whereExpression?.let { "WHERE ${it.toSql(builder)} " } ?: ""
        val orderByStr = orderBy?.let { "ORDER BY ${prepareOrderBy(it)} " } ?: ""
        val limitStr = if (limit != UInt.MAX_VALUE) "LIMIT $limit " else ""
        val offsetStr = if (offset != 0u) "OFFSET $offset " else ""
        return "$whereStr$orderByStr$limitStr$offsetStr"
    }

    // Debug-friendly rendering; placeholders are emitted in place of values.
    override fun toString(): String = toSql(ParamBuilder())

    private fun prepareOrderBy(orderBy: Map<Column<*, *, *>, AscDescOrder>): String =
        orderBy.entries.joinToString(",") { (key, value) -> "${quoteIdentifier(key.name)} ${value.name}" }
}

enum class AscDescOrder{
    ASC, DESC
}
