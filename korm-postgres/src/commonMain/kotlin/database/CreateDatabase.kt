package io.github.knyazevs.korm.database

import io.github.knyazevs.korm.KormConfig
import io.github.knyazevs.korm.PostgresDriver

expect fun createDatabase(
    host: String,
    port: Int = 5432,
    database: String,
    user: String,
    password: String,
    poolSize: Int = 10,
    config: KormConfig = KormConfig(),
): PostgresDriver
