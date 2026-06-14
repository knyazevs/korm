# Backends

The core DSL is backend-agnostic. Backends provide a `Dialect`, a `TypeMapper` and a driver
that executes parameterized SQL.

## PostgreSQL

Artifact:

```kotlin
implementation("io.github.kormium:kormium-postgres")
```

Factory:

```kotlin
val db: Database<App> = createDatabase(
    host = "localhost",
    port = 5432,
    database = "postgres",
    user = "postgres",
    password = "password",
    poolSize = 10,
)
```

Implementations:

- JVM: PostgreSQL JDBC driver plus HikariCP through `kormium-jdbc`.
- Kotlin/Native: libpq cinterop.

Native builds need `libpq` headers/libraries on the build machine. See
[Installation](installation.md#postgresql-native).

PostgreSQL notes:

- On JVM, parameters are bound as properly-typed JDBC objects (uuid, numeric,
  timestamptz, jsonb, ...), so server-prepared statements execute in a single protocol
  round-trip. **Raw SQL** that binds a `String` to a non-text column must cast it
  explicitly — `WHERE id = :id::uuid` — or pass a typed value; DSL queries need nothing.
- On Kotlin/Native, libpq sends parameters as text and the dialect adds the needed
  `::type` casts.

## MySQL / MariaDB

Artifact:

```kotlin
implementation("io.github.kormium:kormium-mysql")
```

Factory:

```kotlin
val db: Database<App> = createDatabase(
    host = "localhost",
    port = 3306,
    database = "app",
    user = "root",
    password = "password",
    poolSize = 10,
)
```

Implementations:

- JVM: `mysql-connector-j` plus HikariCP through `kormium-jdbc`.
- Kotlin/Native (Linux/macOS): MariaDB Connector/C (`libmariadb`) cinterop using prepared
  statements. Windows is served by the JVM driver.
- Async on JVM: `createMySqlR2dbcDatabase(...)` in `kormium-r2dbc` over `io.asyncer:r2dbc-mysql`.

Native builds need `libmariadb` headers/libraries on the build machine
(`brew install mariadb-connector-c` / `apt-get install libmariadb-dev`).

MySQL notes:

- UUID is stored as `CHAR(36)`; JSON uses the native `JSON` type. The session is pinned to UTC so
  `Instant`/`TIMESTAMP` round-trips unchanged.
- Integrity violations are mapped to typed exceptions by vendor code (1062 unique, 1452 foreign
  key, 1048 NOT NULL, 3819 check) since MySQL reports them all under SQLSTATE 23000.
- No transaction-scoped advisory lock (MySQL `GET_LOCK` is session-scoped), so `kormium-migrate`
  runs without one — prefer migrating from a single instance.

## SQLite

Artifact:

```kotlin
implementation("io.github.kormium:kormium-sqlite")
```

Factory:

```kotlin
val memory: Database<App> = createSqliteDatabase()
val file: Database<App> = createSqliteDatabase("app.db")
```

Implementations:

- JVM: sqlite-jdbc.
- Kotlin/Native: sqlite3 cinterop.
- Android: AndroidX SQLite with bundled SQLite.

SQLite notes:

- `poolSize` defaults to `1` because SQLite allows one writer.
- File databases are opened in WAL mode.
- Foreign keys are enabled with `PRAGMA foreign_keys=ON`.
- `UUID`, `BigDecimal`, `Json` and temporal values are stored as text and parsed back.

## r2dbc PostgreSQL

Artifact:

```kotlin
implementation("io.github.kormium:kormium-r2dbc")
```

Factory:

```kotlin
val db: SuspendDatabase<App> = createR2dbcDatabase(
    host = "localhost",
    port = 5432,
    database = "postgres",
    user = "postgres",
    password = "password",
    poolSize = 10,
)
```

`kormium-r2dbc` is JVM-only and implements the suspend API only. It is the backend to choose
when you need true non-blocking PostgreSQL I/O.

## Type Mapping

Kormium's common column types map through backend-specific SQL types:

| Kormium type | PostgreSQL | SQLite |
| --- | --- | --- |
| `UUID` | `UUID` | `TEXT` |
| `Text` | `TEXT` | `TEXT` |
| `Boolean` | `BOOLEAN` | `INTEGER` |
| `Short`, `Int`, `Long` | integer types | `INTEGER` |
| `Float`, `Double` | floating types | `REAL` |
| `BigDecimal` | numeric | `TEXT` |
| `Instant` and local date/time types | temporal/text depending on backend mapper | `TEXT` |
| `Json` | JSON/JSONB-compatible binding | `TEXT` |

The public API presents Kotlin values consistently even when storage differs.

## Platform Support

| Platform | PostgreSQL | SQLite |
| --- | --- | --- |
| JVM | JDBC/HikariCP and r2dbc | sqlite-jdbc |
| Linux Native | libpq | sqlite3 |
| macOS Native | libpq | sqlite3 |
| Android | Not shipped | AndroidX SQLite |
| iOS | Not shipped | sqlite3 |
| Windows Native | libpq (experimental) | sqlite3 (experimental) |
| Wasm | Research | Planned |

See [Installation](installation.md#platform-matrix) for module-level details.
