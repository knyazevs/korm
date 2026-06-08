package io.github.knyazevs.korm.r2dbc

import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.Column
import io.github.knyazevs.korm.Entity
import io.github.knyazevs.korm.Query
import io.github.knyazevs.korm.Table
import io.github.knyazevs.korm.UniqueViolationException
import io.github.knyazevs.korm.count
import io.github.knyazevs.korm.database.SuspendDatabase
import io.github.knyazevs.korm.eq
import io.github.knyazevs.korm.suspendAutocommit
import io.github.knyazevs.korm.suspendTransaction
import kotlinx.coroutines.runBlocking
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * End-to-end test of the async r2dbc backend against a real Postgres (Testcontainers).
 * Exercises suspendTransaction/suspendAutocommit → R2dbcDatabase.useConnection (no
 * offload, pure reactive→suspend) over the same Table DSL the JDBC/libpq backends use.
 * Skips gracefully when Docker isn't available.
 */
class R2dbcIntegrationTest {

    private val dockerAvailable = DockerClientFactory.instance().isDockerAvailable
    private var container: PostgreSQLContainer<*>? = null
    private var db: SuspendDatabase<R2Catalog>? = null

    @BeforeTest
    fun setUp() {
        if (!dockerAvailable) return
        val pg = PostgreSQLContainer("postgres:16-alpine")
        pg.start()
        container = pg
        db = createR2dbcDatabase(
            host = pg.host,
            port = pg.firstMappedPort,
            database = pg.databaseName,
            user = pg.username,
            password = pg.password,
            poolSize = 4,
        )
    }

    @AfterTest
    fun tearDown() {
        db?.close()
        container?.stop()
    }

    @Test
    fun crudRoundTrip() {
        if (!dockerAvailable) return
        val database = db!!
        runBlocking {
            val id = Uuid.random()
            database.suspendTransaction {
                Widgets.createTable()
                Widgets.new(Widget().apply {
                    this.id = id
                    this.name = "async-widget"
                    this.qty = 7
                })
            }

            val found = database.suspendAutocommit { Widgets.findById(id) }
            assertEquals(id, found?.id)
            assertEquals("async-widget", found?.name)
            assertEquals(7, found?.qty)

            val byName = database.suspendAutocommit { Widgets.find(Query(Widgets.name eq "async-widget")) }
            assertEquals(1, byName.size)

            assertTrue(database.suspendAutocommit { Widgets.count() } >= 1)
        }
    }

    @Test
    fun transactionRollsBackOnThrow() {
        if (!dockerAvailable) return
        val database = db!!
        runBlocking {
            database.suspendTransaction { Widgets.createTable() }
            val id = Uuid.random()

            assertFailsWith<IllegalStateException> {
                database.suspendTransaction {
                    Widgets.new(Widget().apply {
                        this.id = id
                        this.name = "doomed"
                        this.qty = 1
                    })
                    error("boom")
                }
            }

            assertNull(database.suspendAutocommit { Widgets.findById(id) })
        }
    }

    @Test
    fun typedUniqueViolation() {
        if (!dockerAvailable) return
        val database = db!!
        runBlocking {
            database.suspendTransaction { Widgets.createTable() }
            val id = Uuid.random()
            database.suspendTransaction {
                Widgets.new(Widget().apply { this.id = id; this.name = "dup"; this.qty = 1 })
            }
            assertFailsWith<UniqueViolationException> {
                database.suspendTransaction {
                    Widgets.new(Widget().apply { this.id = id; this.name = "dup2"; this.qty = 2 })
                }
            }
        }
    }
}

object R2Catalog : Catalog

class Widget : Entity() {
    var id by Widgets.id
    var name by Widgets.name
    var qty by Widgets.qty
}

object Widgets : Table<R2Catalog, Widget>("widgets", ::Widget) {
    val id by Column.UUID().primaryKey()
    val name by Column.Text()
    val qty by Column.Int()

    init { id; name; qty }
}
