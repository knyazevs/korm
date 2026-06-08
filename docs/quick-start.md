# Quick Start

This guide creates a catalog, declares a table, connects to PostgreSQL and runs basic CRUD.

## 1. Define a Catalog

A `Catalog` is a type tag for one logical database. It has no runtime data; it exists so the
compiler can reject using tables with the wrong database handle.

```kotlin
import io.github.knyazevs.korm.Catalog

object App : Catalog
```

## 2. Define a Table and Entity

```kotlin
import io.github.knyazevs.korm.Column
import io.github.knyazevs.korm.Entity
import io.github.knyazevs.korm.Meta
import io.github.knyazevs.korm.Table
import kotlin.uuid.Uuid

object Users : Table<App, User>("users", ::User) {
    val id by Column.UUID().primaryKey()
    val name by Column.Text()
    val age by Column.Int()
    val note by Column.Text().nullable()
}

class User : Entity() {
    var id by Users.id
    var name by Users.name
    var age by Users.age
    var note by Users.note
}
```

Columns register themselves when the delegated properties are declared. Read operations
select columns in declaration order and map rows back into entity fields.

## 3. Connect

```kotlin
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.database.createDatabase

val db: Database<App> = createDatabase(
    host = "localhost",
    port = 5432,
    database = "postgres",
    user = "postgres",
    password = "password",
    poolSize = 10,
)
```

Assigning the factory result to `Database<App>` pins the catalog type. The driver itself is
catalog-agnostic.

## 4. Create the Table

```kotlin
import io.github.knyazevs.korm.transaction

db.transaction {
    Users.createTable()
}
```

`createTable()` emits `CREATE TABLE IF NOT EXISTS` with dialect-specific SQL types,
`NOT NULL` and primary key constraints.

## 5. Insert and Read

```kotlin
import io.github.knyazevs.korm.autocommit
import io.github.knyazevs.korm.gtEq
import io.github.knyazevs.korm.Query
import kotlin.uuid.Uuid

val user = User().apply {
    id = Uuid.random()
    name = "Ada"
    age = 36
    note = null
}

db.transaction {
    Users.new(user)
}

val ada: User? = db.autocommit {
    Users.findById(user.id)
}

val adults: List<User> = db.autocommit {
    Users.find(Query(whereExpression = Users.age gtEq 18, limit = 50u))
}
```

`transaction { }` wraps the block in `BEGIN` / `COMMIT` / `ROLLBACK`. `autocommit { }`
pins one connection without an explicit transaction, which is useful for simple reads.

## 6. Update and Delete

```kotlin
import io.github.knyazevs.korm.eq

db.transaction {
    Users.update(
        Query(Users.id eq user.id),
        User().apply { age = 37 },
    )

    Users.deleteWhere(Query(Users.name eq "Ada"))
}
```

`update` only writes fields present in the entity's `fields` map. That means an untouched
property is omitted, while a property explicitly set to `null` is written as SQL `NULL`.

## SQLite Variant

The table and query code stays the same. Swap only the factory:

```kotlin
import io.github.knyazevs.korm.createSqliteDatabase
import io.github.knyazevs.korm.database.Database

val db: Database<App> = createSqliteDatabase()      // in-memory
val fileDb: Database<App> = createSqliteDatabase("app.db")
```

Next: [Tables and entities](tables-and-entities.md) or [Queries](queries.md).
