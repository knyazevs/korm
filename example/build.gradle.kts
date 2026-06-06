@file:Suppress("DEPRECATION") // legacy custom-named native targets (e.g. macosX64("native"))

plugins {
    // No `application` / `io.ktor.plugin`: both apply the `java` plugin, which is
    // incompatible with Kotlin Multiplatform in Kotlin 2.x. ktor is used purely as
    // libraries; running is provided by the KMP JVM/native binaries DSL below.
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.4.0"
}

group = "io.github.knyazevs.korm.korm.example"
version = "1.0"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
    }
}

val ktorVersion = "3.5.0"

kotlin {
    val hostOs = System.getProperty("os.name")
    println("Host os: $hostOs")

    if (!hostOs.contains("windows", ignoreCase = true)) {
        val arch = System.getProperty("os.arch")
        val nativeTarget = when {
            hostOs == "Mac OS X" && arch == "x86_64" -> macosX64("native")
            hostOs == "Mac OS X" && arch == "aarch64" -> macosArm64("native")
            hostOs == "Linux" -> linuxX64("native")
            // Other supported targets are listed here: https://ktor.io/docs/native-server.html#targets
            else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
        }

        nativeTarget.apply {
            binaries {
                executable {
                    entryPoint = "io.github.knyazevs.korm.example.main"
                }
            }
        }
    } else {
        logger.info("Windows is not supported, because no support ktor")
    }

    jvmToolchain(17)

    jvm {
        binaries {
            executable {
                mainClass.set("io.github.knyazevs.korm.example.MainKt")
            }
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-server-core:$ktorVersion")
                implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

                implementation(project(":core"))
                implementation(project(":korm-postgres"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {

                implementation("io.ktor:ktor-server-netty:$ktorVersion")
                implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
                implementation("org.slf4j:slf4j-api:2.0.16")
                implementation("org.slf4j:slf4j-simple:2.0.16")

                // The JVM Postgres driver now lives in :korm-postgres (jvmMain), pulled
                // in transitively; :pgkjvm was dissolved into the shared :korm-jdbc.
            }
        }
        if (!hostOs.contains("windows", ignoreCase = true)) {
            val nativeMain by getting {
                dependencies {
                    implementation("io.ktor:ktor-server-cio:$ktorVersion")
                    implementation(project(":pgkn"))
                }
            }
        } else {
            logger.info("Windows is not supported, because no support ktor")
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
