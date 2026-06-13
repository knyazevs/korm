package io.github.moreirasantos.pgkn.async

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.posix.F_SETFL
import platform.posix.O_NONBLOCK
import platform.posix.POLLIN
import platform.posix.POLLOUT
import platform.posix.close
import platform.posix.fcntl
import platform.posix.pipe
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.read
import platform.posix.write
import kotlin.concurrent.AtomicReference
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

/**
 * A single-threaded I/O reactor that lets a coroutine wait for a libpq socket to become
 * readable/writable without holding the calling thread: instead of blocking in `poll`, the
 * coroutine suspends and one dedicated reactor thread polls every in-flight socket at once,
 * resuming each waiter when its socket is ready.
 *
 * Design:
 * - One reactor thread runs an infinite `poll` loop over a self-pipe plus every waiting socket.
 * - Registrations are pushed onto a lock-free Treiber stack ([intake]); the reactor drains it
 *   into a thread-confined map each iteration, so the hot map needs no locking.
 * - A self-pipe wakes `poll` when a registration arrives while it is blocked. The written byte
 *   persists in the pipe until drained, so a wake that races the drain is never lost.
 */
@OptIn(ExperimentalForeignApi::class, DelicateCoroutinesApi::class)
internal class SocketReactor : AutoCloseable {

    private class Waiter(
        val fd: Int,
        val events: Int,
        val cont: CancellableContinuation<Unit>,
        val next: Waiter?,
    )

    private val intake = AtomicReference<Waiter?>(null)
    private val wakeRead: Int
    private val wakeWrite: Int

    @Volatile
    private var running = true

    private val dispatcher = newSingleThreadContext("kormium-pg-reactor")
    private val scope = CoroutineScope(dispatcher)

    init {
        val (r, w) = memScoped {
            val fds = allocArray<IntVar>(2)
            check(pipe(fds) == 0) { "pipe() failed" }
            fds[0] to fds[1]
        }
        wakeRead = r
        wakeWrite = w
        // Non-blocking read end so drainPipe()'s read loop stops at EAGAIN instead of
        // blocking the reactor thread once the pipe is empty.
        fcntl(wakeRead, F_SETFL, O_NONBLOCK)
        scope.launch { loop() }
    }

    /** Suspends until [fd] is ready for [POLLIN] (readable). */
    suspend fun awaitReadable(fd: Int) = await(fd, POLLIN)

    /** Suspends until [fd] is ready for [POLLOUT] (writable). */
    suspend fun awaitWritable(fd: Int) = await(fd, POLLOUT)

    private suspend fun await(fd: Int, events: Int) = suspendCancellableCoroutine { cont ->
        while (true) {
            val head = intake.value
            if (intake.compareAndSet(head, Waiter(fd, events, cont, head))) break
        }
        wake()
    }

    private fun wake() = memScoped {
        val byte = allocArray<kotlinx.cinterop.ByteVar>(1)
        byte[0] = 1.toByte()
        write(wakeWrite, byte, 1.convert())
        Unit
    }

    private fun loop() {
        val waiters = HashMap<Int, Waiter>()
        while (running) {
            var node = intake.getAndSet(null)
            while (node != null) {
                waiters[node.fd] = node
                node = node.next
            }
            memScoped {
                val count = waiters.size + 1
                val fds = allocArray<pollfd>(count)
                fds[0].fd = wakeRead
                fds[0].events = POLLIN.toShort()
                val ordered = waiters.values.toList()
                ordered.forEachIndexed { i, waiter ->
                    fds[i + 1].fd = waiter.fd
                    fds[i + 1].events = waiter.events.toShort()
                }
                poll(fds, count.convert(), -1)
                if (fds[0].revents.toInt() and POLLIN != 0) drainPipe()
                ordered.forEachIndexed { i, waiter ->
                    if (fds[i + 1].revents.toInt() != 0) {
                        waiters.remove(waiter.fd)
                        if (waiter.cont.isActive) waiter.cont.resume(Unit)
                    }
                }
            }
        }
    }

    private fun drainPipe() = memScoped {
        val buf = allocArray<kotlinx.cinterop.ByteVar>(BUF)
        @Suppress("ControlFlowWithEmptyBody")
        while (read(wakeRead, buf, BUF.convert()) > 0) { }
        Unit
    }

    override fun close() {
        running = false
        wake() // unblock poll so the loop observes running == false and exits
        scope.cancel()
        dispatcher.close()
        close(wakeRead)
        close(wakeWrite)
    }

    private companion object {
        const val BUF = 64
    }
}
