package io.github.knyazevs.korm.samples.ktorkoin

/** The HTTP engine differs per platform (Netty on JVM, CIO on native). */
expect fun server()

fun main() = server()
