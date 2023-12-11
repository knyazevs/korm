package com.github.knyazevs.korm.database

actual fun createDatabase(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
) = com.github.knyazevs.korm.FPostgresDriver(host, port, database, user, password)
