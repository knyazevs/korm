package io.github.knyazevs.korm

import io.github.knyazevs.korm.database.Database

/**
 * A named, ordered schema change. [id] must be unique and stable (it is the key recorded
 * once the migration is applied); migrations are applied in list order. [up] runs inside a
 * transaction scope, so it can mix table operations and raw SQL.
 */
class Migration<G : Catalog>(val id: String, val up: Scope<G>.() -> Unit)

/**
 * Applies every migration in [migrations] whose [Migration.id] is not yet recorded in the
 * `korm_migrations` table, in list order, each in its own transaction (so applied ids are
 * recorded as progress is made). Running the same list again is a no-op — already-applied
 * migrations are skipped, which makes it safe to call on every startup.
 */
fun <G : Catalog> Database<G>.migrate(migrations: List<Migration<G>>) {
    autocommit {
        executeUpdate("CREATE TABLE IF NOT EXISTS korm_migrations (id text NOT NULL PRIMARY KEY)")
    }
    val applied: Set<String> = autocommit {
        execute("SELECT id FROM korm_migrations") { rs -> rs.getString(0) }
    }.filterNotNull().toSet()

    for (migration in migrations) {
        if (migration.id in applied) continue
        transaction {
            migration.up(this)
            executeUpdate("INSERT INTO korm_migrations (id) VALUES (:id)", mapOf("id" to migration.id))
        }
    }
}
