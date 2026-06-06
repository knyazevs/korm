package io.github.knyazevs.korm

import io.github.knyazevs.korm.database.Database

/**
 * A SQLite-backed [Database]. This is the type returned by [createSqliteDatabase];
 * it adds [AutoCloseable] so the underlying connection(s) can be released (or used
 * via a `use { }` block). All query methods are inherited from [Database].
 */
interface SqliteDriver : Database<Nothing>, AutoCloseable
