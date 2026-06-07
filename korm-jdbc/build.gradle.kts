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
                // The generic JDBC driver returns core's ResultSet and binds core's
                // SqlParameterSource, so :korm-core is part of the public API.
                api(project(":korm-core"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation("com.zaxxer:HikariCP:6.2.1")
            }
        }
    }
}
