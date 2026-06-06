# Korm

Type-safe ORM/SQL DSL for Kotlin Multiplatform.

Korm lets you build JVM and Kotlin/Native services that talk to PostgreSQL without JDBC,
while keeping an Exposed-like Kotlin API: tables, entities, transactions, migrations,
joins, aggregations and typed predicates.

## Why Korm?

- Kotlin Multiplatform: shared core for JVM and Native
- PostgreSQL on JVM and Native: JDBC/HikariCP on JVM, libpq on Native
- Exposed-inspired type-safe DSL
- Compile-time catalog safety: tables cannot be used with the wrong database
- Transactions, savepoints, migrations and typed SQLSTATE exceptions
- JMH benchmarks against Exposed and Hibernate

| Platform | PostgreSQL | Sqlite |
|---|---|---|
| JVM | ✅ JDBC/HikariCP | planned |
| macOS Native | ✅ libpq | planned |
| Linux Native | ✅ libpq | planned |
| Windows Native | ✅ libpq | planned |
| Android | planned | planned |
| iOS | planned | planned | planned |
| Wasm | research/planned | planned |

Status: pre-1.0, API may change.
Good for experimentation, benchmarks, prototypes and feedback.
Not recommended yet as the only persistence layer for critical production systems.

## Modules

| Module | What |
| --- | --- |
| `core` | Backend-agnostic API: `Table`, `Column`, `Entity`, `Query`, `Catalog`, `Database<G>`, scopes/transactions. No backend dependency. |
| `korm-postgres` | The PostgreSQL binding: `createDatabase(...)` plus the JVM (JDBC/HikariCP) and Native (libpq) drivers. |
| `korm-sqlite` | The SQLite binding: `createSqliteDatabase(...)` plus the JVM (sqlite-jdbc) and Native (sqlite3 cinterop) drivers. |
| `korm-jdbc` | Shared JVM JDBC plumbing (HikariCP pool, named-parameter binding) used by the JVM drivers. |

Depend on the backend you want — each brings `core` with it:

```kotlin
dependencies {
    implementation("io.github.knyazevs.korm:korm-postgres:<version>")
    // or
    implementation("io.github.knyazevs.korm:korm-sqlite:<version>")
}
```

### Native requirement: libpq

The Kotlin/Native driver links against **libpq**. Install the PostgreSQL client
libraries on the build/host machine:

- macOS: `brew install libpq`
- Debian/Ubuntu: `apt-get install libpq-dev`
- Windows: `choco install postgresql`

## Quick start

### 1. Declare a catalog, a table and its entity

A `Catalog` is a marker type for a logical database/schema. A `Table` is tagged with
the catalog it belongs to and carries no connection.

```kotlin
import io.github.knyazevs.korm.*

object Main : Catalog

object Users : Table<Main, User>(Meta("users"), ::User) {
    val id by Column.UUID(primaryKey = true)
    val name by Column.Text()
    val age by Column.Int()
    val note by Column.Text(nullable = true)

    init { id; name; age; note }   // register the columns
}

class User(override var fields: MutableMap<String, Any?> = mutableMapOf()) : Entity(fields) {
    var id by Users.id
    var name by Users.name
    var age by Users.age
    var note by Users.note
}
```

Available column types: `UUID`, `Text`, `Boolean`, `Short`, `Int`, `Long`, `Float`,
`Double`, `BigDecimal`, `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`, `Json`.
Each takes optional `nullable` and `primaryKey` flags, e.g.
`Column.UUID(primaryKey = true)`, `Column.Text(nullable = true)`. `findById` uses the
primary key (or the column named `id` if none is marked); mark several columns for a
composite key.

### 2. Connect

`createDatabase` returns a connection pool. Assign it to a `Database<Main>` to pin the
catalog tag:

```kotlin
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.database.createDatabase

val db: Database<Main> = createDatabase(
    host = "localhost",
    port = 5432,
    database = "postgres",
    user = "postgres",
    password = "password",
    poolSize = 10,
)
```

### 3. Run operations inside a scope

Table operations live on the scope opened by `transaction { }` (BEGIN/COMMIT/ROLLBACK)
or `autocommit { }` (a pinned connection, no surrounding transaction — the cheap path
for reads):

```kotlin
import io.github.knyazevs.korm.*

// write — committed on success, rolled back if the block throws
db.transaction {
    Users.new(User().apply {
        id = Uuid.random()
        name = "Ada"
        age = 36
        note = null
    })
}

// read
val ada: User? = db.autocommit { Users.findById(someId) }
val all: List<User> = db.autocommit { Users.all() }

// query
val adults: List<User> = db.autocommit {
    Users.find(
        Query(
            whereExpression = Users.age gtEq 18,
            orderBy = mapOf(Users.age to AscDescOrder.DESC),
            limit = 50u,
        )
    )
}

// update / delete
db.transaction {
    Users.update(Query(Users.id eq someId.toString()), User().apply { age = 37 })
    Users.deleteWhere(Query(Users.name eq "Ada"))
}
```

`new` does a plain `INSERT` and returns the entity you passed — the fast path. Pass
`returning = true` to fetch the stored row back via SQL `RETURNING` (e.g. to read
database-generated columns). Insert many rows in one statement with the list overload,
and count rows with `count`:

```kotlin
db.transaction { Users.new(user) }                                      // plain INSERT (fast)
val saved: User? = db.transaction { Users.new(user, returning = true) } // fetch the DB row
val savedAll: List<User> = db.transaction { Users.new(listOf(user1, user2)) }
val total: Long = db.autocommit { Users.count() }
val adults: Long = db.autocommit { Users.count(Query(Users.age gtEq 18)) }
```

Values are always sent as bind parameters, never inlined — so untrusted input can't
inject SQL. Predicate operators are **typed** (`Users.age gtEq 18`, not a string):
`eq`, `neq`, `less`, `lessEq`, `gt`, `gtEq`, plus `inList`, `like`, `isNull()`,
`isNotNull()` and `not(...)`, combined with `and` / `or`:

```kotlin
Users.find(Query(
    (Users.age gtEq 18) and (Users.name like "A%") and Users.note.isNotNull()
))
Users.find(Query(Users.id inList listOf(id1, id2)))
```

## Joins

Join tables and read the result as `ResultRow`s (indexed by column), map each row to
your own type, or — for two tables — get both entities back as a `Pair`:

```kotlin
// raw rows: index by the column you selected
val rows = db.autocommit {
    (Users innerJoin Orders on (Users.id eq Orders.userId))
        .where(Users.age gtEq 18)
        .select()
}
rows.forEach { row -> println("${row[Users.name]} spent ${row[Orders.total]}") }

// projection into your own type
data class UserSpend(val name: String, val total: BigDecimal)
val spend = db.autocommit {
    (Users innerJoin Orders on (Users.id eq Orders.userId))
        .select(Users.name, Orders.total) { row -> UserSpend(row[Users.name], row[Orders.total]) }
}

// two tables as entity pairs
val pairs: List<Pair<User, Order>> = db.autocommit {
    (Users innerJoin Orders on (Users.id eq Orders.userId)).find()
}
```

`leftJoin` is also available; with it use `row.getOrNull(col)` for columns from the right
side. Joining a third table keeps the `select(...)` forms (the `Pair`/`find()` form is
two-table only). Columns are automatically qualified by table inside a join.

## Aggregations

Group rows and read aggregates. Hold each aggregate in a `val` — `row[...]` looks it up by
identity, so a fresh `count()` would be a different key:

```kotlin
val orders = count()
val total = Orders.total.sum()

db.autocommit {
    (Users innerJoin Orders on (Users.id eq Orders.userId))
        .groupBy(Users.id)
        .having(total gt Value(BigDecimal.fromInt(100)))
        .select(Users.name, orders, total)
}.forEach { row -> println("${row[Users.name]}: ${row[orders]} orders, ${row[total]}") }

// single table:
val byAge = db.autocommit { Users.query().groupBy(Users.age).distinct().select(Users.age) }
```

Aggregates: `count()`, `column.count()`, `column.min()`, `column.max()`, `column.sum()`
(keep the column's type) and `column.avg()` (Double). Plus `groupBy(...)`, `having(...)`
and `distinct()`. Start a single-table query with `Table.query()`.

## Creating tables

Korm generates `CREATE TABLE` from a table's columns (SQL types come from the dialect):

```kotlin
db.transaction {
    Users.createTable()      // CREATE TABLE IF NOT EXISTS "public"."users" (...)
    // Users.dropTable()     // DROP TABLE IF EXISTS ...
}
```

This covers columns, `NOT NULL` and the `PRIMARY KEY` (from the columns marked
`primaryKey = true`). Indexes and other constraints are not generated yet — add them with
raw SQL (`execute("...")`) for now.

## Migrations

Apply ordered, idempotent schema changes. Each `Migration` has a stable `id`; `migrate`
runs the ones not yet recorded in the `korm_migrations` table, in order, each in its own
transaction — so it is safe to call on every startup:

```kotlin
db.migrate(listOf(
    Migration("001-create-users") {
        Users.createTable()
    },
    Migration("002-add-index") {
        executeUpdate("""CREATE INDEX IF NOT EXISTS users_name_idx ON "public"."users" ("name")""")
    },
))
```

## Transactions

```kotlin
db.transaction {
    Users.new(user)
    placeOrder(order)          // a helper that joins this same transaction (see below)
    savepoint {                // a nested unit: if it throws, only its work is undone
        Audit.new(entry)
    }
}                              // COMMIT here; any exception → ROLLBACK
```

- **`transaction { }`** — one transaction on a pinned connection.
- **`autocommit { }`** — a pinned connection without a surrounding transaction.
- **`savepoint { }`** — a nested unit via SQL `SAVEPOINT`; a failure inside rolls back
  only the savepoint and the enclosing transaction may continue.
- **`suspendTransaction { }` / `suspendAutocommit { }`** — coroutine-friendly variants
  that run the (blocking) driver call on `Dispatchers.IO`, so a coroutine (e.g. a ktor
  handler) suspends instead of blocking its thread. Prefer these from suspend code.
- **Composition** — write transactional helpers as extensions on the scope so they join
  the caller's transaction:

  ```kotlin
  fun Scope<Main>.placeOrder(order: Order) {
      Orders.new(order)
  }
  ```

  Calling another database's `transaction { }` inside opens an *independent* transaction
  (a separate connection).

## Error handling

A failed statement throws a `QueryException` carrying the SQLSTATE; common constraint
violations have typed subtypes you can catch directly (same on the JVM and native
backends):

```kotlin
try {
    db.transaction { Users.new(user) }
} catch (e: UniqueViolationException) {   // SQLSTATE 23505
    // duplicate key — e.g. respond 409
}
```

Subtypes: `UniqueViolationException` (23505), `ForeignKeyViolationException` (23503),
`NotNullViolationException` (23502), `CheckViolationException` (23514). Anything else is a
`QueryException` with its `sqlState`.

## Compile-time catalog safety & sharding

Because a `Table` is tagged with its `Catalog`, the compiler rejects using a table from
a different catalog inside a scope:

```kotlin
object Cached : Catalog
object Cache : Table<Cached, Row>(Meta("cache"), ::Row) { /* ... */ }

db.transaction {           // db: Database<Main>
    Users.new(user)        // ✓ Users is Table<Main, _>
    Cache.new(row)         // ✗ compile error: Cache is Table<Cached, _>
}
```

The catalog tag is phantom, so you can have **many database instances per catalog**
(e.g. shards):

```kotlin
val shard0: Database<Main> = createDatabase(host = "shard0", /* ... */)
val shard1: Database<Main> = createDatabase(host = "shard1", /* ... */)

shardFor(tenantId).transaction { Users.new(user) }
```

## Multi-backend

`core` is backend-agnostic — SQL rendering and value conversion go through a `Dialect`
and `TypeMapper` that each backend provides, so the same tables, entities, queries,
scopes and transactions run unchanged on any backend. Two backends ship today:
**PostgreSQL** (`korm-postgres`) and **SQLite** (`korm-sqlite`).

### SQLite

`createSqliteDatabase` returns a pool, just like `createDatabase`. Everything in the
Quick start works the same — only the connection differs:

```kotlin
import io.github.knyazevs.korm.createSqliteDatabase
import io.github.knyazevs.korm.database.Database

// In-memory (shared-cache, lives while the handle is open) — great for tests:
val db: Database<Main> = createSqliteDatabase()           // path = ":memory:"

// File-backed (opened in WAL mode for concurrent reads):
val db: Database<Main> = createSqliteDatabase("app.db")

db.transaction {
    Users.createTable()                                   // CREATE TABLE with SQLite types
    Users.new(User().apply { id = Uuid.random(); name = "Ada"; age = 36 })
}
```

Notes:
- SQLite is a single-writer engine, so `poolSize` defaults to **1** (no `database is
  locked`). File databases use WAL, which permits many concurrent readers alongside one
  writer — raise `poolSize` if you want that.
- Foreign keys are enabled (`PRAGMA foreign_keys=ON`), so FK violations surface as
  `ForeignKeyViolationException`; unique/PK violations surface as `UniqueViolationException`.
- Types map to SQLite storage classes (`INTEGER`/`REAL`/`TEXT`); `UUID`, `BigDecimal`,
  `Json` and the temporal types are stored as TEXT and parsed back transparently.
- Tables declared without a schema (the default) resolve through the connection's default
  schema, so the same `Table` definition works on both Postgres (`search_path`) and SQLite.

#### Native requirement: sqlite3

The Kotlin/Native driver links against the system **sqlite3** library, which ships with
macOS and most Linux distributions. On Debian/Ubuntu install the headers if missing:
`apt-get install libsqlite3-dev`.

## Benchmarks

The `:benchmarks` module has a JMH harness comparing korm against Exposed and Hibernate on
the JVM (`./gradlew :benchmarks:jmh`), plus a native korm harness (`NativeBenchmark`, opt-in
via `KORM_BENCH`); point both at one Postgres with `KORM_DB_HOST` etc. Indicative throughput
(ops/s, 8 threads/workers, higher is better; one developer laptop, same database — treat as
relative, not absolute):

| Operation   | korm (JVM) | korm (Native) | Exposed | Hibernate |
| ----------- | ---------- | ------------- | ------- | --------- |
| findById    | ~8.2k      | ~13.2k        | ~8.2k   | ~16.0k    |
| selectWhere | ~8.2k      | ~13.7k        | ~7.9k   | ~16.3k    |
| insert      | ~8.0k      | ~5.0k         | ~7.9k   | ~8.3k     |

- korm on the JVM tracks Exposed closely (same JDBC/HikariCP layer).
- korm on **Native is ~1.6× faster than on the JVM for reads** (libpq directly, no JDBC),
  closing most of the gap to Hibernate.
- Native inserts are slower because the libpq driver issues `BEGIN`/`COMMIT` as their own
  round-trips (the JVM driver defers `BEGIN`) — a known optimization target.
- Hibernate leads on reads (its mature statement-caching / fetch machinery).

Numbers vary by machine — run them yourself.

## License

MIT — see [LICENSE](LICENSE).
