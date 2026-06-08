package io.github.knyazevs.korm.samples.sqlitecache

import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.Column
import io.github.knyazevs.korm.Entity
import io.github.knyazevs.korm.Table
import io.github.knyazevs.korm.autocommit
import io.github.knyazevs.korm.createSqliteDatabase
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.database.createDatabase
import io.github.knyazevs.korm.transaction

// Two catalogs: the same "products" shape lives in Postgres (source of truth) and in a
// local SQLite cache. Each catalog has its own table + entity, tagged so they can't be mixed.

object PgCatalog : Catalog
object CacheCatalog : Catalog

/** The platform-agnostic domain object the app actually works with. */
data class Product(val id: Int, val name: String)

object PgProducts : Table<PgCatalog, PgProduct>(Meta("products"), ::PgProduct) {
    val id by Column.Int(primaryKey = true)
    val name by Column.Text()
}

object CachedProducts : Table<CacheCatalog, CacheProduct>(Meta("products"), ::CacheProduct) {
    val id by Column.Int(primaryKey = true)
    val name by Column.Text()
}

class PgProduct(override var fields: MutableMap<String, Any?> = mutableMapOf()) : Entity(fields) {
    var id by PgProducts.id
    var name by PgProducts.name
}

class CacheProduct(override var fields: MutableMap<String, Any?> = mutableMapOf()) : Entity(fields) {
    var id by CachedProducts.id
    var name by CachedProducts.name
}

private fun PgProduct.toProduct() = Product(id!!, name!!)
private fun Product.toCacheRow() = CacheProduct().apply { id = this@toCacheRow.id; name = this@toCacheRow.name }
private fun CacheProduct.toProduct() = Product(id!!, name!!)

/** Read-through cache: look in SQLite first, fall back to Postgres on a miss and populate the cache. */
class ProductRepository(
    private val pg: Database<PgCatalog>,
    private val cache: Database<CacheCatalog>,
) {
    fun get(id: Int): Product? {
        cache.autocommit { CachedProducts.findById(id) }?.let {
            println("cache HIT  $id")
            return it.toProduct()
        }
        val fromPg = pg.autocommit { PgProducts.findById(id) }?.toProduct()
        if (fromPg == null) {
            println("cache MISS $id (not in Postgres)")
            return null
        }
        println("cache MISS $id -> populate from Postgres")
        cache.transaction { CachedProducts.new(fromPg.toCacheRow()) }
        return fromPg
    }
}

fun main() {
    val pg: Database<PgCatalog> = createDatabase(
        host = "localhost",
        port = 5432,
        database = "postgres",
        user = "postgres",
        password = "password",
    )
    val cache: Database<CacheCatalog> = createSqliteDatabase() // in-memory local cache

    pg.use {
        cache.use {
            // Seed Postgres (the source of truth).
            pg.transaction {
                PgProducts.dropTable()
                PgProducts.createTable()
                PgProducts.new(PgProduct().apply { id = 1; name = "Keyboard" })
                PgProducts.new(PgProduct().apply { id = 2; name = "Mouse" })
            }
            cache.autocommit { CachedProducts.createTable() }

            val repo = ProductRepository(pg, cache)
            println("get(1) = ${repo.get(1)?.name}") // MISS -> populate
            println("get(1) = ${repo.get(1)?.name}") // HIT
            println("get(2) = ${repo.get(2)?.name}") // MISS -> populate
            println("get(9) = ${repo.get(9)?.name}") // MISS, absent everywhere
        }
    }
}
