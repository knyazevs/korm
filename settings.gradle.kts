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
include("core", "pg", "pgkn", "korm-postgres", "korm-jdbc", "korm-sqlite", "benchmarks")
include("korm-ktor", "korm-ktor-di", "korm-ktor-koin")
include(
    "samples:ktor-di",
    "samples:ktor-koin",
    "samples:crud-sqlite",
    "samples:sharding",
    "samples:sqlite-cache",
)
