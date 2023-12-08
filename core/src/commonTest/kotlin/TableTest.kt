import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import s.knyazev.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestEntity(override var fields: MutableMap<String, Any?> = mutableMapOf()) : Entity(fields) {
    var id by TestTable.id
    var price by TestTable.price
    var text by TestTable.text
    var nullableTest by TestTable.nullableTest
}

object TestTable : Table<TestEntity>(Meta("products"), ::TestEntity, TableTest.databaseMockObj) {
    val id by Column.UUID()
    val price by Column.Int()
    val text by Column.Text()
    val nullableTest by Column.Text(true)

    init {
        id;price;text;nullableTest
    }
}


class TableTest {

    @Test
    fun testInsert() {
        val uuid = UUID.generateUUID()
        val expectedResult = """INSERT INTO public.products
                        ("id", "price", "text", "nullableTest")
                        VALUES('$uuid'::uuid, '100', 'hello world', null);"""
        TestTable.new(TestEntity().apply {
            id = uuid
            price = 100
            text = "hello world"
            nullableTest = null
        })
        assertEquals(remoteNewLinesAndSpaces(expectedResult), remoteNewLinesAndSpaces(databaseMockObj.internalSql))

    }

    @Test
    fun testUpdate() {
        val uuid = UUID.generateUUID()
        val expectedResult = """
            UPDATE public.products
            SET "id"='$uuid'::uuid, "price"='100', "text"='hello world'
            WHERE id = '$uuid'
        """
        TestTable.update(Query(TestTable.id eq uuid.toString()), TestEntity().apply {
            id = uuid
            price = 100
            text = "hello world"
            nullableTest = null
        })
        println(databaseMockObj.internalSql)
        assertEquals(remoteNewLinesAndSpaces(expectedResult), remoteNewLinesAndSpaces(databaseMockObj.internalSql))

    }

    companion object {
        val databaseMockObj = DatabaseMock()

        fun remoteNewLinesAndSpaces(value: String): String {
            return value.replace("\n", "").replace(" ", "")
        }
    }
}