package io.github.kormium.samples.r2dbc

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * Runs the async-r2dbc sample on Netty. Needs a Postgres on localhost:5432 (postgres/password) —
 * `docker compose -f samples/r2dbc/docker-compose.yml up -d` provides one. The `products` table is
 * created on the first `/create` if you add a migration; this sample expects it to exist (the test
 * creates it). Start with `./gradlew :samples:r2dbc:runJvm`.
 */
fun main() {
    embeddedServer(Netty, port = 8080) { module() }.start(wait = true)
}
