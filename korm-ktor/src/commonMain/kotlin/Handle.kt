package io.github.kormium.ktor

import io.github.kormium.Catalog
import io.github.kormium.SuspendScope
import io.github.kormium.database.SuspendDatabase
import io.github.kormium.suspendAutocommit
import io.github.kormium.suspendTransaction
import kotlin.jvm.JvmInline

/**
 * An allocation-free wrapper around a resolved [SuspendDatabase] that offers terse [transaction] /
 * [autocommit] without a second type argument. It's the chain form (c) of the DI helpers:
 * `call.korm<AppCatalog>().transaction { ... }` keeps the catalog a pure type (no value, no `_`)
 * at the cost of one extra `.korm<G>()` hop. Built by the `korm-ktor-di` / `korm-ktor-koin`
 * `korm<G>()` accessors.
 */
@JvmInline
value class KormHandle<G : Catalog>(val database: SuspendDatabase<G>)

/** Runs [block] in a transaction on the wrapped database; see [io.github.kormium.suspendTransaction]. */
suspend fun <G : Catalog, R> KormHandle<G>.transaction(block: suspend SuspendScope<G>.() -> R): R =
    database.suspendTransaction(block)

/** Runs [block] in autocommit on the wrapped database; see [io.github.kormium.suspendAutocommit]. */
suspend fun <G : Catalog, R> KormHandle<G>.autocommit(block: suspend SuspendScope<G>.() -> R): R =
    database.suspendAutocommit(block)
