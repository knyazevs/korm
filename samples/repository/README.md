# Sample: repository

A standalone console app — **no server, no external database** — over **SQLite**, running the
same `commonMain` code on JVM and Kotlin/Native.

Kormium intentionally does **not** ship a `Repository` type (like Exposed, you call table operations
inside `suspendTransaction { }` / `suspendAutocommit { }`). This sample shows the recommended
pattern when you want a Room-style "home" for a table's queries: a small `Repository<G, T>` base
([`Repository.kt`](src/commonMain/kotlin/Repository.kt)) that you **copy into your project** and
adapt — it is ~25 lines and yours to change.

Shows:
- a copyable `Repository` base with `findById` / `all` / `insert` / `deleteWhere` and `observeAll()`;
- a subclass (`UserRepository`) adding a custom query (`adults()`) and a reactive
  `observeAdults(): Flow<List<User>>` via `kormium-observe`;
- a cross-repository **transaction** (`ShopService.register`) wrapping two writes in one
  `suspendTransaction { }` so they commit atomically.

## Run

Run from the repository root. No Docker needed.

```sh
# JVM ...
./gradlew :samples:repository:runJvm
# ... or native
./gradlew :samples:repository:runDebugExecutableNative
```

The run prints a `findById`, the custom `adults()` query, the observed Flow re-emitting after an
insert (`[[Alice], [Alice, Carol]]`), and the order written by the cross-repository transaction.
