# Project Guide

This page covers samples, benchmarks, local verification and contribution notes.

## Samples

Runnable samples live under `samples/`.

| Sample | Shows | Run |
| --- | --- | --- |
| `samples:crud-sqlite` | Standalone SQLite CRUD and migrations | `./gradlew :samples:crud-sqlite:runJvm` |
| `samples:sharding` | Catalog safety and sharding one catalog across database instances | `./gradlew :samples:sharding:runJvm` |
| `samples:sqlite-cache` | SQLite as a read-through cache in front of PostgreSQL | `./gradlew :samples:sqlite-cache:runJvm` |
| `samples:ktor-di` | Ktor CRUD over PostgreSQL with built-in DI | `./gradlew :samples:ktor-di:runJvm` |
| `samples:ktor-koin` | Ktor CRUD over PostgreSQL with Koin | `./gradlew :samples:ktor-koin:runJvm` |
| `samples:r2dbc` | Ktor CRUD over async r2dbc PostgreSQL | `./gradlew :samples:r2dbc:runJvm` |

SQLite samples are self-contained. PostgreSQL samples expect a database on
`localhost:5432` with user/password `postgres`/`password`; each relevant sample includes a
matching `docker-compose.yml`.

## Testing

Useful local checks:

```bash
./gradlew :kormium-core:jvmTest
./gradlew :kormium-sqlite:jvmTest
./gradlew :kormium-postgres:jvmTest
./gradlew :kormium-r2dbc:jvmTest
```

CI runs a broader matrix:

- JVM tests for core and SQLite.
- Linux Native tests for core, PostgreSQL and SQLite.
- Android and iOS compilation on macOS.
- Ktor integration module assembly.
- Sample tests.
- r2dbc and PostgreSQL sample tests with Testcontainers.

On a local machine, plain `./gradlew check` may include native simulator tasks your SDK does
not support. Prefer focused tasks unless you are validating the full host-specific matrix.

## Benchmarks

The `benchmarks` module measures the full matrix — Kormium JVM, Kormium Native (libpq),
Exposed and Hibernate — over six operations (`findById`, `selectWhere`, `selectMany`,
`insert`, `batchInsert`, `updateById`) against one shared PostgreSQL:

```bash
./benchmarks/run.sh            # full matrix, prints a merged summary table
./gradlew :benchmarks:jmh      # JVM ORMs only
```

See [benchmarks/README.md](../benchmarks/README.md) for flags, output files, what each
operation does and the methodology (durability-off PostgreSQL on tmpfs, per-iteration
reseeding, JMH forks).

Indicative throughput from a `run.sh` pass on a dev machine, in ops/s with 8
threads/workers (the native column comes from a simpler non-JMH harness):

| Operation | Kormium JVM | Kormium Native | Exposed | Hibernate |
| --- | ---: | ---: | ---: | ---: |
| `findById` | ~26.1k | ~30.6k | ~11.2k | ~26.3k |
| `selectWhere` | ~26.4k | ~29.7k | ~10.7k | ~23.9k |
| `selectMany` | ~19.9k | ~17.3k | ~9.8k | ~13.7k |
| `insert` | ~12.2k | ~11.3k | ~11.8k | ~12.4k |
| `batchInsert` | ~4.7k | ~5.4k | ~4.9k | ~4.2k |
| `updateById` | ~11.6k | ~11.3k | ~11.1k | ~10.7k |

Treat benchmark numbers as relative. Run them on your own machine and database before making
architecture decisions.

## Contributing

Good contributions include:

- focused bug fixes with regression tests;
- backend compatibility fixes;
- documentation examples that compile against the current API;
- benchmarks that explain a real tradeoff;
- small API improvements that keep multiplatform constraints explicit.

Before a PR, run the focused tests for the touched modules. If your change affects native
code, also run the relevant host native task or rely on CI for unsupported targets.

For larger changes, check the relevant planning docs first:

- [API ergonomics review](api-ergonomics.md) for public API changes;
- [Design](design.md) for backend/core architecture;
- [Compatibility policy](compatibility.md) for version and platform claims;
- [Observability](observability.md) for logging, metrics and failure visibility.
