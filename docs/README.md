# Korm Documentation

This directory is the long-form documentation for Korm. The root [README](../readme.md)
is intentionally short; the deeper material lives here.

## Reading Path

1. [Installation](installation.md) - artifacts, BOM, native requirements and supported
   targets.
2. [Quick start](quick-start.md) - a complete first table, connection and CRUD flow.
3. [Tables and entities](tables-and-entities.md) - catalogs, columns, entities and schema
   generation.
4. [Queries](queries.md) - predicates, ordering, pagination, joins, projections and
   aggregations.
5. [Transactions and migrations](transactions-and-migrations.md) - scopes, savepoints,
   suspend API, async backends and migrations.
6. [Backends](backends.md) - PostgreSQL, SQLite, r2dbc and platform support.
7. [Ktor integration](ktor.md) - DI-agnostic, Ktor DI and Koin helpers.
8. [API cookbook](api-cookbook.md) - copy-pasteable recipes for common tasks.
9. [API ergonomics review](api-ergonomics.md) - sharp edges and pre-1.0 API decisions.
10. [Observability](observability.md) - target logging, metrics and failure visibility.
11. [Production guide](production-guide.md) - conservative guidance for real services.
12. [Compatibility policy](compatibility.md) - supported versions, targets and API policy.
13. [Design](design.md) - internal architecture and extension points.
14. [Roadmap](roadmap.md) - current baseline, pre-1.0 hardening and future work.
15. [Project guide](project.md) - samples, benchmarks, testing and contribution notes.

## API Surface

Korm is split into small artifacts:

| Module | Purpose |
| --- | --- |
| `korm-bom` | Bill of Materials for version alignment |
| `korm-core` | Backend-agnostic DSL, scopes, transactions and migrations |
| `korm-postgres` | PostgreSQL dialect and drivers: JDBC/HikariCP on JVM, libpq on Native |
| `korm-sqlite` | SQLite dialect and drivers: sqlite-jdbc, sqlite3 and AndroidX SQLite |
| `korm-r2dbc` | True async PostgreSQL on JVM, suspend API only |
| `korm-jdbc` | Internal/shared JVM JDBC plumbing used by JVM drivers |
| `korm-ktor` | Ktor helpers without a DI dependency |
| `korm-ktor-di` | Ktor built-in DI integration |
| `korm-ktor-koin` | Koin integration for Ktor |

## Current Status

Korm is pre-1.0. The API is already useful and tested, but not frozen. Documentation should
prefer honest precision over marketing claims: describe what is shipped, what is tested in
CI and what is planned separately.

## Maintenance Notes

- Add user-facing examples to [API cookbook](api-cookbook.md).
- Track public API sharp edges in [API ergonomics review](api-ergonomics.md).
- Keep production-facing guidance in [Production guide](production-guide.md) and
  [Observability](observability.md).
- Update [Compatibility policy](compatibility.md) when versions or targets change.
- Add architecture and contributor context to [Design](design.md).
- Add planned work to [Roadmap](roadmap.md), keeping shipped and planned behavior separate.
