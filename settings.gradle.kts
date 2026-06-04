pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "korm"
// NOTE: "example" temporarily excluded during the Kotlin 2.4 / Gradle 9 migration
// (its ktor 2.3.6 stack needs a separate bump to ktor 3.x).
include("core", "pg", "pgkjvm", "pgkn")
