# API Ergonomics Review

This page tracks the API work needed before Korm can be recommended confidently to teams
that did not build it. It is not only about nicer names; it is about avoiding surprising
runtime behavior and making compiler errors useful.

## Goals

- Keep common use cases short and readable.
- Make dangerous operations visually obvious.
- Preserve catalog safety and typed predicates.
- Avoid APIs that look stable while their behavior is still underdefined.
- Make examples compile with minimal imports and setup.

## Current Strong Points

- Catalog tags prevent using tables with the wrong database handle.
- Predicates are typed and parameterized by default.
- Blocking and suspend scopes have the same table DSL.
- Partial update semantics are useful: absent fields are omitted, assigned `null` writes
  SQL `NULL`.
- Backend factories are small and easy to discover.

## Sharp Edges

### Column Registration

Column registration is automatic through Kotlin delegated-property `provideDelegate`.
Canonical table definitions do not need a manual registration block:

```kotlin
object Users : Table<App, User>(Meta("users"), ::User) {
    val id by Column.UUID(primaryKey = true)
    val name by Column.Text()
    val age by Column.Int()
}
```

The old `init { id; name; age }` style remains source-compatible because reading the
property now returns the already registered column.

Remaining questions:

- whether to keep documenting the old style anywhere;
- whether to detect duplicate/manual registrations defensively;
- whether custom column names should be restored through `@ColumnName` or another API.

Acceptance criteria:

- forgetting registration should be impossible;
- docs should show one recommended style only;
- tests should cover automatic registration and declaration order.

### Entity Construction

Current entities expose `fields`:

```kotlin
class User(
    override var fields: MutableMap<String, Any?> = mutableMapOf(),
) : Entity(fields)
```

This is flexible but low-level. New users may not understand why their domain object owns a
map.

Possible directions:

- keep the map-based entity but improve docs and generated examples;
- provide a base constructor helper;
- provide optional factory helpers for common cases;
- consider a later codegen/plugin story only if manual entities remain too noisy.

Acceptance criteria:

- first example should explain `fields` in one sentence;
- docs should distinguish "new empty entity for writes" from "mapped entity from reads";
- update semantics should be tested and documented with absent vs explicit null.

### Query Constructor Shape

This is compact but not always self-explanatory:

```kotlin
Query(Users.age gtEq 18, limit = 50u)
```

The first positional argument is `whereExpression`. Examples should prefer named arguments
when clarity matters:

```kotlin
Query(whereExpression = Users.age gtEq 18, limit = 50u)
```

Possible directions:

- add `where(...)` helpers or a small query builder;
- keep `Query` as data class but make docs use named arguments;
- add examples for empty query, ordering and pagination.

Acceptance criteria:

- no ambiguous positional `Query(...)` usage in top-level docs;
- compiler errors around `UInt` limit/offset are not confusing in examples.

### Raw SQL

Raw SQL is necessary for indexes, constraints and backend-specific features. It is also the
easiest way to bypass parameterization.

Current raw surfaces:

- `Scope.execute(sql, params)`;
- `Scope.executeUpdate(sql, params)`;
- `RawExpression`.

Possible directions:

- document `RawExpression` as unsafe unless input is fully controlled;
- prefer raw statement execution with parameter maps over raw expressions;
- consider naming that makes unsafe expression embedding explicit.

Acceptance criteria:

- cookbook examples never concatenate user input;
- raw SQL docs always show parameter maps for values;
- unsafe raw expression use is documented as an escape hatch.

### Blocking vs Suspend Naming

Blocking:

```kotlin
db.transaction { }
db.autocommit { }
```

Suspend:

```kotlin
db.suspendTransaction { }
db.suspendAutocommit { }
```

This is explicit, but Ktor helpers use `call.transaction(...)` even though they are suspend.
That is acceptable if docs consistently explain that Ktor helpers delegate to
`SuspendDatabase`.

Acceptance criteria:

- each integration doc states whether the helper is blocking or suspend;
- r2dbc docs never imply a blocking `Database` exists;
- examples in route handlers use `SuspendDatabase`.

## Review Checklist for Public API Changes

Before changing a public API, answer:

- Does the API preserve catalog safety?
- Does it work on JVM and Native?
- Does it have a suspend story?
- Can it be documented in one example?
- What happens on null values?
- Does it leak backend-specific behavior into common code?
- Is there a migration path from the old API?
- Is the error message useful when the user does the obvious wrong thing?

## Pre-1.0 Action Items

- Review top-level docs for positional `Query(...)` usage.
- Add a dedicated raw SQL guide with safe and unsafe examples.
- Add more tests for automatic column registration across backend modules.
- Add "API stability candidates" section to the roadmap once decisions are made.
