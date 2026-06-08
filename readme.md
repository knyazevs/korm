# Korm

[![CI](https://github.com/knyazevs/korm/actions/workflows/ci.yml/badge.svg)](https://github.com/knyazevs/korm/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.knyazevs.korm/korm-core.svg)](https://central.sonatype.com/search?q=g%3Aio.github.knyazevs.korm)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Type-safe ORM and SQL DSL for Kotlin Multiplatform.

Korm gives you an Exposed-like Kotlin API for tables, entities, typed predicates,
transactions, migrations, joins and aggregations, while keeping the core portable across
JVM and Kotlin/Native. It ships PostgreSQL, SQLite, async r2dbc PostgreSQL and Ktor
integration modules.

```kotlin
object App : Catalog

object Users : Table<App, User>("users", ::User) {
    val id by Column.UUID(primaryKey = true)
    val name by Column.Text()
    val age by Column.Int()
}

class User : Entity() {
    var id by Users.id
    var name by Users.name
    var age by Users.age
}

val db: Database<App> = createDatabase(
    host = "localhost",
    database = "postgres",
    user = "postgres",
    password = "password",
)

val adults = db.autocommit {
    Users.find(Query(Users.age gtEq 18, limit = 50u))
}
```

## Why Korm?

- **Multiplatform core.** Write tables, entities, queries and migrations once; run them
  on JVM and Kotlin/Native backends.
- **Typed SQL DSL.** Predicates are built from columns and Kotlin values (`Users.age gtEq
  18`), and values are always bound as parameters.
- **Catalog safety.** A `Table<App, User>` cannot be used inside a `Database<Cache>`
  scope, and the compiler catches that before runtime.
- **Blocking and suspend APIs.** Blocking backends expose `transaction { }` and
  `autocommit { }`; suspend code uses `suspendTransaction { }` and
  `suspendAutocommit { }`.
- **PostgreSQL without JDBC on Native.** JVM uses JDBC/HikariCP, Native uses libpq, and
  r2dbc gives a true async PostgreSQL option on JVM.
- **SQLite for apps, tests and caches.** JVM uses sqlite-jdbc, Native uses sqlite3
  cinterop, Android uses AndroidX SQLite.
- **Server integration.** Ktor helpers are split into DI-agnostic, Ktor DI and Koin
  artifacts.

## Status

Korm is **pre-1.0**. The public API is usable and tested, but still allowed to change.
It is a good fit for experiments, internal tools, prototypes, benchmarks and feedback.
Do not make it the only persistence layer for critical production systems yet.

Requires **JDK 21+** for JVM builds. The JVM suspend offload path uses virtual threads.

## Install

Korm is published to Maven Central under `io.github.knyazevs.korm`.

```kotlin
dependencies {
    implementation(platform("io.github.knyazevs.korm:korm-bom:<version>"))

    implementation("io.github.knyazevs.korm:korm-postgres") // PostgreSQL, JVM + Native
    // implementation("io.github.knyazevs.korm:korm-sqlite")   // SQLite, JVM + Native + Android
    // implementation("io.github.knyazevs.korm:korm-r2dbc")    // async PostgreSQL, JVM only

    // optional Ktor integration
    // implementation("io.github.knyazevs.korm:korm-ktor")
    // implementation("io.github.knyazevs.korm:korm-ktor-di")
    // implementation("io.github.knyazevs.korm:korm-ktor-koin")
}
```

See [Installation](docs/installation.md) for Gradle variants, native system libraries and
module details.

## Documentation

- [Documentation index](docs/README.md)
- [Installation](docs/installation.md)
- [Quick start](docs/quick-start.md)
- [Tables and entities](docs/tables-and-entities.md)
- [Queries, joins and aggregations](docs/queries.md)
- [Transactions, suspend API and migrations](docs/transactions-and-migrations.md)
- [Backends and platform support](docs/backends.md)
- [Ktor integration](docs/ktor.md)
- [API cookbook](docs/api-cookbook.md)
- [API ergonomics review](docs/api-ergonomics.md)
- [Observability](docs/observability.md)
- [Production guide](docs/production-guide.md)
- [Compatibility policy](docs/compatibility.md)
- [Design notes](docs/design.md)
- [Roadmap](docs/roadmap.md)
- [Samples, benchmarks and contributing](docs/project.md)

## Platform Support

| Platform | PostgreSQL | SQLite | Notes |
| --- | --- | --- | --- |
| JVM | JDBC/HikariCP; async r2dbc | sqlite-jdbc | Main server target |
| Linux Native | libpq | sqlite3 | Covered by CI native tests |
| macOS Native | libpq | sqlite3 | Published artifacts for x64 and arm64 |
| Android | Not shipped | AndroidX SQLite | `korm-core` and `korm-sqlite` compile for Android |
| iOS | Not shipped | sqlite3 | `korm-core`, `korm-sqlite` and Ktor integration compile for iOS |
| Windows Native | Planned | Planned | mingw targets are deferred |
| Wasm | Research | Planned | No shipped backend yet |

## Minimal Workflow

```kotlin
db.transaction {
    Users.createTable()
    Users.new(User().apply {
        id = Uuid.random()
        name = "Ada"
        age = 36
    })
}

val ada = db.autocommit {
    Users.find(Query((Users.name like "A%") and (Users.age gtEq 18)))
}
```

For deeper examples, start with [Quick start](docs/quick-start.md) and then read
[Queries](docs/queries.md).

## Samples

Runnable samples live under `samples/`:

| Sample | Shows |
| --- | --- |
| `samples:crud-sqlite` | Standalone SQLite CRUD and migrations |
| `samples:sharding` | Catalog safety and multiple database instances |
| `samples:sqlite-cache` | SQLite cache in front of PostgreSQL |
| `samples:ktor-di` | Ktor CRUD with built-in DI |
| `samples:ktor-koin` | Ktor CRUD with Koin |
| `samples:r2dbc` | Ktor CRUD on async r2dbc PostgreSQL |

See [Samples and benchmarks](docs/project.md#samples).

## License

MIT. See [LICENSE](LICENSE).
