plugins {
    `java-platform`
}

// Bill of Materials: pins the versions of every published korm artifact so consumers can
// depend on `platform("io.github.kormium:korm-bom:<v>")` and omit versions elsewhere.
dependencies {
    constraints {
        listOf(
            "korm-core",
            "korm-postgres",
            "korm-jdbc",
            "korm-sqlite",
            "korm-ktor",
            "korm-ktor-di",
            "korm-ktor-koin",
        ).forEach { api("${project.group}:$it:${project.version}") }
    }
}
