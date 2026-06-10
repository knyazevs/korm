# Roadmap

Kormium is pre-1.0. The roadmap is intentionally explicit about what is shipped, what is
being hardened and what is still exploratory. Items here are not promises for a specific
release date; they are the direction of the project.

## Current Baseline

Shipped today:

- backend-agnostic Kotlin Multiplatform core;
- PostgreSQL on JVM through JDBC/HikariCP;
- PostgreSQL on Kotlin/Native through libpq;
- SQLite on JVM, Kotlin/Native and Android;
- JVM-only async PostgreSQL through r2dbc;
- typed table/entity DSL;
- an open `ColumnType` system (built-ins + `enum`/`json` + custom converters);
- typed predicates, joins and aggregations;
- transactions, savepoints, suspend scopes and migrations;
- reactive `Flow` queries (`kormium-observe`) over a `WriteListener` commit hook;
- Ktor integration for explicit database passing, Ktor DI and Koin;
- Maven Central publishing with a BOM.

## Before 1.0

The main goal before 1.0 is boring reliability: fewer surprising edge cases, stronger docs
and a smaller chance that public API has to move later.

### API Hardening

- Review naming consistency across blocking and suspend APIs.
- Decide which APIs are stable enough to keep source-compatible after 1.0.
- Make raw SQL extension points clearer and safer.
- Improve compiler errors where catalog/type inference currently produces noisy messages.
- Keep the entity model small unless a new abstraction removes real complexity.
- Work through the sharp edges tracked in [API ergonomics review](api-ergonomics.md).

### Query Coverage

- Expand tests for joins with colliding column names.
- Add more aggregation and `HAVING` coverage.
- Improve examples for pagination, projections and nullable left joins.
- Consider richer update/delete result reporting.
- Document unsupported SQL features explicitly instead of implying full SQL coverage.

### Schema and Migrations

- Add documented recipes for foreign keys, indexes and custom constraints through raw SQL.
- Consider first-class index/foreign-key metadata only after the raw SQL workflow is stable.
- Harden migration tests across PostgreSQL and SQLite.
- Add guidance for production migration review and rollback strategy.

### Backend Reliability

- Keep PostgreSQL JVM, PostgreSQL Native and SQLite behavior aligned where practical.
- Expand SQLSTATE/error mapping tests.
- Improve Native driver observability and failure messages.
- Investigate statement caching and prepare/execute paths where benchmarks show real wins.

### Documentation

- Keep README short and honest.
- Keep `docs/` deep enough that users can build a real application without reading source.
- Add more copy-pasteable recipes to [API cookbook](api-cookbook.md).
- Maintain [Production guide](production-guide.md), [Observability](observability.md) and
  [Compatibility policy](compatibility.md) as the production-readiness contract.
- Add architecture diagrams once the public shape settles.
- Add a documentation verification task to CI.

## After 1.0

After 1.0 the project should optimize for compatibility and ecosystem fit:

- stronger source/binary compatibility guarantees;
- a clearer deprecation policy;
- more backend-specific tuning guides;
- richer integrations for application frameworks where there is real demand;
- production migration patterns;
- more benchmark scenarios and published methodology.

## Exploratory Areas

These are useful but should not distract from core reliability:

- Windows Native targets;
- Wasm and browser-adjacent storage stories;
- more SQL dialects;
- richer schema DSL;
- generated entities or compiler plugin support;
- advanced query planner hints and backend-specific SQL features.

## Non-Goals for Now

- Hiding SQL completely. Kormium is an ORM/SQL DSL, not an attempt to make relational storage
  invisible.
- Reimplementing a full SQL parser.
- Adding every backend before the existing ones are boring.
- Building a heavy runtime metadata system if Kotlin types already express the constraint.

## How to Use This Roadmap

For issues and PRs, tie proposed work to one of these buckets:

- reliability bug;
- API hardening;
- backend compatibility;
- docs gap;
- benchmark-backed performance work;
- exploratory prototype.

That keeps the project from accumulating unrelated features while the public API is still
pre-1.0.
