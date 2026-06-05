@file:Suppress("DEPRECATION") // legacy custom-named native target (e.g. macosX64("native"))

plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")
    val nativeTarget = when {
        hostOs == "Mac OS X" && arch == "x86_64" -> macosX64("native")
        hostOs == "Mac OS X" && arch == "aarch64" -> macosArm64("native")
        hostOs == "Linux" -> linuxX64("native")
        hostOs.contains("windows", ignoreCase = true) -> mingwX64 { }
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }
    nativeTarget

    jvmToolchain(17)
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Aggregates the agnostic core with the Postgres drivers and exposes
                // createDatabase(...). PostgresDriver (from :pg) is part of the public
                // return type, so :core and :pg are api dependencies.
                api(project(":core"))
                api(project(":pg"))
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
                implementation(project(":pgkjvm"))
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
        if (!hostOs.contains("windows", ignoreCase = true)) {
            val nativeMain by getting {
                dependencies {
                    implementation(project(":pgkn"))
                }
            }
        }
    }
}
