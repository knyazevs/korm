package io.github.knyazevs.korm.ktor

import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.Scope
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.suspendAutocommit
import io.github.knyazevs.korm.suspendTransaction
import io.ktor.server.application.ApplicationCall

/**
 * Runs [block] in a transaction on [db] (BEGIN/COMMIT, ROLLBACK on throw), suspending the
 * calling coroutine while the blocking driver works (see [suspendTransaction]).
 *
 * This overload is DI-agnostic: you pass the database explicitly, so it works with any way of
 * obtaining it — Koin (`call.transaction(call.get()) { ... }`), Ktor's built-in DI, or a plain
 * reference. The `korm-ktor-di` / `korm-ktor-koin` artifacts add reified overloads that resolve
 * the database for you.
 */
suspend fun <G : Catalog, R> ApplicationCall.transaction(
    db: Database<G>,
    block: Scope<G>.() -> R,
): R = db.suspendTransaction(block)

/**
 * Runs [block] on [db] in autocommit (no surrounding transaction) — the cheap path for reads /
 * single statements. See [transaction] for the DI-agnostic rationale.
 */
suspend fun <G : Catalog, R> ApplicationCall.autocommit(
    db: Database<G>,
    block: Scope<G>.() -> R,
): R = db.suspendAutocommit(block)
