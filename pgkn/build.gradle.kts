import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
}

group = "s.knyazev"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
    }

    fun KotlinNativeTarget.config() {
        compilations.named("main") {
            cinterops {
                register("libpq") {
                    defFile(project.file("src/nativeInterop/cinterop/libpq.def"))
                }
            }
        }
    }

    val hostOs = System.getProperty("os.name")
    println("Host os: $hostOs")

    val arch = System.getProperty("os.arch")
    val nativeTarget = when {
        hostOs == "Mac OS X" && arch == "x86_64" -> macosX64 { config() }
        hostOs == "Mac OS X" && arch == "aarch64" -> macosArm64 { config() }
        hostOs == "Linux" -> linuxX64 { config() }
        //linuxArm64 { config() }
        hostOs.contains("windows", ignoreCase = true) -> mingwX64 { config() }
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }
    nativeTarget

    // android, ios, watchos, tvos, jvm, js will never(?) be supported

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                implementation("io.github.oshai:kotlin-logging:5.1.0")
                api(project(":pg"))
            }
        }
        val commonTest by getting
    }
}
