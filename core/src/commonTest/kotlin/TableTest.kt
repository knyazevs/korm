import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import io.github.knyazevs.korm.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestEntity(override var fields: MutableMap<String, Any?> = mutableMapOf()) : Entity(fields) {
    var id by TestTable.id
    var price by TestTable.price
    var position by TestTable.position
    var text by TestTable.text
    var nullableTest by TestTable.nullableTest
}

object TestTable : Table<TestEntity>(Meta("products"), ::TestEntity, TableTest.databaseMockObj) {
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
        val uuid = UUID.generateUUID()
        val price = BigDecimal.fromInt(100)
        val position = 1
        val text = "hello world"
        val expectedResult = """INSERT INTO public.products
                        ("id", "price", "position", "text", "nullableTest")
                        VALUES('$uuid'::uuid, '$price', '$position', '$text', null);"""
        TestTable.new(TestEntity().apply {
            this.id = uuid
            this.price = price
            this.position = position
            this.text = text
            this.nullableTest = null
        })
        assertEquals(remoteNewLinesAndSpaces(expectedResult), remoteNewLinesAndSpaces(databaseMockObj.internalSql))

    }

    @Test
    fun testUpdate() {
        val uuid = UUID.generateUUID()
        val price = BigDecimal.fromInt(100)
        val position = 1
        val text = "hello world"
        val expectedResult = """
            UPDATE public.products
            SET "id"='$uuid'::uuid, "price"='$price', "position"='$position', "text"='$text'
            WHERE id = '$uuid'
        """
        TestTable.update(Query(TestTable.id eq uuid.toString()), TestEntity().apply {
            this.id = uuid
            this.price = price
            this.position = position
            this.text = text
            this.nullableTest = null
        })
        println(databaseMockObj.internalSql)
        assertEquals(remoteNewLinesAndSpaces(expectedResult), remoteNewLinesAndSpaces(databaseMockObj.internalSql))

    }

    @Test
    fun testSelect() {
        query = TestTable.find(TestTable.price eq price.toString(),
            limit = count,
            offset = from,
            orderBy = mapOf(ProductTable.name to AscDescOrder.ASC))

    }

    companion object {
        val databaseMockObj = DatabaseMock()

        fun remoteNewLinesAndSpaces(value: String): String {
            return value.replace("\n", "").replace(" ", "")
        }
    }
}