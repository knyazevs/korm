package io.github.knyazevs.korm.database

import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.KormConfig
import io.github.knyazevs.korm.SuspendSqlExecutor

/**
 * The suspend counterpart of [Database], tagged with the [Catalog] [G] it connects to.
 *
 * It is a SIBLING of [Database], NOT a subtype: a truly async backend (e.g. r2dbc)
 * cannot provide the blocking [Database.usePinned], so the two hierarchies stand
 * apart. A blocking backend (JDBC/SQLite) may implement BOTH; an async backend
 * implements only this one.
 */
interface SuspendDatabase<out G : Catalog> : AutoCloseable {
    /** Per-database configuration; defaults to [KormConfig] defaults unless a backend overrides it. */
    val config: KormConfig get() = KormConfig()

    /**
     * Pins one connection for the duration of [block]; the [SuspendSqlExecutor] passed
     * to it routes every statement to that connection. Wraps BEGIN/COMMIT/ROLLBACK when
     * [transactional] is true, otherwise runs in autocommit. Backend-specific.
     */
    suspend fun <R> useConnection(transactional: Boolean, block: suspend (SuspendSqlExecutor) -> R): R

    /** Closes the underlying connection(s); the database is unusable afterwards. */
    override fun close()
}
