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
    nativeTarget.apply {
        compilations.getByName("main") {
            cinterops {
                register("sqlite3") {
                    defFile(project.file("src/nativeInterop/cinterop/sqlite3.def"))
                    packageName("csqlite")
                }
            }
        }
    }

    jvmToolchain(17)
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Exposes SqliteDialect / SqliteDriver / createSqliteDatabase. The driver
                // returns core's ResultSet and binds core's SqlParameterSource, so :core
                // is part of the public API.
                api(project(":core"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("com.ionspin.kotlin:bignum:0.3.10")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                // The JVM SQLite driver is the shared JDBC driver wired with the
                // sqlite-jdbc URL + SqliteResultSetWrapper.
                implementation(project(":korm-jdbc"))
                implementation("org.xerial:sqlite-jdbc:3.47.1.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            }
        }
    }
}
