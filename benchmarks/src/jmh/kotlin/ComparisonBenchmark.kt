package korm.bench

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
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
import jakarta.persistence.Entity as JpaEntity
import jakarta.persistence.Id
import jakarta.persistence.Table as JpaTable
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
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
import com.ionspin.kotlin.bignum.decimal.BigDecimal as KormBigDecimal
import org.jetbrains.exposed.sql.Database as ExposedDatabase
import org.jetbrains.exposed.sql.Table as ExposedSqlTable
import kotlin.uuid.Uuid as KormUuid

// --- korm mapping ---
object Cmp : Catalog

class CmpRow(override var fields: MutableMap<String, Any?> = mutableMapOf()) : Entity(fields) {
    var id by CmpTable.id
    var name by CmpTable.name
    var amount by CmpTable.amount
}

object CmpTable : Table<Cmp, CmpRow>(Table.Meta("cmp_bench"), ::CmpRow) {
    val id by Column.UUID(primaryKey = true)
    val name by Column.Text()
    val amount by Column.BigDecimal()

    init { id; name; amount }
}

// --- Exposed mapping ---
object ExposedBench : ExposedSqlTable("cmp_bench") {
    val id = uuid("id")
    val name = text("name")
    val amount = decimal("amount", 20, 2)
    override val primaryKey = PrimaryKey(id)
}

// --- Hibernate mapping ---
@JpaEntity
@JpaTable(name = "cmp_bench")
open class HibBench {
    @Id
    open var id: java.util.UUID = java.util.UUID.randomUUID()
    open var name: String = ""
    open var amount: java.math.BigDecimal = java.math.BigDecimal.ONE
}

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
open class ComparisonBenchmark {

    private lateinit var container: PostgreSQLContainer<*>
    private lateinit var kormDb: Database<Cmp>
    private lateinit var exposedDb: ExposedDatabase
    private lateinit var exposedDs: HikariDataSource
    private lateinit var sessionFactory: SessionFactory

    private val seededJavaId: java.util.UUID = java.util.UUID.randomUUID()
    private val seededKormId: KormUuid = KormUuid.fromLongs(seededJavaId.mostSignificantBits, seededJavaId.leastSignificantBits)

    @Setup
    fun setup() {
        container = PostgreSQLContainer("postgres:16-alpine").apply { start() }

        kormDb = createDatabase(
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            user = container.username,
            password = container.password,
            poolSize = 8,
        )
        kormDb.transaction {
            CmpTable.createTable()
            CmpTable.new(CmpRow().apply { id = seededKormId; name = "seed"; amount = KormBigDecimal.fromInt(1) })
        }

        exposedDs = HikariDataSource(HikariConfig().apply {
            jdbcUrl = container.jdbcUrl
            username = container.username
            password = container.password
            maximumPoolSize = 8
        })
        exposedDb = ExposedDatabase.connect(exposedDs)

        sessionFactory = Configuration()
            .addAnnotatedClass(HibBench::class.java)
            .setProperty("hibernate.connection.url", container.jdbcUrl)
            .setProperty("hibernate.connection.username", container.username)
            .setProperty("hibernate.connection.password", container.password)
            .setProperty("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider")
            .setProperty("hibernate.hikari.maximumPoolSize", "8")
            .setProperty("hibernate.hbm2ddl.auto", "none")
            .buildSessionFactory()
    }

    @TearDown
    fun teardown() {
        runCatching { sessionFactory.close() }
        runCatching { exposedDs.close() }
        runCatching { kormDb.close() }
        container.stop()
    }

    // --- korm ---
    @Benchmark
    fun kormFindById(): Any? = kormDb.autocommit { CmpTable.findById(seededKormId) }

    @Benchmark
    fun kormInsert(): Any? = kormDb.transaction {
        CmpTable.new(CmpRow().apply { id = KormUuid.random(); name = "x"; amount = KormBigDecimal.fromInt(1) })
    }

    @Benchmark
    fun kormSelectWhere(): Any? = kormDb.autocommit { CmpTable.find(Query(CmpTable.name eq "seed")) }

    // --- Exposed ---
    @Benchmark
    fun exposedFindById(): Any? = transaction(exposedDb) {
        ExposedBench.selectAll().where { ExposedBench.id eq seededJavaId }.firstOrNull()
    }

    @Benchmark
    fun exposedInsert(): Any? = transaction(exposedDb) {
        ExposedBench.insert {
            it[id] = java.util.UUID.randomUUID()
            it[name] = "x"
            it[amount] = java.math.BigDecimal.ONE
        }
    }

    @Benchmark
    fun exposedSelectWhere(): Any? = transaction(exposedDb) {
        ExposedBench.selectAll().where { ExposedBench.name eq "seed" }.toList()
    }

    // --- Hibernate ---
    @Benchmark
    fun hibernateFindById(): Any? = sessionFactory.openSession().use { it.find(HibBench::class.java, seededJavaId) }

    @Benchmark
    fun hibernateInsert(): Any? = sessionFactory.openSession().use { s ->
        val tx = s.beginTransaction()
        s.persist(HibBench().apply { id = java.util.UUID.randomUUID(); name = "x"; amount = java.math.BigDecimal.ONE })
        tx.commit()
    }

    @Benchmark
    fun hibernateSelectWhere(): Any? = sessionFactory.openSession().use {
        it.createQuery("from HibBench where name = :n", HibBench::class.java).setParameter("n", "seed").list()
    }
}
