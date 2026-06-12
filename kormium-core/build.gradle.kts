plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.4.0"
    id("com.android.kotlin.multiplatform.library")
}

repositories {
    google()
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
    }
}

kotlin {
    jvmToolchain(21)

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // Compose Multiplatform targets. The Android target is configured via the AGP KMP
    // library plugin's androidLibrary DSL (AGP 9 dropped com.android.library + androidTarget()).
    android {
        namespace = "io.github.kormium"
        compileSdk = 36
        minSdk = 24
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Public suspend API (suspendTransaction/suspendAutocommit) is coroutine-based.
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                // BigDecimal
                implementation("com.ionspin.kotlin:bignum:0.3.10")

                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation("io.github.oshai:kotlin-logging:7.0.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                // kotlin-logging delegates to SLF4J on the JVM; core needs the API on the
                // runtime classpath (previously pulled in transitively via the drivers).
                implementation("org.slf4j:slf4j-api:2.0.16")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                // Android is JVM-flavoured: kotlin-logging delegates to SLF4J here too.
                implementation("org.slf4j:slf4j-api:2.0.16")
            }
        }
    }
}
