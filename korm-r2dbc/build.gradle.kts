plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                // The r2dbc driver returns core's ResultSet and implements core's
                // SuspendDatabase, so :korm-core is part of the public API.
                api(project(":korm-core"))
                // PostgresDialect (the ::uuid cast) is reused verbatim for SQL rendering.
                api(project(":korm-postgres"))
                implementation("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")
                implementation("io.r2dbc:r2dbc-pool:1.0.2.RELEASE")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
                implementation("org.testcontainers:postgresql:1.20.4")
            }
        }
    }
}
