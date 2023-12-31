package io.github.knyazevs.korm.example

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

actual fun server() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}
