package io.github.kormium

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatcher used to run the blocking driver calls behind [suspendTransaction] /
 * [suspendAutocommit]. JVM uses `Dispatchers.IO`; Native uses `Dispatchers.Default`
 * (`Dispatchers.IO` is not public API on Native).
 */
internal expect val ioDispatcher: CoroutineDispatcher
