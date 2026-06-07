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
    jvmToolchain(17)

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    linuxX64()
    macosX64()
    macosArm64()
    // mingwX64() // deferred — see the publishing plan

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // DI-agnostic ktor integration: explicit-db transaction helpers + the
                // KormException -> HttpStatusCode mapper + an optional close-on-stop plugin.
                // Database/Scope/Catalog/exceptions are part of the public API, so :korm-core is api;
                // ApplicationCall appears in the public extension signatures, so ktor-server-core is api.
                api(project(":korm-core"))
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
