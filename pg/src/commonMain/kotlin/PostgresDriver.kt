package io.github.knyazevs.korm

import io.github.knyazevs.korm.database.Database

/**
 * A Postgres-backed [Database]. This is the type returned by the driver factories;
 * it adds [AutoCloseable] so the underlying connection pool can be released
 * (or used via a `use { }` block). All query methods are inherited from [Database].
 */
interface PostgresDriver : Database, AutoCloseable
