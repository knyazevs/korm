# Korm

Korm is a small, type-safe ORM for **Kotlin Multiplatform** (JVM and Native) backed
by PostgreSQL. It gives you tables, entities and transactions with a compile-time
guarantee that a table is only ever used against the database it belongs to.

> Status: early (0.0.x). The API may still change.

## Modules

| Module | What |
| --- | --- |
| `core` | Backend-agnostic API: `Table`, `Column`, `Entity`, `Query`, `Catalog`, `Database<G>`, scopes/transactions. No backend dependency. |
| `korm-postgres` | The PostgreSQL binding: `createDatabase(...)` plus the JVM (JDBC/HikariCP) and Native (libpq) drivers. |

Depend on `korm-postgres` — it brings `core` with it:

```kotlin
dependencies {
    implementation("io.github.knyazevs.korm:korm-postgres:<version>")
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

`new` returns the stored row (via SQL `RETURNING`); insert many rows in one statement
with the list overload, and count rows with `count`:

```kotlin
val saved: User? = db.transaction { Users.new(user) }
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
and `TypeMapper` that each backend provides. Today only the PostgreSQL backend ships;
the seam is there so additional backends (e.g. SQLite) can be added as their own module
without changing `core`.

## License

MIT — see [LICENSE](LICENSE).
