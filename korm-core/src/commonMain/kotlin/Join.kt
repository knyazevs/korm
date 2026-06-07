package io.github.knyazevs.korm

import io.github.knyazevs.korm.resultset.ResultSet

/**
 * Something that can appear in a SELECT list and be read back from a [ResultRow]: a
 * [Column] or an aggregate (see Aggregate.kt). It is also an [Expression] (its [toSql]
 * is the SELECT-list SQL), so aggregates can be used in `having(...)`. [Z] is the value
 * type read.
 */
interface Selectable<Z> : Expression {
    /** Reads this field's value from the result row at [index]. */
    fun read(rs: ResultSet, index: Int, typeMapper: TypeMapper): Z?
}

internal enum class JoinType(val sql: String) { INNER("INNER JOIN"), LEFT("LEFT JOIN") }

internal class JoinClause<G : Catalog>(val type: JoinType, val table: Table<G, *>, val on: Expression)

/**
 * A query over one or more tables. Read it inside a scope with `select(...)` (returning
 * [ResultRow]s) or `select(...) { row -> ... }` (mapping each row). A two-table join built
 * from two tables ([JoinPair]) can also be read as entity pairs with `find()`. Supports
 * `where`, `groupBy`, `having` and `distinct`.
 */
class Join<G : Catalog> internal constructor(
    internal val base: Table<G, *>,
    internal val clauses: List<JoinClause<G>>,
    internal val whereExpr: Expression?,
    internal val groupByCols: List<Column<*, *, *>> = emptyList(),
    internal val havingExpr: Expression? = null,
    internal val distinct: Boolean = false,
) {
    /** All tables in the join, base first. */
    internal val tables: List<Table<G, *>> get() = listOf(base) + clauses.map { it.table }

    private fun copy(
        whereExpr: Expression? = this.whereExpr,
        clauses: List<JoinClause<G>> = this.clauses,
        groupByCols: List<Column<*, *, *>> = this.groupByCols,
        havingExpr: Expression? = this.havingExpr,
        distinct: Boolean = this.distinct,
    ) = Join(base, clauses, whereExpr, groupByCols, havingExpr, distinct)

    /** Restricts the joined rows; combined with AND if called more than once. */
    fun where(condition: Expression): Join<G> = copy(whereExpr = whereExpr?.let { it and condition } ?: condition)

    /** Groups rows by [columns] (for use with aggregates). */
    fun groupBy(vararg columns: Column<*, *, *>): Join<G> = copy(groupByCols = groupByCols + columns)

    /** Filters groups (combined with AND if called more than once). */
    fun having(condition: Expression): Join<G> = copy(havingExpr = havingExpr?.let { it and condition } ?: condition)

    /** Selects distinct rows. */
    fun distinct(): Join<G> = copy(distinct = true)

    infix fun innerJoin(other: Table<G, *>): JoinStep<G> = JoinStep(this, JoinType.INNER, other)
    infix fun leftJoin(other: Table<G, *>): JoinStep<G> = JoinStep(this, JoinType.LEFT, other)
}

/** Starts a query over a single table (for aggregates / grouping / distinct). */
fun <G : Catalog> Table<G, *>.query(): Join<G> = Join(this, emptyList(), null)

/** A pending (erased) join awaiting its ON condition. */
class JoinStep<G : Catalog> internal constructor(
    private val join: Join<G>,
    private val type: JoinType,
    private val table: Table<G, *>,
) {
    infix fun on(condition: Expression): Join<G> =
        Join(join.base, join.clauses + JoinClause(type, table, condition), join.whereExpr, join.groupByCols, join.havingExpr, join.distinct)
}

/**
 * A two-table join that keeps both entity types, so it can be read as entity pairs
 * (`find()`) in addition to the `select(...)` forms. Joining a third table — or grouping —
 * degrades to an (erased) [Join] that supports only the `select(...)` forms.
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

    fun groupBy(vararg columns: Column<*, *, *>): Join<G> = asJoin().groupBy(*columns)
    fun distinct(): Join<G> = asJoin().distinct()

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
 * One row of a query result. Read fields by the [Selectable] you selected:
 * `row[Users.name]` / `row[total]` (throws if NULL/absent) or `row.getOrNull(...)`.
 */
class ResultRow internal constructor(private val values: Map<Any, Any?>) {
    operator fun <Z> get(field: Selectable<Z>): Z {
        @Suppress("UNCHECKED_CAST")
        return (values[fieldKey(field)] as Z?)
            ?: error("Selected field is NULL or was not selected; use getOrNull for nullable fields")
    }

    fun <Z> getOrNull(field: Selectable<Z>): Z? {
        @Suppress("UNCHECKED_CAST")
        return values[fieldKey(field)] as Z?
    }
}

// Identifies a selected field. A Column's property delegate yields a fresh instance on each
// access, so instance identity can't be used — key columns by table+name instead; aggregates
// (held by the caller in a val) are keyed by instance.
internal fun fieldKey(field: Selectable<*>): Any =
    if (field is Column<*, *, *>) "${field.tableRef.meta.tableName}.${field.name}" else field

// Qualifies a table reference. The schema prefix is emitted only when a schema is set;
// an unqualified name resolves through the connection's default schema (search_path / main).
internal fun Table<*, *>.qualifiedName(dialect: Dialect): String {
    val table = dialect.quoteIdentifier(meta.tableName)
    val schema = meta.schema?.let { dialect.quoteIdentifier(it) }
    return if (schema != null) "$schema.$table" else table
}

internal fun Join<*>.allColumns(): List<Column<*, *, *>> =
    tables.flatMap { it.getFieldDisplayNames().values }

// Builds and runs the SELECT, reading each field positionally (columns are emitted
// qualified, e.g. "users"."id", so colliding names don't clash).
internal fun runSelect(exec: SqlExecutor, join: Join<*>, fields: List<Selectable<*>>): List<ResultRow> {
    val builder = ParamBuilder(exec.dialect, exec.typeMapper, qualifyColumns = true)
    val selectList = fields.joinToString(", ") { it.toSql(builder) }
    val from = StringBuilder(join.base.qualifiedName(builder.dialect))
    for (clause in join.clauses) {
        from.append(" ${clause.type.sql} ${clause.table.qualifiedName(builder.dialect)} ON ${clause.on.toSql(builder)}")
    }
    val distinct = if (join.distinct) "DISTINCT " else ""
    val where = join.whereExpr?.let { " WHERE ${it.toSql(builder)}" }.orEmpty()
    val groupBy = if (join.groupByCols.isEmpty()) ""
        else " GROUP BY ${join.groupByCols.joinToString(", ") { it.toSql(builder) }}"
    val having = join.havingExpr?.let { " HAVING ${it.toSql(builder)}" }.orEmpty()
    val sql = "SELECT $distinct$selectList FROM $from$where$groupBy$having"
    return exec.execute(sql, builder.params) { rs ->
        ResultRow(fields.withIndex().associate { (i, f) -> fieldKey(f) to f.read(rs, i, exec.typeMapper) })
    }
}
