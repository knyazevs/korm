# Observing Changes

`korm-observe` turns a query into a `Flow` that re-emits whenever the data it reads changes.
It is the building block for reactive UIs (Compose Multiplatform, Android) the way Room's
observable queries are — without Korm adopting an annotation processor or SQL strings.

```kotlin
dependencies {
    implementation("io.github.kormium:korm-observe")
}
```

## Observing a Table

```kotlin
import io.github.kormium.observe.observe

val adults: Flow<List<User>> = Users.observe(db) {
    where { Users.age gtEq 18 }
}
```

`Users.observe(db) { … }` emits the query result once immediately, then re-runs the query and
emits again after every committed write that touches the `users` table. With no block it
observes every row. `db` is the suspend database handle (`SuspendDatabase<G>`); every shipped
driver provides one.

The query block is the same one used by [`Table.find`](queries.md): predicates, ordering,
`limit`/`offset`. Reads run in `suspendAutocommit`.

## How Invalidation Works

Korm tracks which tables each `transaction { }` / `autocommit { }` (and their suspend
counterparts) writes, and notifies observers **after the block commits**. A rolled-back
transaction notifies nothing. Bursts of writes are conflated — a flood of commits collapses
into a single re-fetch rather than one per commit.

```kotlin
// This commit re-fires every Flow observing "users":
db.suspendTransaction { Users.insert(user) }
```

## The Generic Form

For multi-table queries (joins) or custom fetch logic, use the lower-level overload and pass
the tables to watch:

```kotlin
val dashboard: Flow<Dashboard> = db.observe(setOf("users", "orders")) {
    // any SuspendScope read(s)
    Dashboard(
        userCount = Users.count(),
        orderCount = Orders.count(),
    )
}
```

## Raw SQL

Korm cannot see which tables raw SQL touches, so declare them with `invalidates` so observers
(and any future cache) are notified on commit — the analog of Room's
`@RawQuery(observedEntities = …)`:

```kotlin
db.transaction {
    executeUpdate("UPDATE products SET price = price * 2", invalidates = listOf(Products))
}
```

Without `invalidates`, a raw write commits normally but does not fire observers.

## Boundaries

Observation sees writes made **through this database handle's API**. It does not see:

- writes by another process or another `Database` instance over the same database;
- raw SQL whose tables you did not declare via `invalidates`;
- cascading changes from triggers or `ON DELETE CASCADE` (only the table you wrote is marked).

This is the same default boundary Room has for a single in-process database. Cross-process
invalidation (PostgreSQL `LISTEN/NOTIFY`, SQLite update hooks) is a separate, later concern.

## Lifecycle

Each collector registers its own listener and removes it automatically when collection stops
(the `Flow` is cold). Nothing to close manually. A backend whose driver does not enable
notification emits the initial value and nothing further.
