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
./gradlew :korm-core:jvmTest
./gradlew :korm-sqlite:jvmTest
./gradlew :korm-postgres:jvmTest
./gradlew :korm-r2dbc:jvmTest
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

The `benchmarks` module contains JMH benchmarks comparing Korm, Exposed and Hibernate on
JVM:

```bash
./gradlew :benchmarks:jmh
```

There is also a native Korm benchmark harness. Configure PostgreSQL with environment
variables:

```bash
export KORM_DB_HOST=localhost
export KORM_DB_PORT=5432
export KORM_DB_NAME=postgres
export KORM_DB_USER=postgres
export KORM_DB_PASSWORD=password
```

Indicative throughput from the current README-era benchmark run, in ops/s with 8
threads/workers:

| Operation | Korm JVM | Korm Native | Exposed | Hibernate |
| --- | ---: | ---: | ---: | ---: |
| `findById` | ~8.2k | ~13.2k | ~8.2k | ~16.0k |
| `selectWhere` | ~8.2k | ~13.7k | ~7.9k | ~16.3k |
| `insert` | ~8.0k | ~5.0k | ~7.9k | ~8.3k |

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
