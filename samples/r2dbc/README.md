# r2dbc sample

A Ktor CRUD service backed by the **async, non-blocking** Postgres driver (`kormium-r2dbc`,
r2dbc-postgresql) — and wired through the **same** `kormium-ktor-di` helpers as the blocking
[ktor-di](../ktor-di) sample.

The point of this sample is the diff against `ktor-di`: it's a single line.

```kotlin
// ktor-di (blocking JDBC, offloaded to a virtual thread):
provide<SuspendDatabase<AppCatalog>> { createDatabase(...) }

// r2dbc (truly non-blocking, with pipelining):
provide<SuspendDatabase<AppCatalog>> { createR2dbcDatabase(...) }
```

The routes and the `call.transaction` / `call.autocommit` helpers are identical — the suspend
API is backend-transparent. JVM-only, since r2dbc has no Kotlin/Native target.

## Run

```sh
docker compose -f samples/r2dbc/docker-compose.yml up -d   # start Postgres
./gradlew :samples:r2dbc:runJvm                            # run on http://localhost:8080
docker compose -f samples/r2dbc/docker-compose.yml down    # stop Postgres
```

Endpoints: `GET /`, `PUT /create`, `POST /update?id=…`, `DELETE /delete?id=…`
(bodies are `{"price":…,"payload":{…}}`).

## Test

`./gradlew :samples:r2dbc:jvmTest` runs an end-to-end CRUD test against a real Postgres via
Testcontainers (skips gracefully if Docker isn't available).
