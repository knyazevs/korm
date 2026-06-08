package io.github.knyazevs.korm

/**
 * Opens a SQLite database and returns a [SqliteDriver].
 *
 * @param path the database file path, or `":memory:"` (the default) for an in-memory
 *   database. An in-memory database is opened in shared-cache mode so a pool of
 *   connections all see the same database; it lives only while the driver is open.
 *   A file-backed database is opened in WAL (write-ahead logging) mode for better
 *   read/write concurrency.
 * @param poolSize how many connections to keep. SQLite allows a single writer, so the
 *   default is 1 (everything serialised, no `database is locked`); raise it for
 *   concurrent reads (WAL permits many readers alongside one writer).
 */
expect fun createSqliteDatabase(
    path: String = ":memory:",
    poolSize: Int = 1,
    config: KormConfig = KormConfig(),
): SqliteDriver
