import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.github.knyazevs.korm.createSqliteDatabase
import io.github.knyazevs.korm.database.SuspendDatabase
import io.github.knyazevs.korm.suspendAutocommit
import io.github.knyazevs.korm.suspendTransaction
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/**
 * End-to-end test of the suspend path on a real backend (SQLite, JVM + Native). It
 * exercises [suspendTransaction] / [suspendAutocommit] → SuspendDatabase.useConnection
 * → the offload runner (SuspendExecutorAdapter on the IO dispatcher). Reuses the
 * top-level SqCatalog/Products/Product from SqliteIntegrationTest (shared-cache :memory:).
 */
class SqliteSuspendTest {

    private val db: SuspendDatabase<SqCatalog> = createSqliteDatabase(":memory:")

    @Test
    fun suspendCrudRoundTrip() = runTest {
        val id = Uuid.random()
        db.suspendTransaction {
            Products.createTable()
            Products.new(Product().apply {
                this.id = id
                this.price = BigDecimal.fromInt(42)
                this.qty = 3
                this.displayName = "async-widget"
                this.note = null
                this.rank = null
            })
        }

        val found = db.suspendAutocommit { Products.findById(id) }
        assertEquals(id, found?.id)
        assertEquals("async-widget", found?.displayName)
        assertEquals(3, found?.qty)
    }

    @Test
    fun suspendTransactionRollsBackOnThrow() = runTest {
        val id = Uuid.random()
        db.suspendTransaction { Products.createTable() }

        assertFailsWith<IllegalStateException> {
            db.suspendTransaction {
                Products.new(Product().apply {
                    this.id = id
                    this.price = BigDecimal.fromInt(1)
                    this.qty = 1
                    this.displayName = "doomed"
                    this.note = null
                    this.rank = null
                })
                error("boom")
            }
        }

        // The insert must have been rolled back with the transaction.
        val found = db.suspendAutocommit { Products.findById(id) }
        assertEquals(null, found)
    }
}
