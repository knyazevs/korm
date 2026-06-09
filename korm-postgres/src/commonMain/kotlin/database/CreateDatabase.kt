package io.github.kormium.database

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
