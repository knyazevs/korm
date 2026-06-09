package io.github.kormium

import io.github.kormium.database.Database

/**
 * Mutable form of [KormConfig], used by the `createX { }` builders. It starts from an existing
 * config and produces a new one via `copy`, so adding a field to [KormConfig] only needs a
 * matching `var` here (no default is duplicated). See [KormBuilder.config].
 */
class KormConfigBuilder internal constructor(private val base: KormConfig) {
    /** See [KormConfig.batchInsertMode]. */
    var batchInsertMode: BatchInsertMode = base.batchInsertMode

    internal fun build(): KormConfig = base.copy(
        batchInsertMode = batchInsertMode,
    )
}

/**
 * Receiver of the `createX { }` builder block. Configures the database and registers a
 * [beforeStart] hook that runs once, after the connection pool is up but before the database
 * is returned — the place to run migrations (the `korm-migrate` module, or Flyway/Liquibase).
 * Migrations are intentionally NOT a built-in concern of this builder.
 */
class KormBuilder {
    private var config: KormConfig = KormConfig()
    private var beforeStart: (Database<Nothing>.() -> Unit)? = null

    /** Configures the [KormConfig] carried by the database. Calls accumulate. */
    fun config(block: KormConfigBuilder.() -> Unit) {
        config = KormConfigBuilder(config).apply(block).build()
    }

    /**
     * Runs [block] once at startup, before the database is returned. Use it to run migrations
     * (the `korm-migrate` module): the receiver is the database, so a migration list resolves its
     * own catalog, e.g. `beforeStart { migrate(appMigrations) }`. For Flyway/Liquibase, configure
     * them here with your connection settings (they manage their own JDBC connection). Seed data
     * belongs in a migration, not here.
     */
    fun beforeStart(block: Database<Nothing>.() -> Unit) {
        beforeStart = block
    }

    /**
     * Builds the database with [create] (passing the configured [KormConfig]), then runs the
     * [beforeStart] hook on it. Called by the `createX { }` factory overloads.
     */
    fun <D : Database<Nothing>> finish(create: (KormConfig) -> D): D =
        create(config).also { driver -> beforeStart?.invoke(driver) }
}
