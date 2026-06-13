# korm-ktor-koin

[Ktor](https://ktor.io) integration for [Kormium](../readme.md) that resolves the database from
**[Koin](https://insert-koin.io)** — no explicit `db` argument in your routes.

Builds on [`korm-ktor`](../kormium-ktor/README.md), adding reified `ApplicationCall` helpers that
resolve `SuspendDatabase<G>` from Koin.

> Koin keys by `KClass`, so the generic catalog tag is erased. If you use more than one
> catalog, register databases with named qualifiers and resolve with the matching qualifier.

## Install

```kotlin
dependencies {
    implementation(platform("io.github.kormium:korm-bom:<version>"))
    implementation("io.github.kormium:korm-ktor-koin")
}
```

`korm-ktor` (and `korm-core`) are pulled in transitively.

## Example

```kotlin
fun Application.module() {
    install(Koin) {
        modules(module {
            single<SuspendDatabase<App>> {
                createDatabase(host = "localhost", database = "postgres", user = "postgres", password = "password")
            }
        })
    }

    routing {
        get("/users") {
            val users = call.autocommit<App, _> { Users.all() }
            call.respond(users.map { it.name })
        }
    }
}
```

Multiple catalogs, with qualifiers:

```kotlin
single<SuspendDatabase<App>>(named("app")) { createDatabase(/* ... */) }

call.transaction(App, named("app")) { Users.insert(user) }
```

## Documentation

- [Ktor integration](../docs/ktor.md)
- See also [`samples/ktor-koin`](../samples/ktor-koin).
