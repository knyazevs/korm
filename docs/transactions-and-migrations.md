# Transactions and Migrations

Korm table operations run inside a scope. A scope pins one connection and provides the
executor used by table, query and migration operations.

## Blocking Scopes

```kotlin
db.transaction {
    Users.insert(user)
}

val users = db.autocommit {
    Users.all()
}
```

- `transaction { }` opens one transaction and commits on success.
- If the block throws, Korm rolls the transaction back and rethrows.
- `autocommit { }` pins a connection but does not wrap the block in an explicit
  transaction.

## Savepoints

Use `savepoint { }` when one nested unit may fail without rolling back the whole outer
transaction.

```kotlin
db.transaction {
    Users.insert(user)

    savepoint {
        Audit.insert(entry)
    }
}
```

If the savepoint block throws, Korm rolls back to that savepoint. The enclosing transaction
can continue if you catch the exception.

## Transactional Helpers

Prefer helpers that extend `Scope<G>` or `SuspendScope<G>`. They join the caller's existing
transaction instead of opening a second connection.

```kotlin
fun Scope<App>.createUser(user: User) {
    Users.insert(user)
}

db.transaction {
    createUser(user)
}
```

The suspend variant:

```kotlin
suspend fun SuspendScope<App>.createUser(user: User) {
    Users.insert(user)
}
```

Calling another database handle's `transaction { }` inside a transaction creates an
independent transaction on another connection.

## Suspend API

Blocking backends also expose `SuspendDatabase<G>`. The DSL is the same, but the block can
suspend:

```kotlin
import io.github.kormium.database.SuspendDatabase
import io.github.kormium.database.createDatabase
import io.github.kormium.suspendAutocommit
import io.github.kormium.suspendTransaction

val db: SuspendDatabase<App> = createDatabase(
    host = "localhost",
    database = "postgres",
    user = "postgres",
    password = "password",
)

suspend fun handler() {
    db.suspendTransaction {
        Users.insert(user)
    }

    val users = db.suspendAutocommit {
        Users.all()
    }
}
```

For `korm-postgres` and `korm-sqlite`, suspend work is offloaded from the caller:

- on JVM, to a virtual-thread dispatcher;
- on Native, to `Dispatchers.Default`.

This keeps coroutine workers free, but the underlying driver is still blocking.

## True Async PostgreSQL

`korm-r2dbc` implements `SuspendDatabase<G>` only. There is no blocking `Database<G>` API
because r2dbc is non-blocking.

```kotlin
import io.github.kormium.database.SuspendDatabase
import io.github.kormium.r2dbc.createR2dbcDatabase

val db: SuspendDatabase<App> = createR2dbcDatabase(
    host = "localhost",
    database = "postgres",
    user = "postgres",
    password = "password",
)

db.suspendTransaction {
    Users.insert(user, returning = true)
}
```

Use r2dbc when you specifically need non-blocking PostgreSQL I/O on JVM. For many server
applications, a normal connection pool plus virtual-thread offload is simpler and adequate.

## Migrations

Korm migrations are ordered and idempotent. Each migration has a stable `id`; Korm records
applied IDs in `korm_migrations` and runs only missing migrations.

```kotlin
db.migrate(
    listOf(
        Migration("001-create-users") {
            executeUpdate(
                """CREATE TABLE IF NOT EXISTS "users" ("id" uuid NOT NULL, "name" text NOT NULL, "age" integer NOT NULL, PRIMARY KEY ("id"))""",
            )
        },
        Migration("002-users-name-index") {
            executeUpdate("""CREATE INDEX IF NOT EXISTS users_name_idx ON "users" ("name")""")
        },
    )
)
```

Each migration runs in its own transaction. It is safe to call `migrate(...)` during
application startup.

## Error Handling

Backend errors are normalized to Korm exceptions where possible.

```kotlin
try {
    db.transaction { Users.insert(user) }
} catch (e: UniqueViolationException) {
    // Duplicate key, SQLSTATE 23505 on PostgreSQL.
}
```

Common typed exceptions:

| Exception | Typical meaning |
| --- | --- |
| `UniqueViolationException` | Duplicate primary key or unique index |
| `ForeignKeyViolationException` | Foreign key constraint failed |
| `NotNullViolationException` | Required column was written as NULL |
| `CheckViolationException` | Check constraint failed |
| `QueryException` | Other statement/database failure, with SQLSTATE when available |
