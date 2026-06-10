plugins {
    kotlin("jvm")
    id("me.champeau.jmh") version "0.7.2"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    jmhImplementation(project(":kormium-postgres"))
    jmhImplementation("org.testcontainers:postgresql:1.20.4")
    jmhImplementation("org.postgresql:postgresql:42.7.4")
    jmhImplementation("com.ionspin.kotlin:bignum:0.3.10")
    jmhImplementation("com.zaxxer:HikariCP:6.2.1")

    // For the cross-ORM comparison benchmark.
    jmhImplementation("org.hibernate.orm:hibernate-core:6.4.4.Final")
    jmhImplementation("org.hibernate.orm:hibernate-hikaricp:6.4.4.Final")
    jmhImplementation("org.jetbrains.exposed:exposed-core:0.50.1")
    jmhImplementation("org.jetbrains.exposed:exposed-jdbc:0.50.1")
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
    // Run a subset with, e.g., `-Pjmh ...` or by setting includes here, e.g.
    // includes.add("ComparisonBenchmark")
}
