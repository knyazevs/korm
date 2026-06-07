package io.github.knyazevs.korm

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Dispatchers.IO is not public API on Kotlin/Native; Default is a multi-thread pool.
internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
