plugins {
    kotlin("jvm")
    id("me.champeau.jmh") version "0.7.2"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    jmhImplementation(project(":korm-postgres"))
    jmhImplementation("org.testcontainers:postgresql:1.20.4")
    jmhImplementation("org.postgresql:postgresql:42.7.4")
    jmhImplementation("com.ionspin.kotlin:bignum:0.3.10")
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
}
