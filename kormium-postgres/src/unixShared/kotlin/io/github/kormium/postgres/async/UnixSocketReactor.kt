package io.github.kormium.postgres.async

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import platform.posix.F_SETFL
import platform.posix.O_NONBLOCK
import platform.posix.POLLERR
import platform.posix.POLLHUP
import platform.posix.POLLIN
import platform.posix.POLLOUT
import platform.posix.close
import platform.posix.fcntl
import platform.posix.pipe
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.read
import platform.posix.write
import kotlin.concurrent.Volatile

/** Unix reactor: poll(2) over the waiter sockets plus a self-pipe used as the wake channel. */
@OptIn(ExperimentalForeignApi::class)
internal class UnixSocketReactor : SocketReactorBase() {

    @Volatile
    private var wakeRead = -1

    @Volatile
    private var wakeWrite = -1

    override fun openWake() {
        val (r, w) = memScoped {
            val fds = allocArray<IntVar>(2)
            check(pipe(fds) == 0) { "pipe() failed" }
            fds[0] to fds[1]
        }
        // Non-blocking read end so draining the wake byte(s) never blocks the reactor.
        fcntl(r, F_SETFL, O_NONBLOCK)
        wakeRead = r
        wakeWrite = w
    }

    override fun signalWake() = memScoped {
        val byte = allocArray<kotlinx.cinterop.ByteVar>(1)
        byte[0] = 1.toByte()
        write(wakeWrite, byte, 1.convert())
        Unit
    }

    override fun pollWaiters(fds: IntArray, events: IntArray): BooleanArray = memScoped {
        val count = fds.size + 1
        val poll = allocArray<pollfd>(count)
        poll[0].fd = wakeRead
        poll[0].events = POLLIN.toShort()
        for (i in fds.indices) {
            poll[i + 1].fd = fds[i]
            poll[i + 1].events = (if (events[i] == EV_WRITE) POLLOUT else POLLIN).toShort()
        }
        poll(poll, count.convert(), -1)
        if (poll[0].revents.toInt() and POLLIN != 0) drainWake()
        val readyMask = POLLIN or POLLOUT or POLLERR or POLLHUP
        BooleanArray(fds.size) { (poll[it + 1].revents.toInt() and readyMask) != 0 }
    }

    private fun drainWake() = memScoped {
        val buf = allocArray<kotlinx.cinterop.ByteVar>(BUFFER)
        @Suppress("ControlFlowWithEmptyBody")
        while (read(wakeRead, buf, BUFFER.convert()) > 0) { }
        Unit
    }

    override fun closeWake() {
        if (wakeRead >= 0) close(wakeRead)
        if (wakeWrite >= 0) close(wakeWrite)
    }

    private companion object {
        const val BUFFER = 64
    }
}

internal actual fun createSocketReactor(): SocketReactorBase? = UnixSocketReactor().apply { start() }
