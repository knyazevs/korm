package io.github.knyazevs.korm.samples.sqlitecache

import io.github.knyazevs.korm.autocommit
import io.github.knyazevs.korm.createSqliteDatabase
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.database.createDatabase
import io.github.knyazevs.korm.transaction
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Verifies the read-through cache against a real Postgres (Testcontainers) + in-memory SQLite. */
class SqliteCacheTest {

    @Test
    fun readThroughHitMissPopulate() {
        if (!DockerClientFactory.instance().isDockerAvailable) return // skip without Docker

        val pg = PostgreSQLContainer("postgres:16-alpine").apply { start() }
        try {
            val pgDb: Database<PgCatalog> = createDatabase(
                host = pg.host,
                port = pg.firstMappedPort,
                database = pg.databaseName,
                user = pg.username,
                password = pg.password,
            )
            val cache: Database<CacheCatalog> = createSqliteDatabase()

            pgDb.use {
                cache.use {
                    pgDb.transaction {
                        PgProducts.execSql("DROP TABLE IF EXISTS \"products\"")
                        PgProducts.execSql(pgProductsDdl)
                        PgProducts.insert(PgProduct().apply { id = 1; name = "Keyboard" })
                    }
                    cache.autocommit { CachedProducts.execSql(cachedProductsDdl) }

                    val repo = ProductRepository(pgDb, cache)

                    assertEquals(0L, cache.autocommit { CachedProducts.count() }, "cache starts empty")
                    assertEquals("Keyboard", repo.get(1)?.name, "miss -> read from PG")
                    assertEquals(1L, cache.autocommit { CachedProducts.count() }, "miss populated the cache")
                    assertEquals("Keyboard", repo.get(1)?.name, "second read served")
                    assertNull(repo.get(99), "absent everywhere")
                }
            }
        } finally {
            pg.stop()
        }
    }
}
