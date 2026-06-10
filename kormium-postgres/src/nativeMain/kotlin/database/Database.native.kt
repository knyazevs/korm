package io.github.kormium.database

import io.github.kormium.KormiumConfig

actual fun createDatabase(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    poolSize: Int,
    config: KormiumConfig,
) = io.github.moreirasantos.pgkn.FPostgresDriver(host, port, database, user, password, poolSize, config)
