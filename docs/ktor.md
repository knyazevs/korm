# Ktor Integration

Korm's Ktor support is split into three artifacts so you can choose how database handles are
resolved.

```kotlin
dependencies {
    implementation("io.github.knyazevs.korm:korm-ktor")
    implementation("io.github.knyazevs.korm:korm-ktor-di")
    implementation("io.github.knyazevs.korm:korm-ktor-koin")
}
```

Use only the artifacts you need.

## DI-Agnostic Helpers

Artifact: `korm-ktor`

You pass the database explicitly:

```kotlin
fun Application.module() {
    install(Korm) {
        manage(database)
    }

    install(StatusPages) {
        exception<KormException> { call, e ->
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
                Users.new(user, returning = true)
            }
            call.respond(HttpStatusCode.Created, saved)
        }
    }
}
```

The `Korm` plugin is optional. Use it when you want Ktor to close database handles on
application stop. Skip it if your DI container owns lifecycle.

## Ktor Built-In DI

Artifact: `korm-ktor-di`

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

Artifact: `korm-ktor-koin`

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
    Users.new(user)
}
```

## Call Styles

The DI artifacts expose equivalent styles:

```kotlin
call.transaction<App, _> { ... }
call.transaction(App) { ... }
call.korm<App>().transaction { ... }
```

All helpers delegate to `suspendTransaction` and `suspendAutocommit`, so the same routes can
use the offloaded blocking drivers or `korm-r2dbc`.
