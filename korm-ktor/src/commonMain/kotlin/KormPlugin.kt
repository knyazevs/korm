package io.github.knyazevs.korm.ktor

import io.github.knyazevs.korm.database.Database
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.MonitoringEvent
import io.ktor.server.application.log

/** Configuration for the [Korm] plugin. */
class KormConfig {
    internal val managed = mutableListOf<Database<*>>()

    /**
     * Registers [db] to be [Database.close]d when the application stops. Use this only if you
     * are NOT managing the database's lifecycle elsewhere — Ktor's built-in DI already closes
     * `AutoCloseable` dependencies (which a [Database] is) on shutdown, so registering it there
     * makes this plugin unnecessary.
     */
    fun manage(db: Database<*>) {
        managed += db
    }
}

/**
 * A minimal lifecycle plugin: closes every database registered via [KormConfig.manage] when the
 * application stops. It does not store or expose the databases — access stays through your own
 * reference, a DI container, or the `korm-ktor-di` / `korm-ktor-koin` helpers.
 *
 * ```
 * install(Korm) { manage(database) }
 * ```
 */
val Korm = createApplicationPlugin(name = "Korm", createConfiguration = ::KormConfig) {
    val managed = pluginConfig.managed.toList()
    on(MonitoringEvent(ApplicationStopped)) { application ->
        managed.forEach { db ->
            runCatching { db.close() }.onFailure { application.log.warn("Failed to close database", it) }
        }
    }
}
