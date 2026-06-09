# Observability

Production users need to answer simple questions when something goes wrong:

- What SQL was executed?
- How long did it take?
- Which backend and pool was involved?
- Was the failure a constraint violation, timeout, connection failure or cancellation?
- Can logs be enabled without leaking secrets or personally identifiable data?

Korm does not have a complete observability API yet. This page defines the target behavior
and the minimum contract needed before recommending Korm for production.

## Current State

Today Korm has:

- typed exceptions for common constraint failures;
- backend-specific exception translation for JDBC, r2dbc, SQLite and libpq paths;
- some internal trace logging through kotlin-logging;
- HikariCP underneath JVM JDBC backends, which has its own metrics integrations if the
  application configures them.

Current gaps:

- no public query listener/interceptor;
- no stable slow query logging;
- no built-in parameter redaction policy;
- no common metrics contract across backends;
- no documented pool metrics story;
- no request/correlation context story.

## Principles

### Logs Are for Humans

Logs should help debug incidents. They should not be the only way to collect latency or pool
health.

### Metrics Are for Operations

Metrics should be structured and low-cardinality:

- query duration;
- rows read or affected when known;
- success/failure count;
- pool acquisition timing where the backend exposes it;
- transaction duration;
- rollback count.

Raw SQL text is high-cardinality and should not be used as a metric label.

### Parameters Are Sensitive by Default

Korm should never log bound parameter values by default.

Target redaction levels:

| Level | Behavior |
| --- | --- |
| `none` | Do not log parameters |
| `types` | Log parameter names and Kotlin/backend types |
| `values` | Log values, intended only for local debugging |
| custom | User-provided redactor |

The default should be `none` or `types`, not `values`.

## Target API Shape

A future observability API should be backend-neutral:

```kotlin
interface KormObserver {
    fun onQueryStart(event: QueryStart)
    fun onQuerySuccess(event: QuerySuccess)
    fun onQueryFailure(event: QueryFailure)
    fun onTransactionStart(event: TransactionStart)
    fun onTransactionEnd(event: TransactionEnd)
}
```

Events should carry:

- backend name;
- operation kind: select, insert, update, delete, raw, migration, transaction;
- SQL string or normalized SQL depending on configuration;
- parameter metadata after redaction;
- elapsed time;
- affected row count when known;
- exception type and SQLSTATE/error code on failure.

This does not need to be the final API, but it defines the contract that docs and tests
should eventually cover.

## Slow Query Logging

Target behavior:

```kotlin
createDatabase(
    host = "localhost",
    database = "postgres",
    user = "postgres",
    password = "password",
    observability = KormObservability {
        slowQueryThreshold = 250.milliseconds
        parameterLogging = ParameterLogging.Types
    },
)
```

Slow query logs should include:

- elapsed time;
- backend;
- operation kind;
- SQL;
- redacted parameter metadata;
- SQLSTATE/error code if failed.

They should not include raw parameter values unless explicitly configured.

## Metrics

Recommended metric names if Korm ships a metrics bridge:

| Metric | Type | Labels |
| --- | --- | --- |
| `korm.query.duration` | timer/histogram | backend, operation, outcome |
| `korm.transaction.duration` | timer/histogram | backend, outcome |
| `korm.query.rows` | distribution/counter | backend, operation |
| `korm.pool.acquire.duration` | timer/histogram | backend |
| `korm.pool.active` | gauge | backend, pool |
| `korm.pool.idle` | gauge | backend, pool |
| `korm.pool.pending` | gauge | backend, pool |

For JVM JDBC, HikariCP already exposes pool metrics when configured by the application. Korm
should document how to wire that before adding its own duplicate pool gauges.

## Failure Classification

Failures should be easy to classify:

| Failure | Current/target signal |
| --- | --- |
| unique violation | `UniqueViolationException` |
| foreign key violation | `ForeignKeyViolationException` |
| not-null violation | `NotNullViolationException` |
| check violation | `CheckViolationException` |
| other SQL failure | `QueryException` with SQLSTATE/error code when available |
| pool closed | `QueryException` or backend-specific closed connection error |
| cancellation | should preserve coroutine cancellation semantics |
| timeout | should be distinguishable where backend reports it |

## Production Checklist

Before recommending Korm for production, observability should have:

- documented exception taxonomy;
- no parameter values in logs by default;
- slow query logging story;
- query timing hook or metrics bridge;
- pool metrics guide for JDBC/HikariCP;
- examples for Ktor `StatusPages`;
- tests proving observers run on success and failure (the `WriteListener` commit hook now
  exists — see below).

## Write Notification

`Database`/`SuspendDatabase` expose a `writeListeners: WriteListeners` registry. After a
`transaction { }` / `autocommit { }` (or suspend counterpart) commits, every registered
`WriteListener` is called with the set of table names written during it (rolled-back work
notifies nothing). This is a generic, synchronous commit hook — it backs `korm-observe`
([Observing changes](observe.md)) but is equally usable for cache invalidation, audit or
metrics:

```kotlin
db.writeListeners.add { tables -> log.info("committed writes to {}", tables) }
```

Raw SQL declares its tables via the `invalidates` argument on `execute`/`executeUpdate`;
see [Observing changes](observe.md#raw-sql).

## Current Recommendation

For timing and pool metrics (not yet covered by a built-in API):

- rely on typed exceptions for application-level handling;
- configure HikariCP metrics directly for JVM JDBC pool visibility;
- use database-native tools for slow query analysis;
- avoid enabling trace logs in environments where parameter values may contain sensitive
  data;
- wrap Korm calls at your application boundary if you need timings today.
