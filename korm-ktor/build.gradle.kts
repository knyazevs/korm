@file:Suppress("DEPRECATION") // legacy custom-named native target (e.g. macosX64("native"))

plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
    }
}

val ktorVersion = "3.5.0"

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
                // DI-agnostic ktor integration: explicit-db transaction helpers + the
                // KormException -> HttpStatusCode mapper + an optional close-on-stop plugin.
                // Database/Scope/Catalog/exceptions are part of the public API, so :core is api;
                // ApplicationCall appears in the public extension signatures, so ktor-server-core is api.
                api(project(":core"))
                api("io.ktor:ktor-server-core:$ktorVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
