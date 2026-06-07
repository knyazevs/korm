# Changelog

All notable changes to korm are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] — first public release

First release published to Maven Central (group `io.github.knyazevs.korm`), with artifacts
for **JVM** and **Kotlin/Native** (`linuxX64`, `macosX64`, `macosArm64`).

### Added
- **Kotlin Multiplatform ORM** with a backend-agnostic core (`korm-core`) and pluggable
  backends: **PostgreSQL** (`korm-postgres`, JVM via JDBC/HikariCP + Native via libpq) and
  **SQLite** (`korm-sqlite`, JVM via sqlite-jdbc + Native via the sqlite3 cinterop).
- Compile-time database↔table safety via `Catalog` tags and `Database<G>`; sharding by
  holding many `Database<G>` instances.
- Transactions and scopes: `transaction { }` / `autocommit { }` (+ `suspend` variants),
  savepoints, typed errors (`UniqueViolationException`, `ForeignKey`, `NotNull`, `Check`).
- Query power: typed predicates (`eq`, `inList`, `like`, `isNull`, …), `innerJoin`/`leftJoin`,
  aggregations (`count`/`min`/`max`/`sum`/`avg`), `groupBy`/`having`/`distinct`.
- Schema + idempotent migrations: `createTable()`/`dropTable()`, `Database.migrate(...)`.
- 14 column types (incl. `Long`, `Float`, `Short`, `LocalDate`/`LocalTime`/`LocalDateTime`),
  primary-key abstraction, `INSERT … RETURNING` (opt-in), batch insert, `count()`.
- Ktor server integration split per DI framework: `korm-ktor` (DI-agnostic),
  `korm-ktor-di` (Ktor built-in DI), `korm-ktor-koin` (Koin).
- `korm-bom` Bill of Materials pinning all artifact versions.

### Changed
- Module layout unified under the `korm-` prefix: `core` → `korm-core`; the former `pg`
  (Postgres dialect/driver interface) and `pgkn` (native libpq driver) modules were folded
  into `korm-postgres` (commonMain + nativeMain respectively).
- Publishing moved from the retired OSSRH endpoint to the **Maven Central Portal**, driven
  by the `com.vanniktech.maven.publish` plugin.

[0.1.0]: https://github.com/knyazevs/korm/releases/tag/v0.1.0
