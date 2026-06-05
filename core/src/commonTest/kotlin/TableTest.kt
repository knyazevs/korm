import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.uuid.Uuid
import io.github.knyazevs.korm.*
import io.github.knyazevs.korm.database.Database
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestEntity(override var fields: MutableMap<String, Any?> = mutableMapOf()) : Entity(fields) {
    var id by TestTable.id
    var price by TestTable.price
    var position by TestTable.position
    var text by TestTable.text
    var nullableTest by TestTable.nullableTest
}

object TestCatalog : Catalog

object TestTable : Table<TestCatalog, TestEntity>(Meta("products"), ::TestEntity) {
    val id by Column.UUID()
    val price by Column.BigDecimal()
    val position by Column.Int()
    val text by Column.Text()
    val nullableTest by Column.Text(true)

    init {
        id;price;position;text;nullableTest
    }
}


class TableTest {

    @Test
    fun testInsert() {
        val uuid = Uuid.random()
        val price = BigDecimal.fromInt(100)
        val position = 1
        val text = "hello world"
        val expectedResult = """INSERT INTO "public"."products"
                        ("id", "price", "position", "text", "nullableTest")
                        VALUES(:p0, :p1, :p2, :p3, :p4)
                        RETURNING "id", "price", "position", "text", "nullableTest";"""
        db.transaction {
            TestTable.new(TestEntity().apply {
                this.id = uuid
                this.price = price
                this.position = position
                this.text = text
                this.nullableTest = null
            })
        }
        assertEquals(remoteNewLinesAndSpaces(expectedResult), remoteNewLinesAndSpaces(databaseMockObj.internalSql))
        assertEquals(
            mapOf(
                "p0" to uuid.toString(),
                "p1" to price.toString(),
                "p2" to position,
                "p3" to text,
                "p4" to null,
            ),
            databaseMockObj.internalParams,
        )
    }

    @Test
    fun testUpdate() {
        val uuid = Uuid.random()
        val price = BigDecimal.fromInt(100)
        val position = 1
        val text = "hello world"
        val expectedResult = """
            UPDATE "public"."products"
            SET "id"=:p0, "price"=:p1, "position"=:p2, "text"=:p3, "nullableTest"=:p4
            WHERE "id" = :p5
        """
        db.transaction {
            TestTable.update(Query(TestTable.id eq uuid), TestEntity().apply {
                this.id = uuid
                this.price = price
                this.position = position
                this.text = text
                this.nullableTest = null
            })
        }
        assertEquals(remoteNewLinesAndSpaces(expectedResult), remoteNewLinesAndSpaces(databaseMockObj.internalSql))
        assertEquals(
            mapOf(
                "p0" to uuid.toString(),
                "p1" to price.toString(),
                "p2" to position,
                "p3" to text,
                "p4" to null,
                "p5" to uuid.toString(),
            ),
            databaseMockObj.internalParams,
        )
    }

    /**
     * Regression: a field explicitly set to null must be written as NULL (it used to be
     * dropped from SET), while fields left untouched are still omitted (partial update).
     */
    @Test
    fun testUpdateCanSetNullAndOmitsUntouched() {
        val uuid = Uuid.random()
        val expectedResult = """UPDATE "public"."products" SET "nullableTest"=:p0 WHERE "id" = :p1"""
        db.transaction {
            TestTable.update(
                Query(TestTable.id eq uuid),
                TestEntity().apply { this.nullableTest = null },
            )
        }
        assertEquals(remoteNewLinesAndSpaces(expectedResult), remoteNewLinesAndSpaces(databaseMockObj.internalSql))
        assertEquals(mapOf("p0" to null, "p1" to uuid.toString()), databaseMockObj.internalParams)
    }

    @Test
    fun testSelect() {
        val price = BigDecimal.fromInt(100)
        val count = 10u
        val from = 5u
        val expectedResult = """
            SELECT "id", "price", "position", "text", "nullableTest" FROM "public"."products"
            WHERE "price" = :p0 ORDER BY "position" ASC LIMIT $count OFFSET $from
        """
        db.transaction {
            TestTable.find(
                Query(
                    whereExpression = TestTable.price eq price,
                    limit = count,
                    offset = from,
                    orderBy = mapOf(TestTable.position to AscDescOrder.ASC),
                )
            )
        }
        assertEquals(remoteNewLinesAndSpaces(expectedResult), remoteNewLinesAndSpaces(databaseMockObj.internalSql))
        assertEquals(mapOf("p0" to price.toString()), databaseMockObj.internalParams)
    }

    @Test
    fun testFindById() {
        val uuid = Uuid.random()
        val expectedResult = """SELECT "id", "price", "position", "text", "nullableTest" FROM "public"."products" WHERE "id" = :p0"""
        db.transaction { TestTable.findById(uuid) }
        assertEquals(remoteNewLinesAndSpaces(expectedResult), remoteNewLinesAndSpaces(databaseMockObj.internalSql))
        assertEquals(mapOf("p0" to uuid.toString()), databaseMockObj.internalParams)
    }

    @Test
    fun testDeleteWhere() {
        val expectedResult = """DELETE FROM "public"."products" WHERE "position" = :p0"""
        db.transaction { TestTable.deleteWhere(Query(TestTable.position eq 5)) }
        assertEquals(remoteNewLinesAndSpaces(expectedResult), remoteNewLinesAndSpaces(databaseMockObj.internalSql))
        assertEquals(mapOf("p0" to 5), databaseMockObj.internalParams)
    }

    @Test
    fun testCompoundWhereSharesOneParameterSpace() {
        val expectedResult = """
            SELECT "id", "price", "position", "text", "nullableTest" FROM "public"."products"
            WHERE "position" = :p0 AND "text" = :p1
        """
        db.transaction {
            TestTable.find(Query(TestTable.position eq 1 and (TestTable.text eq "abc")))
        }
        assertEquals(remoteNewLinesAndSpaces(expectedResult), remoteNewLinesAndSpaces(databaseMockObj.internalSql))
        assertEquals(mapOf("p0" to 1, "p1" to "abc"), databaseMockObj.internalParams)
    }

    @Test
    fun testUpdateWithNoNonNullFieldsFails() {
        assertFailsWith<IllegalArgumentException> {
            db.transaction { TestTable.update(Query(TestTable.id eq Uuid.random()), TestEntity()) }
        }
    }

    /**
     * Regression test for the bug where a [Query] with no `where` rendered the
     * literal string "null" into the SQL. An empty query must produce no WHERE.
     */
    @Test
    fun testEmptyQueryHasNoWhereClause() {
        val expectedResult = """SELECT "id", "price", "position", "text", "nullableTest" FROM "public"."products""""
        db.transaction { TestTable.find(Query()) }
        assertEquals(remoteNewLinesAndSpaces(expectedResult), remoteNewLinesAndSpaces(databaseMockObj.internalSql))
        assertFalse(databaseMockObj.internalSql.contains("WHERE"), "empty query must not emit WHERE")
        assertTrue(databaseMockObj.internalParams.isEmpty())
    }

    /**
     * The whole point of parameterization: a value that looks like an injection
     * payload must land in the bind parameters, never inlined into the SQL text.
     */
    @Test
    fun testValuesAreParameterizedNotInlined() {
        val payload = "x'; DROP TABLE products; --"
        db.transaction { TestTable.find(Query(TestTable.text eq payload)) }
        assertFalse(
            databaseMockObj.internalSql.contains("DROP TABLE"),
            "the value must not be inlined into SQL: ${databaseMockObj.internalSql}",
        )
        assertTrue(databaseMockObj.internalSql.contains(":p0"))
        assertEquals(payload, databaseMockObj.internalParams["p0"])
    }

    @Test
    fun testCreateTableGeneratesDdlFromColumns() {
        val expectedResult = """
            CREATE TABLE IF NOT EXISTS "public"."products" (
                "id" uuid NOT NULL,
                "price" numeric NOT NULL,
                "position" integer NOT NULL,
                "text" text NOT NULL,
                "nullableTest" text
            )
        """
        db.transaction { TestTable.createTable() }
        assertEquals(remoteNewLinesAndSpaces(expectedResult), remoteNewLinesAndSpaces(databaseMockObj.internalSql))
    }

    @Test
    fun testInListLikeIsNullAndNotOperators() {
        db.transaction { TestTable.find(Query(TestTable.position inList listOf(1, 2))) }
        assertTrue(remoteNewLinesAndSpaces(databaseMockObj.internalSql).contains(""""position"IN(:p0,:p1)"""))
        assertEquals(mapOf("p0" to 1, "p1" to 2), databaseMockObj.internalParams)

        db.transaction { TestTable.find(Query(TestTable.position inList emptyList())) }
        assertTrue(remoteNewLinesAndSpaces(databaseMockObj.internalSql).contains("WHEREFALSE"))

        db.transaction { TestTable.find(Query(TestTable.text like "ab%")) }
        assertTrue(remoteNewLinesAndSpaces(databaseMockObj.internalSql).contains(""""text"LIKE:p0"""))

        db.transaction { TestTable.find(Query(TestTable.nullableTest.isNull())) }
        assertTrue(remoteNewLinesAndSpaces(databaseMockObj.internalSql).contains(""""nullableTest"ISNULL"""))

        db.transaction { TestTable.find(Query(TestTable.nullableTest.isNotNull())) }
        assertTrue(remoteNewLinesAndSpaces(databaseMockObj.internalSql).contains(""""nullableTest"ISNOTNULL"""))

        db.transaction { TestTable.find(Query(not(TestTable.position eq 0))) }
        assertTrue(remoteNewLinesAndSpaces(databaseMockObj.internalSql).contains("""NOT("position"=:p0)"""))
    }

    companion object {
        val databaseMockObj = DatabaseMock()

        // Same instance, viewed as Database<TestCatalog> (covariance: Database<Nothing>
        // <: Database<TestCatalog>) so transaction { } resolves the catalog tag.
        val db: Database<TestCatalog> = databaseMockObj

        fun remoteNewLinesAndSpaces(value: String): String {
            return value.replace("\n", "").replace(" ", "")
        }
    }
}
