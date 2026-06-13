import io.github.moreirasantos.pgkn.FPostgresDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end tests of the native true-async path (useConnection through the SocketReactor),
 * exercised via the public suspend API. Skipped without KORMIUM_DB_*.
 */
@OptIn(ExperimentalForeignApi::class, DelicateCoroutinesApi::class)
class NativeAsyncTest {

    private fun env(name: String) = getenv(name)?.toKString()

    private fun driver(poolSize: Int) = FPostgresDriver(
        host = env("KORMIUM_DB_HOST") ?: "127.0.0.1",
        port = env("KORMIUM_DB_PORT")?.toInt() ?: 5432,
        database = env("KORMIUM_DB_NAME") ?: "postgres",
        user = env("KORMIUM_DB_USER") ?: "postgres",
        password = env("KORMIUM_DB_PASSWORD") ?: "password",
        poolSize = poolSize,
    )

    @Test
    fun asyncRoundTrip() {
        if (env("KORMIUM_DB_HOST") == null) {
            println("KORMIUM_DB_HOST not set — skipping async test"); return
        }
        driver(poolSize = 2).use { db ->
            runBlocking {
                withTimeout(10.seconds) {
                    val one = db.useConnection(transactional = false) { exec ->
                        exec.execute("SELECT 1") { rs -> rs.getInt(0) }
                    }
                    assertEquals(listOf(1), one)

                    val sum = db.useConnection(transactional = true) { exec ->
                        exec.execute("SELECT :a::int + :b::int", mapOf("a" to 2, "b" to 40)) { rs -> rs.getInt(0) }
                    }
                    assertEquals(listOf(42), sum)
                }
            }
        }
    }

    /**
     * The async payoff through the public API: 8 transactions each running pg_sleep(0.3),
     * dispatched on ONE thread. Blocking would serialise to ~2.4s; the reactor frees the
     * thread at each network wait so they overlap (<1.5s).
     */
    @Test
    fun concurrentSuspendTransactionsOverlapOnOneThread() {
        if (env("KORMIUM_DB_HOST") == null) {
            println("KORMIUM_DB_HOST not set — skipping async test"); return
        }
        val n = 8
        driver(poolSize = n).use { db ->
            val client = newSingleThreadContext("async-test-client")
            try {
                val mark = TimeSource.Monotonic.markNow()
                runBlocking(client) {
                    withTimeout(10.seconds) {
                        coroutineScope {
                            List(n) {
                                async {
                                    db.useConnection(transactional = true) { exec ->
                                        exec.execute("SELECT pg_sleep(0.3)") { }
                                    }
                                }
                            }.awaitAll()
                        }
                    }
                }
                val elapsed = mark.elapsedNow()
                assertTrue(elapsed < 1.5.seconds, "8 async transactions took $elapsed; expected overlap (<1.5s)")
            } finally {
                client.close()
            }
        }
    }
}
