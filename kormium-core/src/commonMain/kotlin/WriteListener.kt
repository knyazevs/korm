package io.github.kormium

import kotlin.concurrent.Volatile

/**
 * Notified after a [transaction] / [autocommit] block (or their suspend counterparts)
 * commits, with the set of table names written during it. This is the generic,
 * non-reactive seam the rest of the system builds on: the `kormium-observe` module turns
 * it into a `Flow`, but it is equally useful for cache invalidation, audit or metrics.
 *
 * [onCommit] is called synchronously on the thread that ran the transaction, so keep it
 * cheap and non-blocking — fan out to a coroutine/`Flow` off the hot path if you need to.
 */
fun interface WriteListener {
    /** [tables] is the non-empty set of table names written by the just-committed block. */
    fun onCommit(tables: Set<String>)
}

/** Handle returned by [WriteListeners.add]; call [remove] to unregister the listener. */
fun interface Registration {
    fun remove()
}

/**
 * Per-database registry of [WriteListener]s. A backend that supports change notification
 * exposes its own instance through [io.github.kormium.database.Database.writeListeners] /
 * [io.github.kormium.database.SuspendDatabase.writeListeners]; backends that do not get
 * the shared [Disabled] registry, which ignores everything at zero cost (so observation
 * simply never fires there).
 *
 * Registration is expected at setup time (few listeners, rarely changed); the registry is
 * copy-on-write so [fire] iterates a stable snapshot without locking on the hot path.
 */
open class WriteListeners {
    @Volatile
    private var listeners: List<WriteListener> = emptyList()

    /** Registers [listener]; returns a [Registration] that removes it again. */
    open fun add(listener: WriteListener): Registration {
        listeners = listeners + listener
        return Registration {
            listeners = listeners.filterNot { it === listener }
        }
    }

    /** Delivers [tables] to every registered listener. No-op when [tables] is empty. */
    fun fire(tables: Set<String>) {
        if (tables.isEmpty()) return
        val snapshot = listeners
        for (l in snapshot) l.onCommit(tables)
    }

    /** True if at least one listener is registered (lets callers skip dirty-set bookkeeping). */
    val isActive: Boolean get() = listeners.isNotEmpty()

    /** The shared no-op registry for backends that don't support write notification. */
    object Disabled : WriteListeners() {
        override fun add(listener: WriteListener): Registration = Registration {}
    }
}
