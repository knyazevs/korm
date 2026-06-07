import io.github.knyazevs.korm.suspendAutocommit
import io.github.knyazevs.korm.suspendTransaction
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SuspendTest {

    @Test
    fun suspendTransactionRunsTheBlock() = runTest {
        TableTest.db.suspendTransaction {
            TestTable.findById(Uuid.random())
        }
        assertTrue(TableTest.databaseMockObj.internalSql.contains("SELECT"))
    }

    @Test
    fun suspendAutocommitReturnsValue() = runTest {
        val result: List<TestEntity> = TableTest.db.suspendAutocommit { TestTable.all() }
        assertEquals(emptyList(), result)
        assertTrue(TableTest.databaseMockObj.internalSql.contains("SELECT"))
    }
}
