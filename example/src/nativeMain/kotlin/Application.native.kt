package com.github.knyazevs.korm.example

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*


actual fun server() {
    embeddedServer(CIO, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}
