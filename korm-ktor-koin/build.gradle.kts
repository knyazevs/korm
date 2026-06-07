plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
    }
}

val koinVersion = "4.1.0"

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
                // Reified transaction helpers that resolve Database<G> from Koin.
                api(project(":korm-ktor"))
                api("io.insert-koin:koin-ktor:$koinVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
