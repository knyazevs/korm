import io.github.kormium.WriteListeners
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Regression for the registry's lock-free copy-on-write: concurrent add/remove must never
 * lose a registration. The old plain-`@Volatile` version dropped listeners under a
 * concurrent `add` race — fatal for kormium-observe, which registers/unregisters a
 * listener per Flow collection.
 */
class WriteListenersConcurrencyTest {

    @Test
    fun concurrentAddsLoseNoListener() {
        val registry = WriteListeners()
        val threads = 8
        val perThread = 250
        val delivered = AtomicInteger()
        val start = CyclicBarrier(threads)

        (1..threads).map {
            thread {
                start.await()
                repeat(perThread) { registry.add { delivered.incrementAndGet() } }
            }
        }.forEach { it.join() }

        registry.fire(setOf("t"))
        assertEquals(threads * perThread, delivered.get(), "every concurrently-added listener must fire")
    }

    @Test
    fun concurrentRemovesDetachEveryRemovedListener() {
        val registry = WriteListeners()
        val threads = 8
        val perThread = 250
        val survivors = AtomicInteger()
        val removedFired = AtomicInteger()

        // Each thread adds one listener that must survive, plus a batch it removes again.
        val ready = CountDownLatch(threads)
        (1..threads).map {
            thread {
                registry.add { survivors.incrementAndGet() }
                val registrations = (1..perThread).map { registry.add { removedFired.incrementAndGet() } }
                ready.countDown()
                ready.await()
                registrations.forEach { it.remove() }
            }
        }.forEach { it.join() }

        registry.fire(setOf("t"))
        assertEquals(threads, survivors.get(), "listeners that were not removed must all fire")
        assertFalse(removedFired.get() > 0, "a removed listener must never fire")
    }
}
