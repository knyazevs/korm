package io.github.knyazevs.korm.ktor.koin

import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.Scope
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.ktor.KormHandle
import io.github.knyazevs.korm.suspendAutocommit
import io.github.knyazevs.korm.suspendTransaction
import io.ktor.server.application.ApplicationCall
import org.koin.core.qualifier.Qualifier
import org.koin.ktor.ext.getKoin

/**
 * Resolves the [Database] for catalog [G] from Koin, wrapped in a [KormHandle] — the chain form
 * (c): `call.korm<AppCatalog>().transaction { ... }`.
 *
 * Note: Koin keys by `KClass`, so generics are erased — `get<Database<AppCatalog>>()` and
 * `get<Database<OtherCatalog>>()` resolve to the same key. If you run more than one catalog,
 * register and resolve them with a [qualifier]:
 * ```
 * single<Database<AppCatalog>>(named("app")) { createDatabase(...) }
 * // call.korm<AppCatalog>(named("app"))
 * ```
 */
inline fun <reified G : Catalog> ApplicationCall.korm(qualifier: Qualifier? = null): KormHandle<G> =
    KormHandle(getKoin().get(qualifier = qualifier))

// --- (a) catalog as a TYPE argument — `call.transaction<AppCatalog, _> { ... }` ---------------
// The `_` lets the return type infer while the catalog is given explicitly as a type.
// For a named dependency use the value form (b) or `korm<G>(qualifier).transaction { }`.

suspend inline fun <reified G : Catalog, R> ApplicationCall.transaction(
    noinline block: Scope<G>.() -> R,
): R = korm<G>().database.suspendTransaction(block)

suspend inline fun <reified G : Catalog, R> ApplicationCall.autocommit(
    noinline block: Scope<G>.() -> R,
): R = korm<G>().database.suspendAutocommit(block)

// --- (b) catalog as a VALUE — `call.transaction(AppCatalog) { ... }` --------------------------
// Both type parameters infer; pass a [qualifier] for a named dependency.

suspend inline fun <reified G : Catalog, R> ApplicationCall.transaction(
    catalog: G,
    qualifier: Qualifier? = null,
    noinline block: Scope<G>.() -> R,
): R = korm<G>(qualifier).database.suspendTransaction(block)

suspend inline fun <reified G : Catalog, R> ApplicationCall.autocommit(
    catalog: G,
    qualifier: Qualifier? = null,
    noinline block: Scope<G>.() -> R,
): R = korm<G>(qualifier).database.suspendAutocommit(block)
