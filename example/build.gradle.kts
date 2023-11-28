plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "1.9.20"
    id("io.ktor.plugin") version "2.3.6"
    application
    java
}

group = "s.knyazev.korm.example"
version = "1.0"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
    }
}

val ktorVersion = "2.3.6"

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
                    entryPoint = "s.knyazev.example.main"
                }
            }
        }
    } else {
        logger.info("Windows is not supported, because no support ktor")
    }

    jvm {
        jvmToolchain(17)
        withJava()
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

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")


                implementation("app.softwork:kotlinx-uuid-core:0.0.21")

                implementation(project(":core"))
                implementation(project(":pg"))
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
                implementation("org.slf4j:slf4j-api:1.7.30")
                implementation("org.slf4j:slf4j-simple:1.7.30")

                implementation(project(":pgkjvm"))
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

application {
    mainClass.set("s.knyazev.example.MainKt")
}

tasks.named<JavaExec>("run") {
    dependsOn(tasks.named<Jar>("jvmJar"))
    classpath(tasks.named<Jar>("jvmJar"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
