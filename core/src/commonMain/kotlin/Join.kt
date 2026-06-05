package io.github.knyazevs.korm

internal enum class JoinType(val sql: String) { INNER("INNER JOIN"), LEFT("LEFT JOIN") }

internal class JoinClause<G : Catalog>(val type: JoinType, val table: Table<G, *>, val on: Expression)

/**
 * An N-table join. Read it inside a scope with `select(...)` (returning [ResultRow]s)
 * or `select(...) { row -> ... }` (mapping each row). A two-table join built directly
 * from two tables ([JoinPair]) can also be read as entity pairs with `find()`.
 */
class Join<G : Catalog> internal constructor(
    internal val base: Table<G, *>,
    internal val clauses: List<JoinClause<G>>,
    internal val whereExpr: Expression?,
) {
    /** All tables in the join, base first. */
    internal val tables: List<Table<G, *>> get() = listOf(base) + clauses.map { it.table }

    /** Restricts the joined rows; combined with AND if called more than once. */
    fun where(condition: Expression): Join<G> =
        Join(base, clauses, whereExpr?.let { it and condition } ?: condition)

    infix fun innerJoin(other: Table<G, *>): JoinStep<G> = JoinStep(this, JoinType.INNER, other)
    infix fun leftJoin(other: Table<G, *>): JoinStep<G> = JoinStep(this, JoinType.LEFT, other)
}

/** A pending (erased) join awaiting its ON condition. */
class JoinStep<G : Catalog> internal constructor(
    private val join: Join<G>,
    private val type: JoinType,
    private val table: Table<G, *>,
) {
    infix fun on(condition: Expression): Join<G> =
        Join(join.base, join.clauses + JoinClause(type, table, condition), join.whereExpr)
}

/**
 * A two-table join that keeps both entity types, so it can be read as entity pairs
 * (`find()`) in addition to the `select(...)` forms. Joining a third table degrades
 * to an (erased) [Join] that supports only the `select(...)` forms.
 */
class JoinPair<G : Catalog, A : Entity, B : Entity> internal constructor(
    internal val left: Table<G, A>,
    internal val right: Table<G, B>,
    private val type: JoinType,
    private val on: Expression,
    internal val whereExpr: Expression?,
) {
    fun where(condition: Expression): JoinPair<G, A, B> =
        JoinPair(left, right, type, on, whereExpr?.let { it and condition } ?: condition)

    internal fun asJoin(): Join<G> = Join(left, listOf(JoinClause(type, right, on)), whereExpr)

    infix fun innerJoin(other: Table<G, *>): JoinStep<G> = asJoin().innerJoin(other)
    infix fun leftJoin(other: Table<G, *>): JoinStep<G> = asJoin().leftJoin(other)
}

/** A two-table join awaiting its ON condition, keeping both entity types. */
class JoinPairStep<G : Catalog, A : Entity, B : Entity> internal constructor(
    private val left: Table<G, A>,
    private val right: Table<G, B>,
    private val type: JoinType,
) {
    infix fun on(condition: Expression): JoinPair<G, A, B> = JoinPair(left, right, type, condition, null)
}

infix fun <G : Catalog, A : Entity, B : Entity> Table<G, A>.innerJoin(other: Table<G, B>): JoinPairStep<G, A, B> =
    JoinPairStep(this, other, JoinType.INNER)

infix fun <G : Catalog, A : Entity, B : Entity> Table<G, A>.leftJoin(other: Table<G, B>): JoinPairStep<G, A, B> =
    JoinPairStep(this, other, JoinType.LEFT)

/**
 * One row of a join result. Read columns by the [Column] you selected:
 * `row[Users.name]` (throws if NULL/absent) or `row.getOrNull(Orders.total)`.
 */
class ResultRow internal constructor(private val values: Map<Column<*, *, *>, Any?>) {
    operator fun <Z> get(column: Column<Z, *, *>): Z {
        @Suppress("UNCHECKED_CAST")
        return (values[column] as Z?)
            ?: error("Column \"${column.name}\" is NULL or was not selected; use getOrNull for nullable columns")
    }

    fun <Z> getOrNull(column: Column<Z, *, *>): Z? {
        @Suppress("UNCHECKED_CAST")
        return values[column] as Z?
    }
}

internal fun Table<*, *>.qualifiedName(dialect: Dialect): String =
    "${dialect.quoteIdentifier(meta.schema)}.${dialect.quoteIdentifier(meta.tableName)}"

internal fun Join<*>.allColumns(): List<Column<*, *, *>> =
    tables.flatMap { it.getFieldDisplayNames().values }

// Builds and runs the join SELECT, reading each selected column positionally (the
// columns are emitted qualified, e.g. "users"."id", so colliding names don't clash).
internal fun runSelect(exec: SqlExecutor, join: Join<*>, columns: List<Column<*, *, *>>): List<ResultRow> {
    val builder = ParamBuilder(exec.dialect, exec.typeMapper, qualifyColumns = true)
    val selectList = columns.joinToString(", ") { it.toSql(builder) }
    val from = StringBuilder(join.base.qualifiedName(builder.dialect))
    for (clause in join.clauses) {
        from.append(" ${clause.type.sql} ${clause.table.qualifiedName(builder.dialect)} ON ${clause.on.toSql(builder)}")
    }
    val where = join.whereExpr?.let { " WHERE ${it.toSql(builder)}" }.orEmpty()
    val sql = "SELECT $selectList FROM $from$where"
    return exec.execute(sql, builder.params) { rs ->
        ResultRow(columns.withIndex().associate { (i, c) -> c to exec.typeMapper.fromResult(rs, i, c.columnType) })
    }
}
