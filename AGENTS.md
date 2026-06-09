# Korm for AI agents

Type-safe Kotlin Multiplatform ORM / SQL DSL. This file is the canonical, copy-ready
reference: prefer the forms shown here over any you might infer. Package is
`io.github.kormium`. Full docs in [`docs/`](docs/README.md); deeper examples in
[`samples/`](samples/README.md).

## Mental model (read this first)

- A `Catalog` is a compile-time database identity; a `Database<G>` is an instance of it.
- A `Table<G, T>` is a pure schema descriptor; `T : Entity` holds the row's values.
- **All queries run inside a scope**: `db.transaction { ... }` (BEGIN/COMMIT/ROLLBACK) or
  `db.autocommit { ... }` (one pinned connection, no explicit tx — use for reads). Table
  operations (`find`, `insert`, `update`, joins, ...) **only exist inside that block** — they
  are extensions on the scope, not on the table globally.
- Suspend mirror: `db.suspendTransaction { ... }` / `db.suspendAutocommit { ... }` with the
  same API. (`korm-r2dbc` is true async; JDBC/SQLite/libpq are offloaded blocking.)

## Define a schema

```kotlin
import io.github.kormium.*

object App : Catalog

object Users : Table<App, User>("users", ::User) {
    val id by Column.UUID().primaryKey()
    val name by Column.Text()
    val age by Column.Int()
    val note by Column.Text().nullable()   // nullable -> entity property is String?
}

class User : Entity() {
    var id by Users.id        // non-null column -> non-null property (no `!!` needed)
    var name by Users.name
    var age by Users.age
    var note by Users.note    // String?
}
```

## Connect

```kotlin
import io.github.kormium.database.createDatabase

val db: Database<App> = createDatabase(
    host = "localhost", database = "postgres", user = "postgres", password = "password",
)
// SQLite: createSqliteDatabase(path = ":memory:")   (from korm-sqlite)
```

## Read — the canonical single-table form

```kotlin
val adults: List<User> = db.autocommit {
    Users.find {
        where { Users.age gtEq 18 }
        where { Users.name like "A%" }   // multiple where { } blocks AND together
        orderBy DESC Users.age
        limit = 50
        offset = 0
    }
}

val ada: User? = db.autocommit { Users.findById(uuid) }   // targets the primary key
val all: List<User> = db.autocommit { Users.all() }
val n: Long = db.autocommit { Users.count { where { Users.age gtEq 18 } } }
```

Predicates: `eq`, `gtEq`, `gt`, `ltEq`, `lt`, `like`, `inList(...)`, `isNull()`,
`isNotNull()`, combine with `and` / `or` / `not(...)`. Values are typed (`Users.age eq 18`,
not `"18"`) and always bound as parameters.

For a reusable query, build a `Query` directly: `Users.find(Query(Users.age gtEq 18))`.

## Write

```kotlin
db.transaction {
    Users.insert(user)                       // plain INSERT, returns the entity
    Users.insert(user, returning = true)     // INSERT ... RETURNING for DB-generated values
    Users.insertAll(listOf(a, b, c))

    Users.update(User().apply { age = 37 }) { where { Users.id eq id } }   // only set fields
    Users.deleteWhere { where { Users.name eq "Ada" } }
}
```

`update` writes only the properties you assigned on the patch entity; untouched ones are left
alone (assigning `null` does set NULL).

## Joins

```kotlin
// Read rows, then index by the column you selected:
db.autocommit {
    (Users innerJoin Orders on (Users.id eq Orders.userId))
        .where(Users.age gtEq 18)            // note: .where(expr) on a join, not a where { } block
        .select()
        .map { row -> row[Users.name] to row[Orders.total] }
}

// Two-table join -> entity pairs:
val pairs: List<Pair<User, Order>> = db.autocommit {
    (Users innerJoin Orders on (Users.id eq Orders.userId)).find()
}

// Three or more tables -> select() + row.entity(Table) for whole entities:
val triples = db.autocommit {
    (Users innerJoin Orders on (Users.id eq Orders.userId)
           innerJoin Items  on (Orders.id eq Items.orderId))
        .select()
        .map { Triple(it.entity(Users), it.entity(Orders), it.entity(Items)) }
}
```

`leftJoin` is available; read nullable right-side fields with `row.getOrNull(col)`.

## Aggregates

```kotlin
val total = Orders.total.sum()
db.autocommit {
    (Users innerJoin Orders on (Users.id eq Orders.userId))
        .groupBy(Users.id)
        .having(total gt Value(BigDecimal.fromInt(100)))
        .select(Users.name, count(), total)
}.forEach { row ->
    println("${row[Users.name]}: ${row[count()]} orders, ${row[total]}")
}
```

Aggregates: `count()`, `col.count()`, `col.min()`, `col.max()`, `col.sum()`, `col.avg()`.
Read them from a row by the same expression you selected — keys are structural, so
`row[count()]` works with a fresh instance (a `val` is handy for reuse / `having`, not
required). Single-table grouping starts from `Table.query()`.

## Which form for what

| Task | Use |
|------|-----|
| Filtered read, one table | `Table.find { where { } orderBy limit }` |
| By primary key | `Table.findById(id)` |
| Reusable/prebuilt query | `Table.find(Query(...))` |
| Two-table join as entities | `(A innerJoin B on ...).find()` |
| 3+ table join as entities | `.select()` + `row.entity(Table)` |
| Columns / aggregates from a join | `.select(...)` + `row[col]` / `row[agg]` |
| Single-table grouping | `Table.query().groupBy(...).select(...)` |

## Gotchas

- Operations are scope extensions — they don't compile outside `db.transaction { }` /
  `db.autocommit { }` (or the `suspend*` variants).
- A `Table<G, _>` can only be used in a `Database<G>` scope; mixing catalogs is a compile error.
- `findById` targets the primary key and throws on a composite key — use `find(Query(col eq v))`.
- Read nullable join columns with `getOrNull`; `row[col]` throws on NULL/absent.
