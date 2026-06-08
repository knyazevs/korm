package io.github.knyazevs.korm

import io.github.knyazevs.korm.database.SuspendDatabase
import io.github.knyazevs.korm.resultset.ResultSet

/**
 * The suspend counterpart of [Scope], the receiver inside a [suspendTransaction] /
 * [suspendAutocommit] block. It pins one connection (via a [SuspendSqlExecutor]) and
 * exposes the same table operations as [Scope], constrained to `Table<G, _>`, but as
 * `suspend` functions — so the block may itself suspend (call other suspend code) while
 * the connection stays pinned. Raw SQL run through [execute] / [executeUpdate] goes to
 * the same pinned connection.
 */
class SuspendScope<G : Catalog> internal constructor(
    private val exec: SuspendSqlExecutor,
    /** The owning database's configuration (e.g. the default [BatchInsertMode]). */
    internal val config: KormConfig = KormConfig(),
) {
    private var savepointCounter = 0

    /** Inserts [entity]; see [Scope.insert]. */
    suspend fun <T : Entity> Table<G, T>.insert(entity: T, returning: Boolean = false): T? =
        insert(entity, exec, returning)

    /** Inserts all [entities] in one statement; see [Scope.insertAll]. */
    suspend fun <T : Entity> Table<G, T>.insertAll(
        entities: List<T>,
        returning: Boolean = false,
        batchInsertMode: BatchInsertMode = config.batchInsertMode,
    ): List<T> = insertAll(entities, exec, returning, batchInsertMode)

    /** Insert-or-update on a single-column conflict target; see [Scope.upsert]. */
    suspend fun <T : Entity> Table<G, T>.upsert(entity: T, onConflict: Column<*, *, *>, update: T, returning: Boolean = false): T? =
        upsert(entity, listOf(onConflict), update, exec, returning)

    /** Insert-or-update on a composite conflict target; see [Scope.upsert]. */
    suspend fun <T : Entity> Table<G, T>.upsert(entity: T, onConflict: List<Column<*, *, *>>, update: T, returning: Boolean = false): T? =
        upsert(entity, onConflict, update, exec, returning)

    /** Insert-or-do-nothing on a single-column conflict target; see [Scope.insertOrIgnore]. */
    suspend fun <T : Entity> Table<G, T>.insertOrIgnore(entity: T, onConflict: Column<*, *, *>): Long =
        insertOrIgnore(entity, listOf(onConflict), exec)

    /** Insert-or-do-nothing on a composite conflict target; see [Scope.insertOrIgnore]. */
    suspend fun <T : Entity> Table<G, T>.insertOrIgnore(entity: T, onConflict: List<Column<*, *, *>>): Long =
        insertOrIgnore(entity, onConflict, exec)

    /** Counts rows matching [query] (all rows by default). */
    suspend fun <T : Entity> Table<G, T>.count(query: Query = Query()): Long = count(query, exec)

    /** Block form of [count]; see [Scope.count]. */
    suspend fun <T : Entity> Table<G, T>.count(block: QueryBuilder.() -> Unit): Long =
        count(QueryBuilder().apply(block).build(), exec)

    suspend fun <T : Entity> Table<G, T>.find(query: Query): List<T> = select(query, exec)

    /** Block form of [find]; see [Scope.find]. */
    suspend fun <T : Entity> Table<G, T>.find(block: QueryBuilder.() -> Unit): List<T> =
        select(QueryBuilder().apply(block).build(), exec)
    suspend fun <T : Entity> Table<G, T>.findById(id: Any): T? = selectById(id, exec)
    suspend fun <T : Entity> Table<G, T>.all(): List<T> = selectAll(exec)
    /** Updates rows matching [query] with the present fields of [entity]; returns the affected row count. */
    suspend fun <T : Entity> Table<G, T>.update(query: Query, entity: T): Long = updateRows(query, entity, exec)

    /** Block form of [update]; see [Scope.update]. */
    suspend fun <T : Entity> Table<G, T>.update(entity: T, block: QueryBuilder.() -> Unit): Long =
        updateRows(QueryBuilder().apply(block).build(), entity, exec)

    /** Deletes rows matching [query]; returns the affected row count. */
    suspend fun <T : Entity> Table<G, T>.deleteWhere(query: Query): Long = deleteRows(query, exec)

    /** Block form of [deleteWhere]; see [Scope.deleteWhere]. */
    suspend fun <T : Entity> Table<G, T>.deleteWhere(block: QueryBuilder.() -> Unit): Long =
        deleteRows(QueryBuilder().apply(block).build(), exec)

    suspend fun <T : Entity> Table<G, T>.execSql(sql: String) = runRaw(sql, exec)

    /** Creates this table from its column definitions (`CREATE TABLE [IF NOT EXISTS]`). */
    suspend fun <T : Entity> Table<G, T>.createTable(ifNotExists: Boolean = true) =
        exec.executeUpdate(createTableSql(exec.dialect, ifNotExists))

    /** Drops this table (`DROP TABLE [IF EXISTS]`). */
    suspend fun <T : Entity> Table<G, T>.dropTable(ifExists: Boolean = true) =
        exec.executeUpdate(dropTableSql(exec.dialect, ifExists))

    /** Runs the query, selecting the given fields (or all columns if none are given). */
    suspend fun Join<G>.select(vararg fields: Selectable<*>): List<ResultRow> =
        runSelect(exec, this, if (fields.isEmpty()) allColumns() else fields.toList())

    /** Runs the query, mapping each [ResultRow] with [map] (a projection into your own type). */
    suspend fun <R> Join<G>.select(vararg fields: Selectable<*>, map: (ResultRow) -> R): List<R> =
        select(*fields).map(map)

    /** Runs a two-table join, selecting the given fields (or all columns if none are given). */
    suspend fun <A : Entity, B : Entity> JoinPair<G, A, B>.select(vararg fields: Selectable<*>): List<ResultRow> =
        asJoin().select(*fields)

    /** Runs a two-table join, mapping each [ResultRow] with [map]. */
    suspend fun <A : Entity, B : Entity, R> JoinPair<G, A, B>.select(vararg fields: Selectable<*>, map: (ResultRow) -> R): List<R> =
        asJoin().select(*fields, map = map)

    /** Runs a two-table join, reconstructing both sides as a `Pair` of entities. */
    suspend fun <A : Entity, B : Entity> JoinPair<G, A, B>.find(): List<Pair<A, B>> {
        val aCols = left.getFieldDisplayNames()
        val bCols = right.getFieldDisplayNames()
        val rows = runSelect(exec, asJoin(), (aCols.values + bCols.values).toList())
        return rows.map { row ->
            left.hydrate(aCols.mapValues { (_, c) -> row.getOrNull(c) }.toMutableMap()) to
                right.hydrate(bCols.mapValues { (_, c) -> row.getOrNull(c) }.toMutableMap())
        }
    }

    /** Runs a raw query on the pinned connection, mapping each row with [handler]. */
    suspend fun <R> execute(sql: String, params: Map<String, Any?> = emptyMap(), handler: (ResultSet) -> R): List<R> =
        exec.execute(sql, params, handler)

    /** Runs raw SQL on the pinned connection, returning the row/update count. */
    suspend fun execute(sql: String, params: Map<String, Any?> = emptyMap()): Long = exec.execute(sql, params)

    /** Runs a raw statement (DDL/DML) on the pinned connection. */
    suspend fun executeUpdate(sql: String, params: Map<String, Any?> = emptyMap()) = exec.executeUpdate(sql, params)

    /**
     * Runs [block] inside a SAVEPOINT on the same connection: if it throws, only its
     * work is rolled back (ROLLBACK TO SAVEPOINT) and the exception propagates; the
     * enclosing transaction may continue if the caller catches it.
     */
    suspend fun <R> savepoint(block: suspend SuspendScope<G>.() -> R): R {
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
 * ROLLBACK if it throws. The suspend counterpart of [transaction]; [block] may itself
 * suspend while the connection stays pinned. Whether this is true async or a blocking
 * driver offloaded to a dispatcher depends on the backend's [SuspendDatabase.useConnection].
 */
suspend fun <G : Catalog, R> SuspendDatabase<G>.suspendTransaction(block: suspend SuspendScope<G>.() -> R): R =
    useConnection(transactional = true) { SuspendScope<G>(it, config).block() }

/**
 * Runs [block] on a pinned connection in autocommit (no surrounding transaction) — the
 * suspend counterpart of [autocommit], the cheap path for reads / single statements.
 */
suspend fun <G : Catalog, R> SuspendDatabase<G>.suspendAutocommit(block: suspend SuspendScope<G>.() -> R): R =
    useConnection(transactional = false) { SuspendScope<G>(it, config).block() }
