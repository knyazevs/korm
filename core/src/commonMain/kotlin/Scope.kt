package io.github.knyazevs.korm

import io.github.knyazevs.korm.database.Database

/**
 * The receiver inside a [transaction] / [autocommit] block. It pins one connection
 * and exposes table operations as member-extensions constrained to `Table<G, _>`,
 * so using a table from a different catalog is a compile error. Raw SQL run through
 * [execute] / [executeUpdate] goes to the same pinned connection.
 */
class Scope<G : Catalog> internal constructor(private val exec: SqlExecutor) {
    private var savepointCounter = 0

    fun <T : Entity> Table<G, T>.new(entity: T) = insert(entity, exec)
    fun <T : Entity> Table<G, T>.find(query: Query): List<T> = select(query, exec)
    fun <T : Entity> Table<G, T>.findById(id: Any): T? = selectById(id, exec)
    fun <T : Entity> Table<G, T>.all(): List<T> = selectAll(exec)
    fun <T : Entity> Table<G, T>.update(query: Query, entity: T) = updateRows(query, entity, exec)
    fun <T : Entity> Table<G, T>.deleteWhere(query: Query) = deleteRows(query, exec)
    fun <T : Entity> Table<G, T>.execSql(sql: String) = runRaw(sql, exec)

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
    usePinned(transactional = true) { Scope<G>(it).block() }

/**
 * Runs [block] on a pinned connection in autocommit (no surrounding transaction) —
 * the cheap path for reads / single statements.
 */
fun <G : Catalog, R> Database<G>.autocommit(block: Scope<G>.() -> R): R =
    usePinned(transactional = false) { Scope<G>(it).block() }
