import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.Column
import io.github.knyazevs.korm.Entity
import io.github.knyazevs.korm.Query
import io.github.knyazevs.korm.SqlParameterSource
import io.github.knyazevs.korm.autocommit
import io.github.knyazevs.korm.Table
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.database.createDatabase
import io.github.knyazevs.korm.eq
import io.github.knyazevs.korm.resultset.ResultSet
import io.github.knyazevs.korm.transaction
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlin.uuid.Uuid
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalForeignApi::class)
private fun env(name: String): String? = getenv(name)?.toKString()

private fun nativeDriver(poolSize: Int) = createDatabase(
    host = env("KORM_DB_HOST") ?: "localhost",
    port = env("KORM_DB_PORT")?.toInt() ?: 5432,
    database = env("KORM_DB_NAME") ?: "postgres",
    user = env("KORM_DB_USER") ?: "postgres",
    password = env("KORM_DB_PASSWORD") ?: "password",
    poolSize = poolSize,
)

/**
 * End-to-end test for the native (libpq) driver. Testcontainers is JVM-only, so
 * the connection is taken from KORM_DB_* environment variables; the test is
 * skipped when they are not set. Run via the CI workflow (.github/workflows).
 *
 * Exercises the type-binding path that the JVM driver fixed via
 * stringtype=unspecified: a BigDecimal into a numeric column, plus a camelCase
 * column and a nullable int.
 */
class NativeTableIntegrationTest {

    @Test
    fun testBigDecimalAndCamelCaseRoundTrip() {
        if (env("KORM_DB_HOST") == null) {
            println("KORM_DB_HOST not set — skipping native integration test")
            return
        }

        val id = Uuid.random()
        NativeDatabase.transaction {
        NativeProducts.new(NativeProduct().apply {
            this.id = id
            this.price = BigDecimal.fromInt(100)
            this.qty = 5
            this.displayName = "widget"
            this.note = null
            this.rank = null
        })

        val found = NativeProducts.findById(id)
        assertEquals(id, found?.id)
        assertEquals(5, found?.qty)
        assertEquals("widget", found?.displayName)
        assertNull(found?.note)
        assertNull(found?.rank)
        assertEquals(0, BigDecimal.fromInt(100).compareTo(found?.price!!))

        NativeProducts.deleteWhere(Query(NativeProducts.id eq id.toString()))
        assertNull(NativeProducts.findById(id))
        }
    }

    /**
     * Regression: a failed statement must not destroy the connection. With poolSize=1
     * there is a single pooled connection, so if the error path closed it (the old
     * behaviour — PQfinish on any query error) the following valid query would fail.
     */
    @Test
    fun testConnectionSurvivesQueryError() {
        if (env("KORM_DB_HOST") == null) {
            println("KORM_DB_HOST not set — skipping native integration test")
            return
        }
        nativeDriver(poolSize = 1).use { driver ->
            assertFailsWith<Exception> {
                driver.execute("SELECT * FROM table_that_does_not_exist") { rs -> rs.getInt(0) }
            }
            // Same single connection must still be usable after the error above.
            assertEquals(1, driver.execute("SELECT 1") { rs -> rs.getInt(0) }.single())
        }
    }

    /** Using the driver after close() must fail instead of touching a finished connection. */
    @Test
    fun testUseAfterCloseThrows() {
        if (env("KORM_DB_HOST") == null) {
            println("KORM_DB_HOST not set — skipping native integration test")
            return
        }
        val driver = nativeDriver(poolSize = 1)
        driver.close()
        assertFailsWith<Exception> {
            driver.execute("SELECT 1") { rs -> rs.getInt(0) }
        }
    }

    /** An exception out of a transaction block must ROLLBACK every statement in it. */
    @Test
    fun testTransactionRollsBackOnException() {
        if (env("KORM_DB_HOST") == null) {
            println("KORM_DB_HOST not set — skipping native integration test")
            return
        }
        val id = Uuid.random()
        assertFailsWith<RuntimeException> {
            NativeDatabase.transaction {
                NativeProducts.new(NativeProduct().apply {
                    this.id = id
                    this.price = BigDecimal.fromInt(1)
                    this.qty = 1
                    this.displayName = "rollback"
                    this.note = null
                    this.rank = null
                })
                throw RuntimeException("boom")
            }
        }
        assertNull(NativeDatabase.autocommit { NativeProducts.findById(id) })
    }
}

class NativeProduct(override var fields: MutableMap<String, Any?> = mutableMapOf()) : Entity(fields) {
    var id by NativeProducts.id
    var price by NativeProducts.price
    var qty by NativeProducts.qty
    var displayName by NativeProducts.displayName
    var note by NativeProducts.note
    var rank by NativeProducts.rank
}

object NativeCatalog : Catalog

object NativeProducts : Table<NativeCatalog, NativeProduct>(Table.Meta("native_products"), ::NativeProduct) {
    val id by Column.UUID()
    val price by Column.BigDecimal()
    val qty by Column.Int()
    val displayName by Column.Text()
    val note by Column.Text(true)
    val rank by Column.Int(true)

    init {
        id;price;qty;displayName;note;rank
    }
}

object NativeDatabase : Database<NativeCatalog> {
    private val driver = createDatabase(
        host = env("KORM_DB_HOST") ?: "localhost",
        port = env("KORM_DB_PORT")?.toInt() ?: 5432,
        database = env("KORM_DB_NAME") ?: "postgres",
        user = env("KORM_DB_USER") ?: "postgres",
        password = env("KORM_DB_PASSWORD") ?: "password",
    )

    init {
        driver.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS public.native_products (
                id uuid PRIMARY KEY,
                price numeric NOT NULL,
                qty int NOT NULL,
                "displayName" text NOT NULL,
                note text,
                rank int
            )
            """.trimIndent()
        )
    }

    override val dialect get() = driver.dialect
    override val typeMapper get() = driver.typeMapper

    override fun <R> usePinned(transactional: Boolean, block: (io.github.knyazevs.korm.SqlExecutor) -> R): R =
        driver.usePinned(transactional, block)

    override fun <T> execute(sql: String, namedParameters: Map<String, Any?>, handler: (ResultSet) -> T): List<T> =
        driver.execute(sql, namedParameters, handler)

    override fun <T> execute(sql: String, paramSource: SqlParameterSource, handler: (ResultSet) -> T): List<T> =
        driver.execute(sql, paramSource, handler)

    override fun execute(sql: String, namedParameters: Map<String, Any?>): Long =
        driver.execute(sql, namedParameters)

    override fun execute(sql: String, paramSource: SqlParameterSource): Long =
        driver.execute(sql, paramSource)

    override fun executeUpdate(sql: String, namedParameters: Map<String, Any?>) =
        driver.executeUpdate(sql, namedParameters)
}
