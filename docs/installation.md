# Installation

Korm is published to Maven Central under the group `io.github.knyazevs.korm`.

The recommended Gradle setup is to import `korm-bom` once and then declare artifacts
without versions:

```kotlin
dependencies {
    implementation(platform("io.github.knyazevs.korm:korm-bom:<version>"))

    implementation("io.github.knyazevs.korm:korm-postgres")
    // or:
    implementation("io.github.knyazevs.korm:korm-sqlite")
    // or, JVM-only true async PostgreSQL:
    implementation("io.github.knyazevs.korm:korm-r2dbc")
}
```

Without the BOM, put the same version on every artifact:

```kotlin
dependencies {
    implementation("io.github.knyazevs.korm:korm-postgres:<version>")
    implementation("io.github.knyazevs.korm:korm-ktor:<version>")
}
```

## Requirements

- JDK 21 or newer for JVM builds.
- Kotlin Multiplatform project setup if you use Native, Android or iOS targets.
- PostgreSQL client libraries for the Native PostgreSQL driver.
- SQLite headers for Native SQLite on Linux if your distribution does not install them by
  default.

## Artifacts

| Artifact | Add when |
| --- | --- |
| `korm-core` | You implement a custom backend or only need the common DSL types |
| `korm-postgres` | You use PostgreSQL through JDBC on JVM or libpq on Native |
| `korm-sqlite` | You use SQLite on JVM, Native or Android |
| `korm-r2dbc` | You want non-blocking PostgreSQL on JVM |
| `korm-ktor` | You want explicit database passing in Ktor routes |
| `korm-ktor-di` | You use Ktor's built-in DI container |
| `korm-ktor-koin` | You use Koin in a Ktor application |

Backend artifacts bring `korm-core` transitively.

## Native Libraries

### PostgreSQL Native

`korm-postgres` links against `libpq` on Kotlin/Native.

```bash
# macOS
brew install libpq

# Debian / Ubuntu
sudo apt-get install libpq-dev
```

Windows Native is planned but not shipped yet.

### SQLite Native

`korm-sqlite` links against `sqlite3` on Kotlin/Native. macOS and iOS ship SQLite. On
Debian/Ubuntu install headers if missing:

```bash
sudo apt-get install libsqlite3-dev
```

## Platform Matrix

| Platform | `korm-core` | `korm-postgres` | `korm-sqlite` | `korm-r2dbc` | Ktor modules |
| --- | --- | --- | --- | --- | --- |
| JVM | Yes | JDBC/HikariCP | sqlite-jdbc | Yes | Yes |
| Linux Native | Yes | libpq | sqlite3 | No | Yes |
| macOS Native | Yes | libpq | sqlite3 | No | Yes |
| Android | Yes | No | AndroidX SQLite | No | Compiles |
| iOS | Yes | No | sqlite3 | No | Compiles |
| Windows Native | Planned | Planned | Planned | No | Planned |
| Wasm | Research | No | Planned | No | No |

The CI workflow builds JVM/Linux Native tests on Ubuntu and Android/iOS compilation on
macOS. Local `./gradlew check` may try host-specific native simulator tests; use the focused
commands in [Project guide](project.md#testing) when your local SDK does not provide every
target.
