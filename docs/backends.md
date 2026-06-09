# Backends

The core DSL is backend-agnostic. Backends provide a `Dialect`, a `TypeMapper` and a driver
that executes parameterized SQL.

## PostgreSQL

Artifact:

```kotlin
implementation("io.github.kormium:korm-postgres")
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

- JVM: PostgreSQL JDBC driver plus HikariCP through `korm-jdbc`.
- Kotlin/Native: libpq cinterop.

Native builds need `libpq` headers/libraries on the build machine. See
[Installation](installation.md#postgresql-native).

## SQLite

Artifact:

```kotlin
implementation("io.github.kormium:korm-sqlite")
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
implementation("io.github.kormium:korm-r2dbc")
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

`korm-r2dbc` is JVM-only and implements the suspend API only. It is the backend to choose
when you need true non-blocking PostgreSQL I/O.

## Type Mapping

Korm's common column types map through backend-specific SQL types:

| Korm type | PostgreSQL | SQLite |
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
| Windows Native | Planned | Planned |
| Wasm | Research | Planned |

See [Installation](installation.md#platform-matrix) for module-level details.
