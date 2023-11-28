package s.knyazev


data class Query(
    val whereExpression: Expression? = null,
    val limit: UInt = UInt.MAX_VALUE,
    val offset: UInt = 0u,
    val orderBy: Map<Column<*, *, *>, AscDescOrder>? = null
) {
    override fun toString(): String {
        val generatedString = "${whereExpression?.let{"WHERE $whereExpression "}}${if(limit!=UInt.MAX_VALUE) {"LIMIT $limit "} else {""}}${if(offset != 0u) {"OFFSET  $offset "} else {""}}${orderBy?.let{"ORDER BY ${prepareOrderBy(orderBy)} "} ?: ""}"
        if (generatedString.isEmpty()) return ""
        return generatedString
    }

    private fun prepareOrderBy(orderBy: Map<Column<*, *, *>, AscDescOrder>?): String? {
        val items: List<String>? = orderBy?.map { (key: Column<*, *, *>, value: AscDescOrder) -> "${key.name} ${value.name}" }
        return items?.joinToString(",")
    }
}

enum class AscDescOrder{
    ASC, DESC
}
