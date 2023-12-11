package io.github.knyazevs.korm.example

import io.ktor.server.application.*
//import io.ktor.server.plugins.callloging.*


expect fun server()

fun Application.module() {
    //install(CallLogging)

    configureRouting()
    configureSerialization()
}
