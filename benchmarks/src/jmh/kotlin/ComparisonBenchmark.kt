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
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.annotations.Warmup
import org.testcontainers.containers.PostgreSQLContainer
import java.util.concurrent.TimeUnit
import com.ionspin.kotlin.bignum.decimal.BigDecimal as KormBigDecimal
import org.jetbrains.exposed.sql.Database as ExposedDatabase
import org.jetbrains.exposed.sql.Table as ExposedSqlTable
import kotlin.uuid.Uuid as KormUuid

// --- korm mapping ---
object Cmp : Catalog

class CmpRow : Entity() {
    var id by CmpTable.id
    var name by CmpTable.name
    var amount by CmpTable.amount
}

object CmpTable : Table<Cmp, CmpRow>("cmp_bench", ::CmpRow) {
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
@Threads(8)
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
        // Use an external Postgres (shared with the native benchmark) when KORM_DB_HOST is
        // set; otherwise spin up an ephemeral Testcontainers one.
        val envHost = System.getenv("KORM_DB_HOST")
        val host: String
        val port: Int
        val database: String
        val user: String
        val password: String
        if (envHost != null) {
            host = envHost
            port = System.getenv("KORM_DB_PORT")?.toInt() ?: 5432
            database = System.getenv("KORM_DB_NAME") ?: "postgres"
            user = System.getenv("KORM_DB_USER") ?: "postgres"
            password = System.getenv("KORM_DB_PASSWORD") ?: "password"
        } else {
            container = PostgreSQLContainer("postgres:16-alpine").apply { start() }
            host = container.host
            port = container.firstMappedPort
            database = container.databaseName
            user = container.username
            password = container.password
        }
        val jdbcUrl = "jdbc:postgresql://$host:$port/$database"

        kormDb = createDatabase(host, port, database, user, password, poolSize = 8)
        // Start from a clean table (the insert benchmarks bloat it across runs) and index
        // `name` so selectWhere is an index lookup, not a size-dependent sequential scan.
        kormDb.transaction {
            CmpTable.dropTable()
            CmpTable.createTable()
            executeUpdate("""CREATE INDEX IF NOT EXISTS cmp_bench_name_idx ON "public"."cmp_bench" ("name")""")
            CmpTable.new(CmpRow().apply { id = seededKormId; name = "seed"; amount = KormBigDecimal.fromInt(1) })
        }

        exposedDs = HikariDataSource(HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            maximumPoolSize = 8
        })
        exposedDb = ExposedDatabase.connect(exposedDs)

        sessionFactory = Configuration()
            .addAnnotatedClass(HibBench::class.java)
            .setProperty("hibernate.connection.url", jdbcUrl)
            .setProperty("hibernate.connection.username", user)
            .setProperty("hibernate.connection.password", password)
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
        if (::container.isInitialized) container.stop()
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
