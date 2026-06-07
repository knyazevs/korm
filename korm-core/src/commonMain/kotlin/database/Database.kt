package io.github.knyazevs.korm.database

import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.SqlExecutor

/**
 * A database handle, tagged with the [Catalog] [G] it connects to. The tag is
 * phantom (it appears in no member), so a backend driver implements
 * `Database<Nothing>` and — by covariance — fits any `Database<G>`; a caller pins
 * the tag by assigning it to a `Database<MyCatalog>`. A [io.github.knyazevs.korm.Table]
 * tagged with the same catalog can then be used against it via
 * [io.github.knyazevs.korm.transaction] / [io.github.knyazevs.korm.autocommit].
 */
interface Database<out G : Catalog> : SqlExecutor, AutoCloseable {
    /**
     * Pins one connection for the duration of [block]; the [SqlExecutor] passed to it
     * routes every statement to that connection. Wraps BEGIN/COMMIT/ROLLBACK when
     * [transactional] is true, otherwise runs in autocommit. Backend-specific.
     */
    fun <R> usePinned(transactional: Boolean, block: (SqlExecutor) -> R): R

    /** Closes the underlying connection(s); the database is unusable afterwards. */
    override fun close()
}
