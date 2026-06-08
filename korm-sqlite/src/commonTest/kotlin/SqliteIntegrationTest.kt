import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.Column
import io.github.knyazevs.korm.Entity
import io.github.knyazevs.korm.ForeignKeyViolationException
import io.github.knyazevs.korm.Query
import io.github.knyazevs.korm.Table
import io.github.knyazevs.korm.UniqueViolationException
import io.github.knyazevs.korm.autocommit
import io.github.knyazevs.korm.count
import io.github.knyazevs.korm.createSqliteDatabase
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.eq
import io.github.knyazevs.korm.innerJoin
import io.github.knyazevs.korm.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * End-to-end tests for the SQLite backend. They run on every target (JVM via
 * sqlite-jdbc, Native via the sqlite3 cinterop driver) against a shared in-memory
 * database, so no external server / Docker is needed. The schema is created through
 * [Table.createTable], which exercises [io.github.knyazevs.korm.SqliteDialect]'s
 * type mapping; the all-types round-trip is the key check that SQLite's text storage
 * and the verbatim temporal parsing line up.
 */
class SqliteIntegrationTest {

    // One shared in-memory database (shared-cache) for the whole class. Tables are
    // created with IF NOT EXISTS; every test uses fresh random ids.
    private val db: Database<SqCatalog> = createSqliteDatabase(":memory:")

    @Test
    fun testInsertFindUpdateDeleteRoundTrip() {
        val id = Uuid.random()
        db.transaction {
            Products.createTable()
            Products.new(Product().apply {
                this.id = id
                this.price = BigDecimal.fromInt(100)
                this.qty = 5
                this.displayName = "widget"
                this.note = null
                this.rank = null
            })
        }

        val found = db.autocommit { Products.findById(id) }
        assertEquals(id, found?.id)
        assertEquals(5, found?.qty)
        assertEquals("widget", found?.displayName)
        assertNull(found?.note)
        // A nullable numeric column that is NULL must read back as null, not 0.
        assertNull(found?.rank)
        assertEquals(0, BigDecimal.fromInt(100).compareTo(found?.price!!))

        // Parameterized WHERE on a typed value.
        val byQty = db.autocommit { Products.find(Query(Products.qty eq 5)) }.filter { it.id == id }
        assertEquals(1, byQty.size)

        // Partial update: only the set field goes into SET.
        db.transaction { Products.update(Query(Products.id eq id), Product().apply { this.qty = 9 }) }
        assertEquals(9, db.autocommit { Products.findById(id) }?.qty)

        db.transaction { Products.deleteWhere(Query(Products.id eq id)) }
        assertNull(db.autocommit { Products.findById(id) })
    }

    /** A value containing a quote round-trips intact (proves parameterization). */
    @Test
    fun testValueWithQuoteRoundTrips() {
        val id = Uuid.random()
        val tricky = "O'Brien'; DROP TABLE products; --"
        db.transaction {
            Products.createTable()
            Products.new(Product().apply {
                this.id = id; this.price = BigDecimal.fromInt(1); this.qty = 1
                this.displayName = tricky; this.note = null; this.rank = null
            })
        }
        assertEquals(tricky, db.autocommit { Products.findById(id) }?.displayName)
        db.transaction { Products.deleteWhere(Query(Products.id eq id)) }
    }

    /** Every supported column type round-trips through a generated table. */
    @Test
    fun testAllColumnTypesRoundTrip() {
        val id = Uuid.random()
        val instant = kotlinx.datetime.Instant.parse("2024-01-02T03:04:05Z")
        val json = kotlinx.serialization.json.JsonPrimitive("hi")
        val date = kotlinx.datetime.LocalDate.parse("2024-01-02")
        val time = kotlinx.datetime.LocalTime.parse("03:04:05")
        val dateTime = kotlinx.datetime.LocalDateTime.parse("2024-01-02T03:04:05")
        db.transaction {
            AllTypes.createTable()
            AllTypes.new(AllTypesEntity().apply {
                this.id = id
                this.anInt = 42
                this.aDouble = 2.5
                this.aBool = true
                this.aText = "txt"
                this.aDecimal = BigDecimal.fromInt(123)
                this.anInstant = instant
                this.aJson = json
                this.aLong = 9_000_000_000L
                this.aFloat = 1.5f
                this.aShort = 7.toShort()
                this.aDate = date
                this.aTime = time
                this.aDateTime = dateTime
            })
        }
        val row = db.autocommit { AllTypes.findById(id) }!!
        assertEquals(42, row.anInt)
        assertEquals(2.5, row.aDouble)
        assertEquals(true, row.aBool)
        assertEquals("txt", row.aText)
        assertEquals(0, BigDecimal.fromInt(123).compareTo(row.aDecimal!!))
        assertEquals(instant, row.anInstant)
        assertEquals(json, row.aJson)
        assertEquals(9_000_000_000L, row.aLong)
        assertEquals(1.5f, row.aFloat)
        assertEquals(7.toShort(), row.aShort)
        assertEquals(date, row.aDate)
        assertEquals(time, row.aTime)
        assertEquals(dateTime, row.aDateTime)
        db.transaction { AllTypes.deleteWhere(Query(AllTypes.id eq id)) }
    }

    /** Batch insert + count() over a predicate. */
    @Test
    fun testBatchInsertAndCount() {
        val ids = List(3) { Uuid.random() }
        val tag = "batch-${Uuid.random()}"
        db.transaction {
            Products.createTable()
            Products.new(ids.mapIndexed { i, id ->
                Product().apply {
                    this.id = id; this.price = BigDecimal.fromInt(i); this.qty = i
                    this.displayName = tag; this.note = null; this.rank = null
                }
            })
        }
        val count = db.autocommit { Products.count(Query(Products.displayName eq tag)) }
        assertEquals(3L, count)
        db.transaction { ids.forEach { Products.deleteWhere(Query(Products.id eq it)) } }
    }

    /** An exception out of a transaction block rolls back every statement in it. */
    @Test
    fun testTransactionRollsBackOnException() {
        val id = Uuid.random()
        db.transaction { Products.createTable() }
        assertFailsWith<RuntimeException> {
            db.transaction {
                Products.new(Product().apply {
                    this.id = id; this.price = BigDecimal.fromInt(1); this.qty = 1
                    this.displayName = "rollback"; this.note = null; this.rank = null
                })
                throw RuntimeException("boom")
            }
        }
        assertNull(db.autocommit { Products.findById(id) })
    }

    /** A duplicate primary key surfaces as a typed UniqueViolationException. */
    @Test
    fun testUniqueViolationIsTyped() {
        val id = Uuid.random()
        db.transaction {
            Products.createTable()
            Products.new(Product().apply {
                this.id = id; this.price = BigDecimal.fromInt(1); this.qty = 1
                this.displayName = "dup"; this.note = null; this.rank = null
            })
        }
        assertFailsWith<UniqueViolationException> {
            db.transaction {
                Products.new(Product().apply {
                    this.id = id; this.price = BigDecimal.fromInt(2); this.qty = 2
                    this.displayName = "dup2"; this.note = null; this.rank = null
                })
            }
        }
        db.transaction { Products.deleteWhere(Query(Products.id eq id)) }
    }

    /**
     * A foreign-key violation surfaces as a typed ForeignKeyViolationException —
     * proving the `foreign_keys=ON` pragma is applied (SQLite defaults it OFF).
     * The FK constraint is created via raw DDL since createTable() emits only PKs.
     */
    @Test
    fun testForeignKeyViolationIsTyped() {
        db.transaction {
            executeUpdate("CREATE TABLE IF NOT EXISTS fk_parent (id TEXT PRIMARY KEY)")
            executeUpdate(
                "CREATE TABLE IF NOT EXISTS fk_child (" +
                    "id TEXT PRIMARY KEY, parent_id TEXT REFERENCES fk_parent(id))"
            )
        }
        assertFailsWith<ForeignKeyViolationException> {
            db.transaction {
                executeUpdate(
                    "INSERT INTO fk_child (id, parent_id) VALUES (:id, :p)",
                    mapOf("id" to Uuid.random().toString(), "p" to Uuid.random().toString()),
                )
            }
        }
    }

    /** A join round-trips: ResultRow, projection, and entity pairs. */
    @Test
    fun testJoinRoundTrip() {
        val authorId = Uuid.random()
        val bookId = Uuid.random()
        db.transaction {
            Authors.createTable()
            Books.createTable()
            Authors.new(Author().apply { id = authorId; name = "Ada" })
            Books.new(Book().apply { id = bookId; this.authorId = authorId; title = "Notes" })
        }

        val rows = db.autocommit {
            (Authors innerJoin Books on (Authors.id eq Books.authorId)).select()
        }
        assertEquals(1, rows.size)
        assertEquals("Ada", rows.single()[Authors.name])
        assertEquals("Notes", rows.single()[Books.title])

        val titles = db.autocommit {
            (Authors innerJoin Books on (Authors.id eq Books.authorId))
                .select(Authors.name, Books.title) { it[Authors.name] to it[Books.title] }
        }
        assertEquals(listOf("Ada" to "Notes"), titles)

        db.transaction { Books.deleteWhere(Query(Books.id eq bookId)); Authors.deleteWhere(Query(Authors.id eq authorId)) }
    }
}

object SqCatalog : Catalog

class Product : Entity() {
    var id by Products.id
    var price by Products.price
    var qty by Products.qty
    var displayName by Products.displayName
    var note by Products.note
    var rank by Products.rank
}

object Products : Table<SqCatalog, Product>("products", ::Product) {
    val id by Column.UUID(primaryKey = true)
    val price by Column.BigDecimal()
    val qty by Column.Int()
    val displayName by Column.Text()
    val note by Column.Text(true)
    val rank by Column.Int(true)

    init { id; price; qty; displayName; note; rank }
}

class Author : Entity() {
    var id by Authors.id
    var name by Authors.name
}

object Authors : Table<SqCatalog, Author>("authors", ::Author) {
    val id by Column.UUID(primaryKey = true)
    val name by Column.Text()

    init { id; name }
}

class Book : Entity() {
    var id by Books.id
    var authorId by Books.authorId
    var title by Books.title
}

object Books : Table<SqCatalog, Book>("books", ::Book) {
    val id by Column.UUID(primaryKey = true)
    val authorId by Column.UUID()
    val title by Column.Text()

    init { id; authorId; title }
}

class AllTypesEntity : Entity() {
    var id by AllTypes.id
    var anInt by AllTypes.anInt
    var aDouble by AllTypes.aDouble
    var aBool by AllTypes.aBool
    var aText by AllTypes.aText
    var aDecimal by AllTypes.aDecimal
    var anInstant by AllTypes.anInstant
    var aJson by AllTypes.aJson
    var aLong by AllTypes.aLong
    var aFloat by AllTypes.aFloat
    var aShort by AllTypes.aShort
    var aDate by AllTypes.aDate
    var aTime by AllTypes.aTime
    var aDateTime by AllTypes.aDateTime
}

object AllTypes : Table<SqCatalog, AllTypesEntity>("all_types", ::AllTypesEntity) {
    val id by Column.UUID(primaryKey = true)
    val anInt by Column.Int()
    val aDouble by Column.Double()
    val aBool by Column.Boolean()
    val aText by Column.Text()
    val aDecimal by Column.BigDecimal()
    val anInstant by Column.Instant()
    val aJson by Column.Json()
    val aLong by Column.Long()
    val aFloat by Column.Float()
    val aShort by Column.Short()
    val aDate by Column.LocalDate()
    val aTime by Column.LocalTime()
    val aDateTime by Column.LocalDateTime()

    init {
        id; anInt; aDouble; aBool; aText; aDecimal; anInstant; aJson
        aLong; aFloat; aShort; aDate; aTime; aDateTime
    }
}
