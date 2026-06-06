# Sample: ktor-di

Ktor web CRUD over **Postgres**, with the database resolved from Ktor's built-in dependency
injection — i.e. the `korm-ktor-di` artifact:

- `dependencies { provide<Database<AppCatalog>> { createDatabase(...) } }` — Ktor DI keys by the
  full parameterized type (so catalogs don't collide) and auto-closes the pool on shutdown
  (a `Database` is `AutoCloseable`), so no lifecycle plugin is needed.
- routes use `call.transaction<AppCatalog, _> { ... }` / `call.autocommit<AppCatalog, _> { ... }`
  (catalog as a type argument, `_` infers the result).
- `KormException` is mapped to an HTTP status via `httpStatusCode()` in a `StatusPages` handler.

## Run

Run all commands from the repository root.

```sh
# 1. start Postgres (localhost:5432, user/db "postgres", password "password")
docker compose -f samples/ktor-di/docker-compose.yml up -d

# 2. run the app — JVM (Netty) ...
./gradlew :samples:ktor-di:runJvm
# ... or native (CIO)
./gradlew :samples:ktor-di:runDebugExecutableNative
```

The server listens on http://localhost:8080.

## Endpoints

```sh
# list all products
curl localhost:8080/

# create one (responds with the stored row, including its generated id)
curl -X PUT localhost:8080/create \
  -H 'Content-Type: application/json' \
  -d '{"price": 42, "payload": {"color": "red"}}'

# update by id (id is a query parameter)
curl -X POST "localhost:8080/update?id=<uuid>" \
  -H 'Content-Type: application/json' \
  -d '{"price": 99, "payload": {"color": "blue"}}'

# delete by id
curl -X DELETE "localhost:8080/delete?id=<uuid>"
```

## Test

```sh
./gradlew :samples:ktor-di:jvmTest
```

The test runs the whole app against a throwaway Postgres started with **Testcontainers** (needs a
running Docker; it's skipped if Docker is unavailable). It does not use `docker-compose.yml`.

## Stop

```sh
docker compose -f samples/ktor-di/docker-compose.yml down
```
