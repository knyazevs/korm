# kormium-mysql

The MySQL / MariaDB backend for [Kormium](../readme.md). Provides `createDatabase(...)`, the
`MySqlDialect` and the `MySqlDriver`, with two interchangeable transports:

- **JVM** — JDBC via `mysql-connector-j`, pooled with HikariCP.
- **Native** (Linux/macOS) — the MariaDB Connector/C (`libmariadb`, API-compatible with
  `libmysqlclient`) through cinterop, no JVM required.

For non-blocking MySQL on the JVM, see [`createMySqlR2dbcDatabase`](../kormium-r2dbc/README.md) in
`kormium-r2dbc` (over `io.asyncer:r2dbc-mysql`).

## Install

```kotlin
dependencies {
    implementation(platform("io.github.kormium:kormium-bom:<version>"))
    implementation("io.github.kormium:kormium-mysql")
}
```

`kormium-core` is pulled in transitively.

### Native system library

On Kotlin/Native the driver links against the MariaDB Connector/C:

```bash
# macOS
brew install mariadb-connector-c
# Debian / Ubuntu
sudo apt-get install libmariadb-dev
```

## Example

```kotlin
val db: Database<App> = createDatabase(
    host = "localhost",
    port = 3306,
    database = "app",
    user = "root",
    password = "password",
    poolSize = 10,
)

val adults = db.autocommit {
    Users.find { where { Users.age gtEq 18 } }
}
```

## MySQL specifics

- **UUID** is stored as `CHAR(36)` text (MySQL has no UUID type); bind/read go through that form.
  For binary `BINARY(16)` storage, map your own column type with `ColumnType.convert`.
- **JSON** uses MySQL's native `JSON` type; a `JsonElement` binds as its text and reads back via text.
- **Timestamps** round-trip in UTC: the session is pinned to UTC, so an `Instant` stored in a
  `TIMESTAMP` reads back unchanged.
- **Write path.** MySQL has no `RETURNING`, so `insert/insertAll/upsert(returning = true)` run the
  write and then **re-select the row by primary key** — transparent, but it needs a primary key (and
  the PK value set on the entity), otherwise it throws. `upsert` maps to `ON DUPLICATE KEY UPDATE`
  (which keys on any unique/primary index, so the `onConflict` columns are advisory on MySQL), and
  `insertOrIgnore` to a no-op `ON DUPLICATE KEY UPDATE`.
- **No advisory lock.** MySQL `GET_LOCK` is session-scoped (not released at `COMMIT`), so
  `kormium-migrate` runs without an advisory lock on MySQL — as it does on SQLite. Prefer migrating
  from a single instance.
- **Native suspend path** offloads blocking calls to the IO dispatcher (libmysql has no portable
  non-blocking API); the blocking `usePinned` path calls the client directly.

## Platforms

| Platform | Transport |
| --- | --- |
| JVM | JDBC / HikariCP |
| Linux Native | libmariadb |
| macOS Native | libmariadb |
| Windows Native | Use the JVM driver |

Android and iOS do not ship a MySQL backend — use `kormium-sqlite` there.

## Documentation

- [Backends and platform support](../docs/backends.md)
- [Installation](../docs/installation.md)
- [Quick start](../docs/quick-start.md)
