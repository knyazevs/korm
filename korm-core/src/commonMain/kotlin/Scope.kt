package io.github.kormium

import io.github.kormium.database.Database
import io.github.kormium.resultset.ResultSet

/**
 * The receiver inside a [transaction] / [autocommit] block. It pins one connection
 * and exposes table operations as member-extensions constrained to `Table<G, _>`,
 * so using a table from a different catalog is a compile error. Raw SQL run through
 * [execute] / [executeUpdate] goes to the same pinned connection.
 */
class Scope<G : Catalog> internal constructor(
    private val exec: SqlExecutor,
    /** The owning database's configuration (e.g. the default [BatchInsertMode]). */
    internal val config: KormConfig = KormConfig(),
) {
    private var savepointCounter = 0

    /**
     * Inserts [entity]. By default returns the entity as given (a plain INSERT — the fast
     * path). Pass `returning = true` to fetch the stored row back via SQL `RETURNING`, e.g. to
     * read database-generated columns; then it returns that row (or null if none).
     */
    fun <T : Entity> Table<G, T>.insert(entity: T, returning: Boolean = false): T? =
        insert(entity, exec, returning)

    /**
     * Inserts all [entities] in one statement. By default returns [entities] as given; pass
     * `returning = true` to fetch the stored rows back via SQL `RETURNING`.
     */
    fun <T : Entity> Table<G, T>.insertAll(
        entities: List<T>,
        returning: Boolean = false,
        batchInsertMode: BatchInsertMode = config.batchInsertMode,
    ): List<T> = insertAll(entities, exec, returning, batchInsertMode)

    /**
     * Insert-or-update on a single-column conflict target: inserts [entity]'s present fields,
     * and on conflict with [onConflict] updates with [update]'s present fields. Pass
     * `returning = true` to fetch the resulting row.
     */
    fun <T : Entity> Table<G, T>.upsert(entity: T, onConflict: Column<*, *, *>, update: T, returning: Boolean = false): T? =
        upsert(entity, listOf(onConflict), update, exec, returning)

    /** Insert-or-update on a composite (multi-column) conflict target; see the single-column overload. */
    fun <T : Entity> Table<G, T>.upsert(entity: T, onConflict: List<Column<*, *, *>>, update: T, returning: Boolean = false): T? =
        upsert(entity, onConflict, update, exec, returning)

    /** Insert-or-do-nothing on a single-column conflict target; returns the affected row count (1 inserted, 0 ignored). */
    fun <T : Entity> Table<G, T>.insertOrIgnore(entity: T, onConflict: Column<*, *, *>): Long =
        insertOrIgnore(entity, listOf(onConflict), exec)

    /** Insert-or-do-nothing on a composite conflict target; see the single-column overload. */
    fun <T : Entity> Table<G, T>.insertOrIgnore(entity: T, onConflict: List<Column<*, *, *>>): Long =
        insertOrIgnore(entity, onConflict, exec)

    /** Counts rows matching [query] (all rows by default). */
    fun <T : Entity> Table<G, T>.count(query: Query = Query()): Long = count(query, exec)

    /** Block form of [count]: `Users.count { where { Users.deletedAt eq null } }`. */
    fun <T : Entity> Table<G, T>.count(block: QueryBuilder.() -> Unit): Long =
        count(QueryBuilder().apply(block).build(), exec)

    fun <T : Entity> Table<G, T>.find(query: Query): List<T> = select(query, exec)

    /** Block form of [find]: `Users.find { where { ... }; orderBy DESC col; limit = 50 }`. */
    fun <T : Entity> Table<G, T>.find(block: QueryBuilder.() -> Unit): List<T> =
        select(QueryBuilder().apply(block).build(), exec)
    fun <T : Entity> Table<G, T>.findById(id: Any): T? = selectById(id, exec)
    fun <T : Entity> Table<G, T>.all(): List<T> = selectAll(exec)
    /** Updates rows matching [query] with the present fields of [entity]; returns the affected row count. */
    fun <T : Entity> Table<G, T>.update(query: Query, entity: T): Long = updateRows(query, entity, exec)

    /**
     * Block form of [update]: `Users.update(patch) { where { Users.id eq id } }`. Multiple
     * `where { }` blocks AND together; an empty block updates every row. Returns the affected
     * row count (e.g. 0 means no row matched — useful for not-found / optimistic-locking checks).
     */
    fun <T : Entity> Table<G, T>.update(entity: T, block: QueryBuilder.() -> Unit): Long =
        updateRows(QueryBuilder().apply(block).build(), entity, exec)

    /** Deletes rows matching [query]; returns the affected row count. */
    fun <T : Entity> Table<G, T>.deleteWhere(query: Query): Long = deleteRows(query, exec)

    /**
     * Block form of [deleteWhere]: `Users.deleteWhere { where { Users.deletedAt neq null } }`.
     * An empty block deletes every row. Returns the affected row count.
     */
    fun <T : Entity> Table<G, T>.deleteWhere(block: QueryBuilder.() -> Unit): Long =
        deleteRows(QueryBuilder().apply(block).build(), exec)

    fun <T : Entity> Table<G, T>.execSql(sql: String) = runRaw(sql, exec)

    /** Runs the query, selecting the given fields (or all columns if none are given). */
    fun Join<G>.select(vararg fields: Selectable<*>): List<ResultRow> =
        runSelect(exec, this, if (fields.isEmpty()) allColumns() else fields.toList())

    /** Runs the query, mapping each [ResultRow] with [map] (a projection into your own type). */
    fun <R> Join<G>.select(vararg fields: Selectable<*>, map: (ResultRow) -> R): List<R> =
        select(*fields).map(map)

    /** Runs a two-table join, selecting the given fields (or all columns if none are given). */
    fun <A : Entity, B : Entity> JoinPair<G, A, B>.select(vararg fields: Selectable<*>): List<ResultRow> =
        asJoin().select(*fields)

    /** Runs a two-table join, mapping each [ResultRow] with [map]. */
    fun <A : Entity, B : Entity, R> JoinPair<G, A, B>.select(vararg fields: Selectable<*>, map: (ResultRow) -> R): List<R> =
        asJoin().select(*fields, map = map)

    /** Runs a two-table join, reconstructing both sides as a `Pair` of entities. */
    fun <A : Entity, B : Entity> JoinPair<G, A, B>.find(): List<Pair<A, B>> =
        select().map { row -> row.entity(left) to row.entity(right) }

    /** Runs a raw query on the pinned connection, mapping each row with [handler]. */
    fun <R> execute(sql: String, params: Map<String, Any?> = emptyMap(), handler: (ResultSet) -> R): List<R> =
        exec.execute(sql, params, handler)

    /** Runs raw SQL on the pinned connection, returning the row/update count. */
    fun execute(sql: String, params: Map<String, Any?> = emptyMap()): Long = exec.execute(sql, params)

    /** Runs a raw statement (DDL/DML) on the pinned connection. */
    fun executeUpdate(sql: String, params: Map<String, Any?> = emptyMap()) = exec.executeUpdate(sql, params)

    /**
     * Runs [block] inside a SAVEPOINT on the same connection: if it throws, only its
     * work is rolled back (ROLLBACK TO SAVEPOINT) and the exception propagates; the
     * enclosing transaction may continue if the caller catches it.
     */
    fun <R> savepoint(block: Scope<G>.() -> R): R {
        val name = "korm_sp_${savepointCounter++}"
        exec.executeUpdate("SAVEPOINT $name")
        return try {
            block().also { exec.executeUpdate("RELEASE SAVEPOINT $name") }
        } catch (e: Throwable) {
            exec.executeUpdate("ROLLBACK TO SAVEPOINT $name")
            throw e
        }
    }
}

/**
 * Runs [block] in a transaction on a pinned connection: COMMIT when it returns,
 * ROLLBACK if it throws. Table operations inside are constrained to this database's
 * catalog [G]. Calling another database's `transaction` inside opens an independent
 * transaction (separate connection); use [Scope.savepoint] for a nested unit.
 */
fun <G : Catalog, R> Database<G>.transaction(block: Scope<G>.() -> R): R =
    usePinned(transactional = true) { Scope<G>(it, config).block() }

/**
 * Runs [block] on a pinned connection in autocommit (no surrounding transaction) —
 * the cheap path for reads / single statements.
 */
fun <G : Catalog, R> Database<G>.autocommit(block: Scope<G>.() -> R): R =
    usePinned(transactional = false) { Scope<G>(it, config).block() }
