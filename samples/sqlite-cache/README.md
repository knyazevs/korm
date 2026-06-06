# Sample: sqlite-cache

A console app showing **two catalogs** working together: **Postgres** as the source of truth and
**SQLite** (in-memory) as a local read-through cache.

`ProductRepository.get(id)` looks in the SQLite cache first; on a miss it reads Postgres and
populates the cache, so the next read is served locally. `PgCatalog` and `CacheCatalog` are
distinct catalogs with their own tables — using one against the other's database is a compile error.

## Run

Run all commands from the repository root.

```sh
# 1. start Postgres (localhost:5432, user/db "postgres", password "password")
docker compose -f samples/sqlite-cache/docker-compose.yml up -d

# 2. run — JVM ...
./gradlew :samples:sqlite-cache:runJvm
# ... or native
./gradlew :samples:sqlite-cache:runDebugExecutableNative
```

It seeds Postgres, then prints `cache MISS … -> populate` / `cache HIT …` as it reads ids back.

## Test

```sh
./gradlew :samples:sqlite-cache:jvmTest
```

Verifies the hit/miss/populate behaviour against a throwaway Postgres via **Testcontainers**
(needs Docker; skipped if unavailable). Does not use `docker-compose.yml`.

## Stop

```sh
docker compose -f samples/sqlite-cache/docker-compose.yml down
```
