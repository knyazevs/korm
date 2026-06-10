# korm-bom

The Bill of Materials for [Kormium](../readme.md). Import it once and omit the version on every
other Kormium artifact — the BOM pins them all to a single, consistent release.

## Install

```kotlin
dependencies {
    implementation(platform("io.github.kormium:korm-bom:<version>"))

    // versions come from the BOM
    implementation("io.github.kormium:korm-postgres")
    implementation("io.github.kormium:korm-ktor")
    implementation("io.github.kormium:korm-observe")
}
```

## Managed artifacts

`korm-core`, `korm-postgres`, `korm-jdbc`, `korm-sqlite`, `korm-migrate`, `korm-ktor`,
`korm-ktor-di`, `korm-ktor-koin`.

> `korm-r2dbc` is not yet pinned by the BOM — give it an explicit version for now.

## Documentation

- [Installation](../docs/installation.md)
