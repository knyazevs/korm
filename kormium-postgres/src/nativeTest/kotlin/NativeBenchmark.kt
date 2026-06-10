import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.github.kormium.Catalog
import io.github.kormium.Column
import io.github.kormium.Entity
import io.github.kormium.Query
import io.github.kormium.Table
import io.github.kormium.autocommit
import io.github.kormium.database.Database
import io.github.kormium.database.createDatabase
import io.github.kormium.eq
import io.github.kormium.transaction
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
private fun benchEnv(name: String): String? = getenv(name)?.toKString()

private fun benchDriver(poolSize: Int): Database<BenchCatalog> = createDatabase(
    host = benchEnv("KORMIUM_DB_HOST") ?: "localhost",
    port = benchEnv("KORMIUM_DB_PORT")?.toInt() ?: 5432,
    database = benchEnv("KORMIUM_DB_NAME") ?: "postgres",
    user = benchEnv("KORMIUM_DB_USER") ?: "postgres",
    password = benchEnv("KORMIUM_DB_PASSWORD") ?: "password",
    poolSize = poolSize,
)

object BenchCatalog : Catalog

class BenchRow : Entity() {
    var id by BenchTable.id
    var name by BenchTable.name
    var amount by BenchTable.amount
}

object BenchTable : Table<BenchCatalog, BenchRow>("cmp_bench", ::BenchRow) {
    val id by Column.UUID().primaryKey()
    val name by Column.Text()
    val amount by Column.BigDecimal()

    init { id; name; amount }
}

private val benchSeedId = Uuid.random()

private enum class Op { FIND_BY_ID, SELECT_WHERE, INSERT }

private fun runOp(db: Database<BenchCatalog>, op: Op) {
    when (op) {
        Op.FIND_BY_ID -> db.autocommit { BenchTable.findById(benchSeedId) }
        Op.SELECT_WHERE -> db.autocommit { BenchTable.find(Query(BenchTable.name eq "seed")) }
        Op.INSERT -> db.transaction {
            BenchTable.insert(BenchRow().apply { id = Uuid.random(); name = "x"; amount = BigDecimal.fromInt(1) })
        }
    }
}

/**
 * Native counterpart to the JVM JMH ComparisonBenchmark: same three operations, 8 worker
 * threads, against the Postgres given by KORMIUM_DB_* env vars. Opt-in via KORM_BENCH so it
 * never runs in the normal test suite. Prints ops/s lines the runner script parses.
 */
class NativeBenchmark {

    @Test
    fun runNativeBenchmark() {
        if (benchEnv("KORM_BENCH") == null) {
            println("KORM_BENCH not set — skipping native benchmark")
            return
        }
        val driver = benchDriver(poolSize = 8)
        // Fresh table + an index on `name` so selectWhere is an index lookup (matches the JVM
        // harness) rather than a sequential scan that degrades as inserts bloat the table.
        driver.transaction {
            BenchTable.execSql("DROP TABLE IF EXISTS \"cmp_bench\"")
            BenchTable.execSql(benchDdl)
            executeUpdate("""CREATE INDEX IF NOT EXISTS cmp_bench_name_idx ON "public"."cmp_bench" ("name")""")
            BenchTable.insert(BenchRow().apply { id = benchSeedId; name = "seed"; amount = BigDecimal.fromInt(1) })
        }

        val threads = 8
        val ops = 5_000
        val findById = bench(driver, threads, ops, Op.FIND_BY_ID)
        val selectWhere = bench(driver, threads, ops, Op.SELECT_WHERE)
        val insert = bench(driver, threads, ops, Op.INSERT)

        println("KORM_NATIVE_RESULT findById=${findById.toInt()} selectWhere=${selectWhere.toInt()} insert=${insert.toInt()}")
        driver.close()
    }

    /** Runs [opsPerThread] ops of [op] on each of [threads] workers; returns ops/second. */
    private fun bench(driver: Database<BenchCatalog>, threads: Int, opsPerThread: Int, op: Op): Double {
        repeat(2_000) { runOp(driver, op) } // warm up
        val mark = TimeSource.Monotonic.markNow()
        val workers = List(threads) { Worker.start() }
        val futures = workers.map { worker ->
            worker.execute(TransferMode.SAFE, { Triple(driver, opsPerThread, op) }) { (db, n, o) ->
                repeat(n) { runOp(db, o) }
            }
        }
        futures.forEach { it.result }
        val elapsedMs = mark.elapsedNow().inWholeMilliseconds.coerceAtLeast(1)
        workers.forEach { it.requestTermination().result }
        return threads.toLong() * opsPerThread * 1000.0 / elapsedMs
    }
}

private val benchDdl = """CREATE TABLE IF NOT EXISTS "cmp_bench" ("id" uuid NOT NULL, "name" text NOT NULL, "amount" numeric NOT NULL, PRIMARY KEY ("id"))"""
