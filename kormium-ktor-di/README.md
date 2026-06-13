# korm-ktor-di

[Ktor](https://ktor.io) integration for [Kormium](../readme.md) that resolves the database from
**Ktor's built-in DI** container — no explicit `db` argument in your routes.

Builds on [`korm-ktor`](../kormium-ktor/README.md), adding reified `ApplicationCall` helpers that
look up `SuspendDatabase<G>` by its parameterized type. Ktor DI keys by full type, so
`SuspendDatabase<App>` and `SuspendDatabase<Cache>` are distinct dependencies — catalog safety
carries through to resolution.

## Install

```kotlin
dependencies {
    implementation(platform("io.github.kormium:korm-bom:<version>"))
    implementation("io.github.kormium:korm-ktor-di")
}
```

`korm-ktor` (and `korm-core`) are pulled in transitively.

## Example

```kotlin
fun Application.module() {
    dependencies {
        provide<SuspendDatabase<App>> {
            createDatabase(host = "localhost", database = "postgres", user = "postgres", password = "password")
        }
    }

    routing {
        get("/users") {
            val users = call.autocommit<App, _> { Users.all() }
            call.respond(users.map { it.name })
        }
    }
}
```

## Call styles

```kotlin
call.transaction<App, _> { ... }   // catalog as a type argument
call.transaction(App) { ... }      // catalog as a value
call.korm<App>().transaction { ... } // resolve a handle, then use it
```

All delegate to `suspendTransaction` / `suspendAutocommit`, so the same routes work over the
offloaded blocking drivers or `korm-r2dbc`.

## Documentation

- [Ktor integration](../docs/ktor.md)
- See also [`samples/ktor-di`](../samples/ktor-di).
