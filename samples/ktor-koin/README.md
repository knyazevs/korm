# Sample: ktor-koin

The same Ktor web CRUD over **Postgres** as [ktor-di](../ktor-di), but the database is wired
through **Koin** instead of Ktor's built-in DI — i.e. the `kormium-ktor-koin` artifact:

- `install(Koin) { modules(module { single<Database<AppCatalog>> { createDatabase(...) } }) }`,
  with `onClose { it?.close() }` to close the pool when Koin shuts down with the application
  (Koin does not auto-close like Ktor DI).
- routes use `call.transaction<AppCatalog, _> { ... }` / `call.autocommit<AppCatalog, _> { ... }`.

Note: Koin keys by `KClass`, so generics are erased. With a single catalog this is fine; for
multiple catalogs register and resolve with a `named(...)` qualifier.

## Run

Run all commands from the repository root.

```sh
# 1. start Postgres (localhost:5432, user/db "postgres", password "password")
docker compose -f samples/ktor-koin/docker-compose.yml up -d

# 2. run the app — JVM (Netty) ...
./gradlew :samples:ktor-koin:runJvm
# ... or native (CIO)
./gradlew :samples:ktor-koin:runDebugExecutableNative
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
./gradlew :samples:ktor-koin:jvmTest
```

Runs the app against a throwaway Postgres via **Testcontainers** (needs Docker; skipped if
unavailable). Does not use `docker-compose.yml`.

## Stop

```sh
docker compose -f samples/ktor-koin/docker-compose.yml down
```
