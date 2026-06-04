package io.github.knyazevs.korm.database

actual fun createDatabase(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    poolSize: Int,
) = io.github.moreirasantos.pgkn.FPostgresDriver(host, port, database, user, password, poolSize)
