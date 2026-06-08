# Tables and Entities

Korm models database shape with three pieces:

- `Catalog` marks a logical database.
- `Table<G, T>` describes a table belonging to catalog `G`.
- `Entity` stores row fields and exposes typed delegated properties.

## Catalogs

```kotlin
object Main : Catalog
object CacheCatalog : Catalog
```

Catalogs are phantom type tags. A `Database<Main>` accepts `Table<Main, *>` operations and
rejects `Table<CacheCatalog, *>` operations at compile time.

```kotlin
db.transaction {           // db: Database<Main>
    Users.new(user)        // OK: Users is Table<Main, User>
    CacheRows.new(row)     // Compile error if CacheRows is Table<CacheCatalog, CacheRow>
}
```

You can still have many database instances for one catalog, which is useful for sharding:

```kotlin
val shard0: Database<Main> = createDatabase(host = "shard0", database = "app", user = "postgres", password = "password")
val shard1: Database<Main> = createDatabase(host = "shard1", database = "app", user = "postgres", password = "password")

fun shardFor(tenantId: String): Database<Main> = if (tenantId.hashCode() % 2 == 0) shard0 else shard1
```

## Table Metadata

```kotlin
object Users : Table<Main, User>(Meta("users"), ::User)
```

`Meta("users")` uses an unqualified table name. If you need a schema, pass it in metadata:

```kotlin
object Users : Table<Main, User>(Meta(tableName = "users", schema = "public"), ::User)
```

Unqualified names resolve through the backend's default schema/search path. This keeps the
same table definitions portable between PostgreSQL and SQLite.

## Columns

Columns are declared with delegated properties:

```kotlin
object Users : Table<Main, User>(Meta("users"), ::User) {
    val id by Column.UUID(primaryKey = true)
    val name by Column.Text()
    val age by Column.Int()
    val note by Column.Text(nullable = true)
}
```

Supported column types:

| Column | Kotlin value |
| --- | --- |
| `Column.UUID` | `kotlin.uuid.Uuid` |
| `Column.Text` | `String` |
| `Column.Boolean` | `Boolean` |
| `Column.Short` | `Short` |
| `Column.Int` | `Int` |
| `Column.Long` | `Long` |
| `Column.Float` | `Float` |
| `Column.Double` | `Double` |
| `Column.BigDecimal` | `com.ionspin.kotlin.bignum.decimal.BigDecimal` |
| `Column.Instant` | `kotlinx.datetime.Instant` |
| `Column.LocalDate` | `kotlinx.datetime.LocalDate` |
| `Column.LocalTime` | `kotlinx.datetime.LocalTime` |
| `Column.LocalDateTime` | `kotlinx.datetime.LocalDateTime` |
| `Column.Json` | `kotlinx.serialization.json.JsonElement` |

Every column accepts:

```kotlin
Column.Text(nullable = true)
Column.UUID(primaryKey = true)
```

`findById` uses a single explicit primary key. If none is marked, it falls back to a column
named `id`. For composite primary keys, use `find(Query(...))` instead of `findById`.

## Entities

```kotlin
class User(
    override var fields: MutableMap<String, Any?> = mutableMapOf(),
) : Entity(fields) {
    var id by Users.id
    var name by Users.name
    var age by Users.age
    var note by Users.note
}
```

Entities are intentionally small. They wrap a mutable field map and expose typed property
delegates. This gives Korm two useful update semantics:

- A property never assigned on the entity is omitted from `UPDATE`.
- A property assigned to `null` is included and written as SQL `NULL`.

```kotlin
Users.update(Query(Users.id eq id), User().apply { note = null })
```

This updates only `note`.

## Schema Generation

```kotlin
db.transaction {
    Users.createTable()
    Users.dropTable()
}
```

`createTable()` generates:

- table name with dialect-specific identifier quoting;
- column SQL types;
- `NOT NULL` for non-null columns;
- primary key constraint from `primaryKey = true`.

It does not generate indexes, foreign keys or custom checks yet. Use raw SQL inside a
transaction for those:

```kotlin
db.transaction {
    executeUpdate("""CREATE INDEX IF NOT EXISTS users_name_idx ON "users" ("name")""")
}
```
