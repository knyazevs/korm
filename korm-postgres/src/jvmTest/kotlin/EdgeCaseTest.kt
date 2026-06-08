import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.Column
import io.github.knyazevs.korm.Entity
import io.github.knyazevs.korm.Query
import io.github.knyazevs.korm.Table
import io.github.knyazevs.korm.autocommit
import io.github.knyazevs.korm.count
import io.github.knyazevs.korm.eq
import io.github.knyazevs.korm.inList
import io.github.knyazevs.korm.leftJoin
import io.github.knyazevs.korm.query
import io.github.knyazevs.korm.sum
import io.github.knyazevs.korm.transaction
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.DockerClientFactory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** End-to-end edge/corner cases against real Postgres. */
class EdgeCaseTest {

    @BeforeTest
    fun setup() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker is not available")
        ItDatabase.transaction {
            EdgeTable.execSql(edgeDdl)
            EdgeChild.execSql(edgeChildDdl)
            Reserved.execSql(reservedDdl)
        }
    }

    @Test
    fun findByIdMissingReturnsNull() {
        assertNull(ItDatabase.autocommit { EdgeTable.findById(Uuid.random()) })
    }

    @Test
    fun findWithNoMatchesIsEmpty() {
        val rows = ItDatabase.autocommit { EdgeTable.find(Query(EdgeTable.num eq -999_999)) }
        assertTrue(rows.isEmpty())
    }

    @Test
    fun nullableColumnsRoundTripNull() {
        val id = Uuid.random()
        ItDatabase.transaction {
            EdgeTable.insert(EdgeRow().apply { this.id = id; n = null; t = null; big = null; num = 1 })
        }
        val row = ItDatabase.autocommit { EdgeTable.findById(id) }!!
        assertNull(row.n)
        assertNull(row.t)
        assertNull(row.big)
        assertEquals(1, row.num)
    }

    @Test
    fun leftJoinMissingRightIsNull() {
        val pid = Uuid.random()
        ItDatabase.transaction { EdgeTable.insert(EdgeRow().apply { id = pid; num = 1 }) }
        val rows = ItDatabase.autocommit {
            (EdgeTable leftJoin EdgeChild on (EdgeTable.id eq EdgeChild.parentId))
                .where(EdgeTable.id eq pid)
                .select()
        }
        assertEquals(1, rows.size)
        assertNull(rows.single().getOrNull(EdgeChild.label))
        assertFailsWith<IllegalStateException> { rows.single()[EdgeChild.label] }
    }

    @Test
    fun countOfEmptyIsZeroAndSumIsNull() {
        val c = count()
        val s = EdgeTable.num.sum()
        val rows = ItDatabase.autocommit {
            EdgeTable.query().where(EdgeTable.num eq -888_888).select(c, s)
        }
        assertEquals(1, rows.size)
        assertEquals(0L, rows.single()[c])
        assertNull(rows.single().getOrNull(s))
    }

    @Test
    fun emptyInListMatchesNothing() {
        val rows = ItDatabase.autocommit { EdgeTable.find(Query(EdgeTable.id inList emptyList())) }
        assertTrue(rows.isEmpty())
    }

    @Test
    fun valueWithSpecialCharactersRoundTrips() {
        val id = Uuid.random()
        val tricky = "a'b\"c\\d\neé\t--; DROP TABLE edge; --"
        ItDatabase.transaction { EdgeTable.insert(EdgeRow().apply { this.id = id; t = tricky; num = 1 }) }
        assertEquals(tricky, ItDatabase.autocommit { EdgeTable.findById(id) }?.t)
    }

    @Test
    fun boundaryIntegerValuesRoundTrip() {
        val id = Uuid.random()
        ItDatabase.transaction { EdgeTable.insert(EdgeRow().apply { this.id = id; n = Int.MIN_VALUE; num = Int.MAX_VALUE }) }
        val row = ItDatabase.autocommit { EdgeTable.findById(id) }!!
        assertEquals(Int.MIN_VALUE, row.n)
        assertEquals(Int.MAX_VALUE, row.num)
    }

    @Test
    fun updateMatchingNoRowsIsNoOp() {
        // Must not throw, and must change nothing.
        ItDatabase.transaction {
            EdgeTable.update(Query(EdgeTable.id eq Uuid.random()), EdgeRow().apply { num = 5 })
        }
    }

    @Test
    fun batchInsertEmptyListIsNoOp() {
        val inserted = ItDatabase.transaction { EdgeTable.insertAll(emptyList<EdgeRow>()) }
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun nestedSavepointsRollBackInnerWork() {
        val keep = Uuid.random()
        val mid = Uuid.random()
        val inner = Uuid.random()
        ItDatabase.transaction {
            EdgeTable.insert(EdgeRow().apply { id = keep; num = 1 })
            runCatching {
                savepoint {
                    EdgeTable.insert(EdgeRow().apply { id = mid; num = 2 })
                    savepoint {
                        EdgeTable.insert(EdgeRow().apply { id = inner; num = 3 })
                        throw RuntimeException("boom")
                    }
                }
            }
        }
        assertEquals(keep, ItDatabase.autocommit { EdgeTable.findById(keep) }?.id)
        assertNull(ItDatabase.autocommit { EdgeTable.findById(mid) })
        assertNull(ItDatabase.autocommit { EdgeTable.findById(inner) })
    }

    @Test
    fun reservedWordColumnNameRoundTrips() {
        val id = Uuid.random()
        ItDatabase.transaction { Reserved.insert(ReservedRow().apply { this.id = id; order = 7 }) }
        assertEquals(7, ItDatabase.autocommit { Reserved.findById(id) }?.order)
    }
}

class EdgeRow : Entity() {
    var id by EdgeTable.id
    var n by EdgeTable.n
    var t by EdgeTable.t
    var big by EdgeTable.big
    var num by EdgeTable.num
}

object EdgeTable : Table<ItCatalog, EdgeRow>("edge", ::EdgeRow) {
    val id by Column.UUID().primaryKey()
    val n by Column.Int().nullable()
    val t by Column.Text().nullable()
    val big by Column.BigDecimal().nullable()
    val num by Column.Int()

    init { id; n; t; big; num }
}

class EdgeChildRow : Entity() {
    var id by EdgeChild.id
    var parentId by EdgeChild.parentId
    var label by EdgeChild.label
}

object EdgeChild : Table<ItCatalog, EdgeChildRow>("edge_child", ::EdgeChildRow) {
    val id by Column.UUID().primaryKey()
    val parentId by Column.UUID()
    val label by Column.Text()

    init { id; parentId; label }
}

class ReservedRow : Entity() {
    var id by Reserved.id
    var order by Reserved.order
}

object Reserved : Table<ItCatalog, ReservedRow>("reserved_tbl", ::ReservedRow) {
    val id by Column.UUID().primaryKey()
    val order by Column.Int()

    init { id; order }
}

// Raw schema DDL for tests (Korm no longer owns createTable). Postgres types.
private val edgeDdl = """CREATE TABLE IF NOT EXISTS "edge" ("id" uuid NOT NULL, "n" integer, "t" text, "big" numeric, "num" integer NOT NULL, PRIMARY KEY ("id"))"""
private val edgeChildDdl = """CREATE TABLE IF NOT EXISTS "edge_child" ("id" uuid NOT NULL, "parentId" uuid NOT NULL, "label" text NOT NULL, PRIMARY KEY ("id"))"""
private val reservedDdl = """CREATE TABLE IF NOT EXISTS "reserved_tbl" ("id" uuid NOT NULL, "order" integer NOT NULL, PRIMARY KEY ("id"))"""
