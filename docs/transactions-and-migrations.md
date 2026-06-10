# Transactions and Migrations

Kormium table operations run inside a scope. A scope pins one connection and provides the
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
- If the block throws, Kormium rolls the transaction back and rethrows.
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

If the savepoint block throws, Kormium rolls back to that savepoint. The enclosing transaction
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

For `kormium-postgres` and `kormium-sqlite`, suspend work is offloaded from the caller:

- on JVM, to a virtual-thread dispatcher;
- on Native, to `Dispatchers.Default`.

This keeps coroutine workers free, but the underlying driver is still blocking.

## True Async PostgreSQL

`kormium-r2dbc` implements `SuspendDatabase<G>` only. There is no blocking `Database<G>` API
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

Migrations live in the separate **`kormium-migrate`** module (Kormium core does not own schema). Add
the dependency and import from `io.github.kormium.migrate`:

```kotlin
// build.gradle.kts
implementation("io.github.kormium:kormium-migrate")
```

A migration is **raw SQL** with a stable `id` and is bound to a catalog. Kormium does not generate
DDL, so the SQL is intentionally backend-specific — write it for the database you target. A
single SQL string is split into statements on top-level `;` (quoted strings/identifiers,
`--` / `/* */` comments and Postgres `$tag$…$tag$` bodies are respected):

```kotlin
import io.github.kormium.migrate.Migration
import io.github.kormium.migrate.migrate

db.migrate(
    listOf(
        Migration("001-create-users", """
            CREATE TABLE "users" ("id" uuid PRIMARY KEY, "name" text NOT NULL, "age" integer NOT NULL);
            CREATE INDEX users_name_idx ON "users" ("name");
        """),
    ),
)
```

For SQL the splitter can't handle (e.g. a Postgres function body containing `;`), pass the
statements explicitly: `Migration("002-fn", listOf(stmtA, stmtB))`.

What the runner guarantees:

- **Ordered & idempotent.** Applied ids are recorded in `kormium_migrations` (with the SQL
  checksum, an `applied_at` timestamp and the apply order); only missing migrations run, so
  calling `migrate(...)` on every startup is safe.
- **Checksum validation.** If an already-applied migration's SQL is later edited, `migrate`
  fails fast with `MigrationChecksumException`. Migrations are immutable once applied — add a
  new one instead.
- **Concurrency-safe.** The whole run executes in one transaction; on PostgreSQL it takes a
  transaction-scoped advisory lock first, so several instances starting at once block and don't
  apply the same migration twice. SQLite has no advisory lock, so concurrent cross-process
  migration is not fully serialized — it stays safe (the journal primary key plus the
  all-or-nothing transaction rule out double-application; at worst one instance rolls back and
  no-ops on restart), but prefer migrating SQLite from a single process.
- **All-or-nothing.** Because the batch is one transaction, a failure rolls the whole batch back
  and records nothing. (A statement that cannot run inside a transaction — e.g.
  `CREATE INDEX CONCURRENTLY` — therefore cannot be part of a batch.)

The idiomatic place to run migrations is the `createX { }` builder's `beforeStart { }` hook,
which runs once after the pool is up and before the database is returned:

```kotlin
val db: Database<App> = createDatabase(host = "…", database = "…", user = "…", password = "…") {
    beforeStart { migrate(appMigrations) }
}
```

## Error Handling

Backend errors are normalized to Kormium exceptions where possible.

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
