plugins {
    kotlin("multiplatform")
    id("maven-publish")
}

group = "s.knyazev"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/knyazevs/korm")
            credentials {
                username = System.getenv("USERNAME")
                password = System.getenv("TOKEN")
            }
        }
    }
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
    }

    val hostOs = System.getProperty("os.name")
    println("Host os: $hostOs")

    val arch = System.getProperty("os.arch")
    val nativeTarget = when {
        hostOs == "Mac OS X" && arch == "x86_64" -> macosX64 { }
        hostOs == "Mac OS X" && arch == "aarch64" -> macosArm64 { }
        hostOs == "Linux" -> linuxX64 { }
        //linuxArm64 { config() }
        hostOs.contains("windows", ignoreCase = true) -> mingwX64 { }
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }
    nativeTarget

    // android, ios, watchos, tvos, jvm, js will never(?) be supported

    jvm {
        jvmToolchain(17)
    }


    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                implementation("io.github.oshai:kotlin-logging:5.1.0")
            }
        }
        val commonTest by getting
    }
}
