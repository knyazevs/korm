package io.github.knyazevs.korm.database

import io.github.knyazevs.korm.KormConfig

actual fun createDatabase(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    poolSize: Int,
    config: KormConfig,
) = io.github.moreirasantos.pgkn.FPostgresDriver(host, port, database, user, password, poolSize, config)
