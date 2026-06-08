# Changelog

All notable changes to korm are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] — API redesign

A breaking redesign of the core API. Korm now models runtime query/insert/update mapping
only — schema ownership moves out to migrations / raw SQL. (Pre-1.0, so breaking changes
bump the minor version.)

### Added
- **Read query block DSL** over `Query(...)`: `Table.find { where { … }; orderBy DESC …;
  limit = …; offset = … }` and `Table.count { where { … } }`. Multiple `where { }` blocks
  AND together (each parenthesized); `Query(...)` value API is unchanged.
- **Null predicates** `column eq null` / `column neq null`, rendering `IS NULL` /
  `IS NOT NULL` while keeping the comparison vocabulary uniform.
- **Mutation block DSL**: `Table.update(patch) { where { … } }` and
  `Table.deleteWhere { where { … } }`, mirroring the read DSL.
- **`upsert(entity, onConflict, update, returning)`** and **`insertOrIgnore(entity,
  onConflict)`** for single- and composite-column conflict targets, rendered cross-dialect
  as `ON CONFLICT … DO UPDATE` / `DO NOTHING`.
- **Per-database `KormConfig`** (with `batchInsertMode`), threaded through
  `createDatabase` / `createSqliteDatabase` / `createR2dbcDatabase` and carried on the
  `Database` / `SuspendDatabase` handle.
- **Batch insert modes** for `insertAll`: `Strict`, `GroupByAssignedFields` (default,
  preserves input order on `returning`) and `UnionNulls`.

### Changed
- **Type-safe column nullability + fluent column API**. Nullability is now encoded in the
  type: `val note by Column.Text().nullable()` (entity property `String?`),
  `val id by Column.UUID().primaryKey()` (non-null PK). `Column` splits into
  `NotNullColumn` / `NullableColumn`; `.nullable()` and `.primaryKey()` are mutually
  exclusive in the type system. Custom SQL name via constructor `name = …`. Removed the
  `nullable=` / `primaryKey=` constructor params and the per-type `*Type` classes.
- **`Table` takes the SQL table name directly** (`Table("users", ::User)`); the `schema`
  concept and `Table.Meta` are removed (rely on the connection's search_path / migrations).
  `Entity.fields` is now internal (with `replaceFields` / `isSet` / `unset` escape hatches);
  user entities are just `class User : Entity()`, and `@Serializable` is no longer required
  on them.
- **`Scope`/`SuspendScope` `new()` / `new(List)` renamed to `insert()` / `insertAll()`.**
- **`executeUpdate` returns the affected-row count** (`Long`) across JDBC, native libpq,
  native/Android SQLite and r2dbc; `update()` and `deleteWhere()` propagate it (0 = no row
  matched, for not-found / optimistic locking).
- A single `insert` now omits absent fields (so DB defaults / generated values apply) and
  emits `INSERT … DEFAULT VALUES` when nothing is set; an explicit `null` is still bound as
  `NULL`.

### Removed
- **`createTable()` / `dropTable()`** (from `Scope` / `SuspendScope`), the
  `createTableSql` / `dropTableSql` builders on `Table`, and `Dialect.sqlType` (with its
  `PostgresDialect` / `SqliteDialect` overrides). Korm no longer owns schema DDL — create
  schema via raw `CREATE TABLE` (`execSql` / `executeUpdate`) or a migration tool
  (Flyway, Liquibase, …).

### Fixed
- **JDBC**: `JdbcExecutor` now closes the `PreparedStatement` after each execute (previously
  every prepared statement leaked until the pooled connection was returned).

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

[0.2.0]: https://github.com/knyazevs/korm/releases/tag/v0.2.0
[0.1.0]: https://github.com/knyazevs/korm/releases/tag/v0.1.0
