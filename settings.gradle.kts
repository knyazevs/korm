pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "korm"
include("korm-core", "korm-postgres", "korm-jdbc", "korm-sqlite", "korm-r2dbc", "benchmarks")
include("korm-observe")
include("korm-migrate")
include("korm-ktor", "korm-ktor-di", "korm-ktor-koin")
include("korm-bom")
include(
    "samples:ktor-di",
    "samples:ktor-koin",
    "samples:crud-sqlite",
    "samples:repository",
    "samples:sharding",
    "samples:sqlite-cache",
    "samples:r2dbc",
)
