package io.github.knyazevs.korm

import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.database.SuspendDatabase

/**
 * A SQLite-backed [Database] (and [SuspendDatabase]). This is the type returned by
 * [createSqliteDatabase]; it adds [AutoCloseable] so the underlying connection(s) can be
 * released (or used via a `use { }` block). Blocking query methods come from [Database];
 * the suspend path (suspendTransaction/suspendAutocommit) comes from [SuspendDatabase].
 */
interface SqliteDriver : Database<Nothing>, SuspendDatabase<Nothing>, AutoCloseable {
    // Resolves the config default inherited from both Database and SuspendDatabase; concrete
    // drivers supply it (from the createSqliteDatabase config argument).
    override val config: KormConfig
}
