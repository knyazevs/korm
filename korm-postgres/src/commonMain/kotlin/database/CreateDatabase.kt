package io.github.kormium.database

import io.github.kormium.KormBuilder
import io.github.kormium.KormConfig
import io.github.kormium.PostgresDriver

expect fun createDatabase(
    host: String,
    port: Int = 5432,
    database: String,
    user: String,
    password: String,
    poolSize: Int = 10,
    config: KormConfig = KormConfig(),
): PostgresDriver

/**
 * Opens a PostgreSQL database with a configuration block: `createDatabase(host = …, …) {`
 * `config { … }; beforeStart { migrate(appMigrations) } }`. See [KormBuilder].
 */
fun createDatabase(
    host: String,
    port: Int = 5432,
    database: String,
    user: String,
    password: String,
    poolSize: Int = 10,
    block: KormBuilder.() -> Unit,
): PostgresDriver = KormBuilder().apply(block).finish {
    createDatabase(host, port, database, user, password, poolSize, it)
}
