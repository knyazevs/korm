import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.github.knyazevs.korm.Column
import io.github.knyazevs.korm.Entity
import io.github.knyazevs.korm.Query
import io.github.knyazevs.korm.SqlParameterSource
import io.github.knyazevs.korm.Table
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.database.createDatabase
import io.github.knyazevs.korm.eq
import io.github.knyazevs.korm.resultset.ResultSet
import kotlin.uuid.Uuid
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * End-to-end tests for the JVM driver against a real Postgres (Testcontainers).
 * Unlike the [TableTest] unit tests (which assert generated SQL against a mock),
 * these exercise the whole stack — named-parameter translation in
 * [io.github.knyazevs.korm.NamedParamStatement], real type binding and
 * [Table.mapToDao] reading rows back.
 *
 * Includes a camelCase column ("displayName") on purpose: it exercises the
 * identifier-quoting fix end-to-end — an unquoted SELECT would fail to find a
 * mixed-case column on a real Postgres.
 */
class TableIntegrationTest {

    @Test
    fun testInsertFindUpdateDeleteRoundTrip() {
        assumeDockerAvailable()
        val id = Uuid.random()
        ItProducts.new(ItProduct().apply {
            this.id = id
            this.price = BigDecimal.fromInt(100)
            this.qty = 5
            this.displayName = "widget"
            this.note = null
            this.rank = null
        })

        val found = ItProducts.findById(id)
        assertEquals(id, found?.id)
        assertEquals(5, found?.qty)
        assertEquals("widget", found?.displayName)
        assertNull(found?.note)
        // Regression: a nullable numeric column that is NULL must read back as null, not 0.
        assertNull(found?.rank)
        assertEquals(0, BigDecimal.fromInt(100).compareTo(found?.price!!))

        // Found via a parameterized WHERE on a value.
        val byQty = ItProducts.find(Query(ItProducts.qty eq "5")).filter { it.id == id }
        assertEquals(1, byQty.size)

        // Partial update: only non-null fields go into SET.
        ItProducts.update(Query(ItProducts.id eq id.toString()), ItProduct().apply { this.qty = 9 })
        assertEquals(9, ItProducts.findById(id)?.qty)

        ItProducts.deleteWhere(Query(ItProducts.id eq id.toString()))
        assertNull(ItProducts.findById(id))
    }

    /**
     * Proves parameterization end-to-end: a value containing a quote (which would
     * break naive string interpolation / enable injection) round-trips intact.
     */
    @Test
    fun testValueWithQuoteRoundTrips() {
        assumeDockerAvailable()
        val id = Uuid.random()
        val tricky = "O'Brien'; DROP TABLE it_products; --"
        ItProducts.new(ItProduct().apply {
            this.id = id
            this.price = BigDecimal.fromInt(1)
            this.qty = 1
            this.displayName = tricky
            this.note = null
        })

        assertEquals(tricky, ItProducts.findById(id)?.displayName)
    }

    /**
     * Regression: [Table.execSql] routes through `execute(sql): Long`, which used
     * to throw (ClassCastException / "no results returned") and could not run DDL.
     */
    @Test
    fun testExecSqlRunsDdl() {
        assumeDockerAvailable()
        ItProducts.execSql("CREATE TABLE IF NOT EXISTS public.exec_sql_probe (id int)")
        ItProducts.execSql("DROP TABLE public.exec_sql_probe")
    }

    /**
     * Regression: connections must be returned to the Hikari pool. With the leak,
     * more than ~10 calls would exhaust the default pool and block until timeout.
     */
    @Test
    fun testConnectionsAreReleasedAcrossManyCalls() {
        assumeDockerAvailable()
        repeat(30) { i ->
            val id = Uuid.random()
            ItProducts.new(ItProduct().apply {
                this.id = id
                this.price = BigDecimal.fromInt(i)
                this.qty = i
                this.displayName = "n$i"
                this.note = null
                this.rank = null
            })
            ItProducts.findById(id)
        }
    }

    /** A failing statement must not break the driver; the next query still runs. */
    @Test
    fun testConnectionSurvivesQueryError() {
        assumeDockerAvailable()
        ItDatabase.newDriver(poolSize = 1).use { driver ->
            assertFailsWith<Exception> {
                driver.execute("SELECT * FROM table_that_does_not_exist") { rs -> rs.getInt(0) }
            }
            assertEquals(1, driver.execute("SELECT 1") { rs -> rs.getInt(0) }.single())
        }
    }

    /** Using the driver after close() must fail. */
    @Test
    fun testUseAfterCloseThrows() {
        assumeDockerAvailable()
        val driver = ItDatabase.newDriver(poolSize = 1)
        driver.close()
        assertFailsWith<Exception> {
            driver.execute("SELECT 1") { rs -> rs.getInt(0) }
        }
    }

    /**
     * Regression: the JVM driver's SqlParameterSource overloads used to ignore the
     * source entirely (running the SQL with unbound placeholders). They must now
     * bind each named parameter from the source.
     */
    @Test
    fun testParamSourceBinding() {
        assumeDockerAvailable()
        val source = object : SqlParameterSource {
            private val values = mapOf("a" to 7, "b" to 5)
            override fun hasValue(paramName: String) = paramName in values
            override fun getValue(paramName: String) = values.getValue(paramName)
            override val parameterNames get() = values.keys.toTypedArray()
        }
        ItDatabase.newDriver(poolSize = 1).use { driver ->
            val sum = driver.execute("SELECT :a::int + :b::int AS s", source) { rs -> rs.getInt(0) }
            assertEquals(12, sum.single())
        }
    }

    // Skip (rather than fail) these tests where Docker is unavailable, e.g. CI runners
    // without a Docker daemon. Must run before any reference to the Postgres-backed table.
    private fun assumeDockerAvailable() =
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker is not available")
}

class ItProduct(override var fields: MutableMap<String, Any?> = mutableMapOf()) : Entity(fields) {
    var id by ItProducts.id
    var price by ItProducts.price
    var qty by ItProducts.qty
    var displayName by ItProducts.displayName
    var note by ItProducts.note
    var rank by ItProducts.rank
}

object ItProducts : Table<ItProduct>(Table.Meta("it_products"), ::ItProduct, ItDatabase) {
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

/**
 * Adapter that exposes the Hikari/JDBC [createDatabase] driver as a [Database],
 * backed by a Postgres container started once for the test run.
 */
object ItDatabase : Database {
    private val container = PostgreSQLContainer("postgres:16-alpine").apply { start() }

    private val driver = createDatabase(
        host = container.host,
        port = container.firstMappedPort,
        database = container.databaseName,
        user = container.username,
        password = container.password,
    )

    /** A separate driver against the same container, for tests that need their own pool/lifecycle. */
    fun newDriver(poolSize: Int) = createDatabase(
        host = container.host,
        port = container.firstMappedPort,
        database = container.databaseName,
        user = container.username,
        password = container.password,
        poolSize = poolSize,
    )

    init {
        driver.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS public.it_products (
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
