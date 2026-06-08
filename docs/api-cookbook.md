# API Cookbook

This page collects practical recipes. It is intentionally example-heavy and should grow as
real usage patterns appear.

## Define a Table

```kotlin
object App : Catalog

object Users : Table<App, User>("users", ::User) {
    val id by Column.UUID().primaryKey()
    val email by Column.Text()
    val name by Column.Text()
    val deletedAt by Column.Instant().nullable()
}

class User : Entity() {
    var id by Users.id
    var email by Users.email
    var name by Users.name
    var deletedAt by Users.deletedAt
}
```

## Create Tables and Indexes

Korm does not own schema; create it with raw SQL or a migration tool.

```kotlin
db.transaction {
    executeUpdate(
        """CREATE TABLE IF NOT EXISTS "users" ("id" uuid NOT NULL, "email" text NOT NULL, "name" text NOT NULL, "deletedAt" timestamptz, PRIMARY KEY ("id"))""",
    )
    executeUpdate("""CREATE UNIQUE INDEX IF NOT EXISTS users_email_idx ON "users" ("email")""")
}
```

## Insert a Row

```kotlin
val user = User().apply {
    id = Uuid.random()
    email = "ada@example.com"
    name = "Ada"
    deletedAt = null
}

db.transaction {
    Users.insert(user)
}
```

## Fetch Database-Generated Values

```kotlin
val saved: User? = db.transaction {
    Users.insert(user, returning = true)
}
```

Use `returning = true` when the database may fill values you want to read back.

## Batch Insert

```kotlin
val saved: List<User> = db.transaction {
    Users.insertAll(listOf(user1, user2, user3), returning = true)
}
```

## Partial Update

```kotlin
db.transaction {
    Users.update(
        Query(Users.id eq id),
        User().apply { name = "Ada Lovelace" },
    )
}
```

Only assigned fields are written.

## Set a Nullable Column to NULL

```kotlin
db.transaction {
    Users.update(
        Query(Users.id eq id),
        User().apply { deletedAt = null },
    )
}
```

An assigned `null` is different from an untouched property. Korm writes assigned `null`
values as SQL `NULL`.

## Paginate

```kotlin
val page = db.autocommit {
    Users.find(
        Query(
            whereExpression = Users.deletedAt.isNull(),
            orderBy = mapOf(Users.email to AscDescOrder.ASC),
            limit = 50u,
            offset = 100u,
        )
    )
}
```

Offset pagination is simple and portable. For high-volume feeds, prefer keyset pagination:

```kotlin
val page = db.autocommit {
    Users.find(
        Query(
            whereExpression = (Users.email gt lastSeenEmail) and Users.deletedAt.isNull(),
            orderBy = mapOf(Users.email to AscDescOrder.ASC),
            limit = 50u,
        )
    )
}
```

## Count Rows

```kotlin
val total = db.autocommit {
    Users.count()
}

val active = db.autocommit {
    Users.count(Query(Users.deletedAt.isNull()))
}
```

## Left Join with Nullable Right Side

The join examples assume an `Orders` table declared in the same style as `Users`.

```kotlin
val rows = db.autocommit {
    (Users leftJoin Orders on (Users.id eq Orders.userId))
        .select(Users.email, Orders.total)
}

rows.forEach { row ->
    val email = row[Users.email]
    val total = row.getOrNull(Orders.total)
}
```

Use `getOrNull` when a selected field can be absent or SQL `NULL`.

## Aggregate and Read the Result

```kotlin
val orderCount = Orders.id.count()
val totalSpent = Orders.total.sum()

val rows = db.autocommit {
    (Users innerJoin Orders on (Users.id eq Orders.userId))
        .groupBy(Users.id)
        .select(Users.email, orderCount, totalSpent)
}

rows.forEach { row ->
    println("${row[Users.email]}: ${row[orderCount]} orders, ${row[totalSpent]}")
}
```

Keep aggregates in variables and read rows with the same instances.

## Compose Transactional Helpers

This example assumes an `AuditEvents` table and `AuditEvent` entity in the same catalog.

```kotlin
fun Scope<App>.registerUser(user: User) {
    Users.insert(user)
    AuditEvents.insert(AuditEvent.forUser(user.id))
}

db.transaction {
    registerUser(user)
}
```

This helper joins the caller's transaction. It does not open a second connection.

## Use Suspend API in a Handler

```kotlin
suspend fun listUsers(db: SuspendDatabase<App>): List<User> =
    db.suspendAutocommit {
        Users.find(Query(Users.deletedAt.isNull()))
    }
```

The same table DSL works with blocking and suspend scopes.

## Handle Constraint Errors

```kotlin
try {
    db.transaction {
        Users.insert(user)
    }
} catch (e: UniqueViolationException) {
    // Return 409 Conflict or equivalent application error.
}
```

For Ktor, `korm-ktor` includes `KormException.httpStatusCode()`.

## Run Migrations on Startup

```kotlin
db.migrate(
    listOf(
        Migration("001-create-users") {
            executeUpdate(
                """CREATE TABLE IF NOT EXISTS "users" ("id" uuid NOT NULL, "email" text NOT NULL, "name" text NOT NULL, PRIMARY KEY ("id"))""",
            )
        },
        Migration("002-users-email-index") {
            executeUpdate("""CREATE UNIQUE INDEX IF NOT EXISTS users_email_idx ON "users" ("email")""")
        },
    )
)
```

Migration IDs are permanent. Do not edit an already-applied migration in a released
application; add a new migration instead.

## Use SQLite in Tests

```kotlin
val db: Database<App> = createSqliteDatabase()

db.transaction {
    executeUpdate(
        """CREATE TABLE IF NOT EXISTS "users" ("id" INTEGER NOT NULL, "name" TEXT NOT NULL, PRIMARY KEY ("id"))""",
    )
}
```

The default SQLite database is in-memory and lives while the database handle is open.

## Resolve a Database in Ktor DI

```kotlin
dependencies {
    provide<SuspendDatabase<App>> {
        createDatabase(
            host = "localhost",
            database = "postgres",
            user = "postgres",
            password = "password",
        )
    }
}

routing {
    get("/users") {
        call.respond(
            call.autocommit<App, _> {
                Users.all()
            }
        )
    }
}
```

Use `SuspendDatabase` in server routes so the route body can suspend naturally.
