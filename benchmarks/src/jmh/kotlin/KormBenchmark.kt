package korm.bench

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.github.kormium.Catalog
import io.github.kormium.Column
import io.github.kormium.Entity
import io.github.kormium.Query
import io.github.kormium.Table
import io.github.kormium.autocommit
import io.github.kormium.eq
import io.github.kormium.database.Database
import io.github.kormium.database.createDatabase
import io.github.kormium.transaction
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import org.testcontainers.containers.PostgreSQLContainer
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

object Bench : Catalog

class BenchRow : Entity() {
    var id by BenchTable.id
    var name by BenchTable.name
    var amount by BenchTable.amount
}

object BenchTable : Table<Bench, BenchRow>("bench", ::BenchRow) {
    val id by Column.UUID().primaryKey()
    val name by Column.Text()
    val amount by Column.BigDecimal()

    init { id; name; amount }
}

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
open class KormBenchmark {

    private lateinit var container: PostgreSQLContainer<*>
    private lateinit var db: Database<Bench>
    private val seededId = Uuid.random()

    @Setup
    fun setup() {
        container = PostgreSQLContainer("postgres:16-alpine").apply { start() }
        db = createDatabase(
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            user = container.username,
            password = container.password,
            poolSize = 8,
        )
        db.transaction {
            BenchTable.execSql(benchDdl)
            BenchTable.insert(BenchRow().apply { id = seededId; name = "seed"; amount = BigDecimal.fromInt(1) })
        }
    }

    @TearDown
    fun teardown() {
        db.close()
        container.stop()
    }

    @Benchmark
    fun insert(): Any? = db.transaction {
        BenchTable.insert(BenchRow().apply { id = Uuid.random(); name = "x"; amount = BigDecimal.fromInt(1) })
    }

    @Benchmark
    fun findById(): Any? = db.autocommit { BenchTable.findById(seededId) }

    @Benchmark
    fun selectWhere(): Any? = db.autocommit { BenchTable.find(Query(BenchTable.name eq "seed")) }
}

private val benchDdl = """CREATE TABLE IF NOT EXISTS "bench" ("id" uuid NOT NULL, "name" text NOT NULL, "amount" numeric NOT NULL, PRIMARY KEY ("id"))"""
