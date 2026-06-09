import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.github.kormium.BatchInsertMode
import io.github.kormium.Migration
import io.github.kormium.autocommit
import io.github.kormium.createSqliteDatabase
import io.github.kormium.database.Database
import io.github.kormium.migrate
import io.github.kormium.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** The createSqliteDatabase { } builder: config { } is applied and beforeStart { } runs before
 *  the database is returned (here, migrations create the table). JVM + native. */
class SqliteBuilderTest {

    @Test
    fun builderAppliesConfigAndRunsBeforeStart() {
        val migrations = listOf(Migration<SqCatalog>("builder-001") { Products.execSql(productsDdl) })

        val db: Database<SqCatalog> = createSqliteDatabase {
            config { batchInsertMode = BatchInsertMode.UnionNulls }
            beforeStart { migrate(migrations) }   // receiver is the db; catalog comes from the list
        }

        db.use {
            // config { } was applied to the handle.
            assertEquals(BatchInsertMode.UnionNulls, db.config.batchInsertMode)

            // beforeStart ran the migration, so the table exists and is usable.
            val id = Uuid.random()
            db.transaction {
                Products.insert(Product().apply {
                    this.id = id
                    this.price = BigDecimal.fromInt(5)
                    this.qty = 1
                    this.displayName = "from-builder"
                    this.note = null
                    this.rank = null
                })
            }
            assertEquals("from-builder", db.autocommit { Products.findById(id) }?.displayName)
        }
    }
}
