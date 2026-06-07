package io.github.knyazevs.korm

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Offload dispatcher for the blocking JDBC drivers behind suspend transactions.
 * Backed by VIRTUAL THREADS (Loom, requires JDK 21+): a coroutine that blocks on a
 * driver call unmounts its carrier thread, so blocking is cheap and concurrency is
 * bounded only by the connection pool — not by a fixed thread pool. This is what makes
 * the offload (strategy B) path scale without a truly async driver.
 */
internal actual val ioDispatcher: CoroutineDispatcher =
    Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
