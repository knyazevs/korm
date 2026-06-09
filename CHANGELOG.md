# Changelog

All notable changes to korm are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- **Migrations moved out of `korm-core` into a new `korm-migrate` module** (breaking). Core does
  not own schema, so the migration runner is now opt-in. `io.github.kormium.Migration` / `migrate`
  become `io.github.kormium.migrate.{Migration, migrate}`; add the `korm-migrate` dependency and
  update imports. They still run the same way via `beforeStart { migrate(appMigrations) }`.

### Added
- **`korm-migrate`** module: an ordered, idempotent **raw-SQL** migration runner.
  - `Migration(id, sql)` takes raw SQL split into statements on top-level `;` (quoted
    strings/identifiers, `--` / `/* */` comments and Postgres `$tag$…$tag$` bodies respected), or
    `Migration(id, statements)` for explicit statements.
  - **Checksum validation**: editing an already-applied migration fails fast with
    `MigrationChecksumException`.
  - **Concurrency-safe**: the whole batch runs in one transaction; on PostgreSQL it first takes a
    transaction-scoped advisory lock so concurrently-starting instances block and don't
    double-apply (all-or-nothing — a failed batch records nothing). SQLite has no advisory lock, so
    concurrent cross-process migration is not fully serialized, but the journal primary key plus the
    all-or-nothing rule out double-application (prefer migrating SQLite from one process).
  - The `korm_migrations` journal now also records the SQL `checksum`, an `applied_at` timestamp
    and the apply-order index.
- **`Dialect.advisoryLockSql(key)`** (defaults to `null`): a backend exposes advisory-lock SQL for
  the migration runner; `PostgresDialect` returns `pg_advisory_xact_lock`.

### Migration notes
- The previous `up: Scope.() -> Unit` lambda form is removed — migrations are raw SQL now. Move
  any seed/data logic that used Korm operations into application startup or an explicit-statement
  migration.
- The `korm_migrations` journal gained columns; since 0.x is unpublished, drop any throwaway dev
  journal so it is recreated with the new schema.

## [0.3.0] — Reactive queries

### Added
- **`korm-observe`** module: reactive `Flow` queries. `Table.observe(db) { where { … } }`
  emits the result now and re-emits after every committed write to the table; a generic
  `SuspendDatabase.observe(tables) { … }` covers joins and custom fetches. Writes are
  conflated. See [Observing changes](docs/observe.md).
- **`WriteListener` commit hook** on `Database` / `SuspendDatabase`
  (`db.writeListeners.add { tables -> … }`): notified, after commit, with the set of tables a
  scope wrote. A generic seam (also usable for cache invalidation, audit, metrics) that backs
  `korm-observe`.
- **`invalidates` argument** on the raw `Scope.execute` / `executeUpdate` (and suspend
  counterparts): declare the tables a raw statement writes so observers are notified — the
  analog of Room's `@RawQuery(observedEntities = …)`.
- **`createX { }` configuration builders.** `createSqliteDatabase("app.db") { config { … };
  beforeStart { … } }` and the same for `createDatabase(...)` (PostgreSQL). `config { }` is a
  mutable view of `KormConfig`; `beforeStart { }` runs once before the database is returned —
  the place to run migrations (your own package, or Flyway/Liquibase), which the builder does
  not own. The existing `config: KormConfig` factory overloads are unchanged.
- **Open column-type system.** Column types are now an extensible `ColumnType<T>` interface
  instead of a fixed list. New built-ins `Column.enum<E>()` (enum by name) and
  `Column.json<T>()` (`@Serializable` value as JSON), a `ColumnType<S>.convert(to, from)`
  helper for custom types over an existing one (replacing Room's `@TypeConverter`), and
  `Column.of(type)` to declare a column of any `ColumnType`. The 14 built-in types are
  unchanged in behaviour. Conversion applies on insert, update and in predicates.

### Changed
- **Breaking (internal extension point): the column-type representation changed.**
  `Column.ColumnNameEnum` is removed and `TypeMapper.fromResult(...)` is gone (reading is now
  `ColumnType.read`); `Column.columnType` is a `ColumnType<Z>`. Ordinary table/query/insert
  code is unaffected and behaves identically — only custom `TypeMapper`s or code referencing
  `ColumnNameEnum` need migration (nothing in Korm itself did).
- **Breaking: `Database` no longer extends `SqlExecutor`.** The pooled, scope-less
  `db.execute(...)` / `db.executeUpdate(...)` is removed — run one-off statements through a
  scope instead: `db.autocommit { execute(...) }`. This makes every write transactional and
  observable. `SuspendDatabase` was never an executor, so it is unchanged. The
  `SqlParameterSource` overloads now live only on the pinned `SqlExecutor` inside a scope.

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

First release published to Maven Central (group `io.github.kormium`), with artifacts
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

[0.2.0]: https://github.com/kormium/kormium/releases/tag/v0.2.0
[0.1.0]: https://github.com/kormium/kormium/releases/tag/v0.1.0
