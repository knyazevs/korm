package io.github.knyazevs.korm

/**
 * Builder behind the block form of reads and mutations:
 *
 * ```kotlin
 * Users.find {
 *     where { Users.age gtEq 18 }
 *     where { Users.name like "A%" }
 *     orderBy DESC Users.age
 *     orderBy ASC Users.name
 *     limit = 50
 *     offset = 100
 * }
 * ```
 *
 * It is a thin, ergonomic layer over [Query]: an empty block builds `Query()` (no `WHERE`,
 * no ordering, no limit/offset). [Query] stays available for reusable/prebuilt queries.
 */
class QueryBuilder {
    private val conditions = mutableListOf<Expression>()
    private val orderings = LinkedHashMap<Column<*, *, *>, AscDescOrder>()

    /** `orderBy DESC Users.age`, `orderBy ASC Users.name`. Multiple calls keep their order. */
    val orderBy: OrderByDsl = OrderByDsl(orderings)

    /** No `LIMIT` when left null. */
    var limit: Int? = null

    /** No `OFFSET` when left null. */
    var offset: Int? = null

    /**
     * Adds a predicate. Multiple `where { ... }` calls combine with `AND`; put complex
     * boolean logic inside a single block using `and` / `or` / `not(...)`.
     */
    fun where(block: () -> Expression) {
        conditions += block()
    }

    internal fun build(): Query {
        val whereExpression = when (conditions.size) {
            0 -> null
            1 -> conditions[0]
            // Parenthesize each block so a block-internal `or` keeps the right precedence
            // when blocks are AND-combined: `(a OR b) AND c`, not `a OR b AND c`.
            else -> conditions.map { ParenExpression(it) as Expression }.reduce { acc, e -> AndOp(acc, e) }
        }
        return Query(
            whereExpression = whereExpression,
            limit = limit?.toUInt() ?: UInt.MAX_VALUE,
            offset = offset?.toUInt() ?: 0u,
            orderBy = orderings.ifEmpty { null },
        )
    }
}

/** Infix ordering for [QueryBuilder.orderBy]: `orderBy DESC column`, `orderBy ASC column`. */
class OrderByDsl internal constructor(private val orderings: MutableMap<Column<*, *, *>, AscDescOrder>) {
    infix fun ASC(column: Column<*, *, *>) {
        orderings[column] = AscDescOrder.ASC
    }

    infix fun DESC(column: Column<*, *, *>) {
        orderings[column] = AscDescOrder.DESC
    }
}
