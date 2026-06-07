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
            // Long-running stability/soak tests are opt-in: run with -Pstability.
            useJUnitPlatform {
                if (!project.hasProperty("stability")) excludeTags("stability")
            }
        }
    }

    // The native Postgres driver talks to libpq via cinterop (formerly the :pgkn module,
    // now folded into this module's nativeMain). Register the same-named "libpq" cinterop
    // on every native target so it commonizes into the shared nativeMain source set.
    listOf(linuxX64(), macosX64(), macosArm64()).forEach { target ->
        target.compilations.getByName("main").cinterops {
            register("libpq") {
                defFile(project.file("src/nativeInterop/cinterop/libpq.def"))
            }
        }
    }
    // mingwX64() // deferred — see the publishing plan

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Exposes createDatabase(...) plus the Postgres Dialect + PostgresDriver
                // interface (formerly the :pg module). PostgresDriver is part of the public
                // return type, so :korm-core is an api dependency.
                api(project(":korm-core"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                // PostgresDialect casts a JsonElement bind to ::jsonb (needed so the truly-typed
                // r2dbc driver doesn't send it as text; harmless for the text-based JDBC/libpq paths).
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("io.github.oshai:kotlin-logging:7.0.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("com.ionspin.kotlin:bignum:0.3.10")
            }
        }
        val jvmMain by getting {
            dependencies {
                // The JVM Postgres driver is the shared JDBC driver wired with the
                // pgjdbc URL + PgResultSetWrapper (which uses kotlinx-datetime).
                implementation(project(":korm-jdbc"))
                implementation("org.postgresql:postgresql:42.7.4")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            }
        }
        val jvmTest by getting {
            dependencies {
                // End-to-end tests of the JVM driver against a real Postgres in Docker.
                implementation("org.testcontainers:postgresql:1.20.4")
                implementation("org.postgresql:postgresql:42.7.4")
                // For the all-column-types round-trip test (Instant / Json columns).
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            }
        }
        val nativeMain by getting {
            dependencies {
                // The native libpq driver (formerly :pgkn).
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("io.github.oshai:kotlin-logging:7.0.3")
            }
        }
    }
}
