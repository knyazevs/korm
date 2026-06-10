# korm-core

The multiplatform core of [Kormium](../readme.md): the type-safe table/entity DSL, the typed
SQL predicate builder, transaction scopes and the driver seam (`Dialect`, `SqlExecutor`,
`ResultSet`, `WriteListener`) that the backend modules implement.

This module contains **no database driver**. You normally do not depend on it directly — a
backend artifact (`korm-postgres`, `korm-sqlite`, `korm-r2dbc`) brings it in transitively. Add
`korm-core` on its own only when you:

- write pure common code (tables, entities, queries) shared across modules, or
- implement a custom backend against the driver seam.

## Install

```kotlin
dependencies {
    implementation(platform("io.github.kormium:korm-bom:<version>"))
    implementation("io.github.kormium:korm-core")
}
```

## What it provides

- **Tables and entities.** `Table<G, T>` with `Column.UUID()`, `Column.Text()`, `Column.Int()`,
  … and `Entity` property delegation.
- **Catalog safety.** A `Table<App, User>` can only be used inside a `Database<App>` scope; the
  compiler rejects mixing catalogs.
- **Typed SQL DSL.** `Users.find { where { Users.age gtEq 18 }; orderBy DESC Users.age }` —
  values are always bound as parameters.
- **Transaction scopes.** Blocking `transaction { }` / `autocommit { }` and suspend
  `suspendTransaction { }` / `suspendAutocommit { }`.
- **Configuration.** `KormConfig` and the `KormBuilder` used by the `createX { }` builders
  (`config { }`, `beforeStart { }`).
- **Extensible types.** Open `ColumnType<T>` (enum / json / custom converters) plus the 14
  built-in column types.
- **Driver seam.** `Dialect`, `SqlExecutor`, `ResultSet`, `SqlParameterSource`, and the
  `WriteListener` commit hook that `korm-observe` builds on.

## Example

```kotlin
object App : Catalog

object Users : Table<App, User>("users", ::User) {
    val id by Column.UUID().primaryKey()
    val name by Column.Text()
    val age by Column.Int()
}

class User : Entity() {
    var id by Users.id
    var name by Users.name
    var age by Users.age
}

// A driver module supplies the actual Database<App>; the DSL above is pure korm-core.
val adults = db.autocommit {
    Users.find {
        where { Users.age gtEq 18 }
        orderBy DESC Users.age
        limit = 50
    }
}
```

## Platforms

JVM, Linux/macOS Native, iOS and Android. The suspend API is coroutine-based; the JVM suspend
offload path uses virtual threads (JDK 21+).

## Documentation

- [Tables and entities](../docs/tables-and-entities.md)
- [Queries, joins and aggregations](../docs/queries.md)
- [Transactions, suspend API and migrations](../docs/transactions-and-migrations.md)
- [Design notes](../docs/design.md)
