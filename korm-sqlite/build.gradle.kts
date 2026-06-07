plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(17)

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // Compose Multiplatform: Android can't use the Kotlin/Native sqlite3 cinterop (it
    // compiles to JVM bytecode), so it gets its own driver in androidMain on top of
    // androidx.sqlite's bundled SQLite. iOS reuses the cinterop driver below.
    android {
        namespace = "io.github.knyazevs.korm.sqlite"
        compileSdk = 36
        minSdk = 24
    }

    // Register the same-named "sqlite3" cinterop on every native target so it commonizes
    // into the shared nativeMain source set. iOS links the libsqlite3 shipped in the SDK.
    listOf(
        linuxX64(), macosX64(), macosArm64(),
        iosX64(), iosArm64(), iosSimulatorArm64(),
    ).forEach { target ->
        target.compilations.getByName("main").cinterops {
            register("sqlite3") {
                defFile(project.file("src/nativeInterop/cinterop/sqlite3.def"))
                packageName("csqlite")
                // Use the vendored sqlite3.h so the cinterop generates identically across
                // targets (incl. cross-compiling linuxX64 from a macOS host, where there is
                // no system sqlite3.h). Linking still uses the platform's libsqlite3.
                compilerOpts("-I${project.file("src/nativeInterop/cinterop")}")
            }
        }
    }
    // mingwX64() // deferred — see the publishing plan

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Exposes SqliteDialect / SqliteDriver / createSqliteDatabase. The driver
                // returns core's ResultSet and binds core's SqlParameterSource, so :korm-core
                // is part of the public API.
                api(project(":korm-core"))
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
        val androidMain by getting {
            dependencies {
                // androidx.sqlite's bundled SQLite ships its own native library for Android,
                // so the driver works on-device without relying on the framework's sqlite.
                implementation("androidx.sqlite:sqlite:2.6.2")
                implementation("androidx.sqlite:sqlite-bundled:2.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
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
