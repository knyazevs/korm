package korm.bench

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.github.knyazevs.korm.Catalog
import io.github.knyazevs.korm.Column
import io.github.knyazevs.korm.Entity
import io.github.knyazevs.korm.Query
import io.github.knyazevs.korm.Table
import io.github.knyazevs.korm.autocommit
import io.github.knyazevs.korm.eq
import io.github.knyazevs.korm.database.Database
import io.github.knyazevs.korm.database.createDatabase
import io.github.knyazevs.korm.transaction
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
    val id by Column.UUID(primaryKey = true)
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
            BenchTable.createTable()
            BenchTable.new(BenchRow().apply { id = seededId; name = "seed"; amount = BigDecimal.fromInt(1) })
        }
    }

    @TearDown
    fun teardown() {
        db.close()
        container.stop()
    }

    @Benchmark
    fun insert(): Any? = db.transaction {
        BenchTable.new(BenchRow().apply { id = Uuid.random(); name = "x"; amount = BigDecimal.fromInt(1) })
    }

    @Benchmark
    fun findById(): Any? = db.autocommit { BenchTable.findById(seededId) }

    @Benchmark
    fun selectWhere(): Any? = db.autocommit { BenchTable.find(Query(BenchTable.name eq "seed")) }
}
