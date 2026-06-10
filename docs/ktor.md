# Ktor Integration

Kormium's Ktor support is split into three artifacts so you can choose how database handles are
resolved.

```kotlin
dependencies {
    implementation("io.github.kormium:kormium-ktor")
    implementation("io.github.kormium:kormium-ktor-di")
    implementation("io.github.kormium:kormium-ktor-koin")
}
```

Use only the artifacts you need.

## DI-Agnostic Helpers

Artifact: `kormium-ktor`

You pass the database explicitly:

```kotlin
fun Application.module() {
    install(Kormium) {
        manage(database)
    }

    install(StatusPages) {
        exception<KormiumException> { call, e ->
            call.respond(e.httpStatusCode(), e.message ?: "database error")
        }
    }

    routing {
        get("/users") {
            val users = call.autocommit(database) {
                Users.all()
            }
            call.respond(users.map { it.name })
        }

        post("/users") {
            val saved = call.transaction(database) {
                Users.insert(user, returning = true)
            }
            call.respond(HttpStatusCode.Created, saved)
        }
    }
}
```

The `Kormium` plugin is optional. Use it when you want Ktor to close database handles on
application stop. Skip it if your DI container owns lifecycle.

## Ktor Built-In DI

Artifact: `kormium-ktor-di`

Register `SuspendDatabase<G>` by its parameterized type:

```kotlin
fun Application.module() {
    dependencies {
        provide<SuspendDatabase<App>> {
            createDatabase(
                host = "localhost",
                database = "postgres",
                user = "postgres",
                password = "password",
            )
        }
    }

    routing {
        get("/users") {
            val users = call.autocommit<App, _> {
                Users.all()
            }
            call.respond(users.map { it.name })
        }
    }
}
```

Ktor DI keys by full type, so `SuspendDatabase<App>` and `SuspendDatabase<Cache>` are
different dependencies.

## Koin

Artifact: `kormium-ktor-koin`

```kotlin
fun Application.module() {
    install(Koin) {
        modules(
            module {
                single<SuspendDatabase<App>> {
                    createDatabase(
                        host = "localhost",
                        database = "postgres",
                        user = "postgres",
                        password = "password",
                    )
                }
            }
        )
    }

    routing {
        get("/users") {
            val users = call.autocommit<App, _> {
                Users.all()
            }
            call.respond(users.map { it.name })
        }
    }
}
```

Koin keys by `KClass`, so generic catalog tags are erased. If you use more than one catalog,
register databases with named qualifiers and resolve with the matching qualifier:

```kotlin
single<SuspendDatabase<App>>(named("app")) { createDatabase(/* ... */) }

call.transaction(App, named("app")) {
    Users.insert(user)
}
```

## Call Styles

The DI artifacts expose equivalent styles:

```kotlin
call.transaction<App, _> { ... }
call.transaction(App) { ... }
call.kormium<App>().transaction { ... }
```

All helpers delegate to `suspendTransaction` and `suspendAutocommit`, so the same routes can
use the offloaded blocking drivers or `kormium-r2dbc`.
