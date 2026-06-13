package io.github.moreirasantos.pgkn.async

import kotlinx.cinterop.*
import winsock.*
import kotlin.concurrent.Volatile

/**
 * Windows reactor: WSAPoll over the waiter sockets plus a loopback UDP socket used as the wake
 * channel (Winsock cannot poll a pipe, so signalling is a self-addressed datagram). FIONBIO via
 * ioctlsocket makes the wake socket non-blocking. Winsock2 comes from the `winsock` cinterop —
 * Kotlin/Native's platform.windows does not expose WSAPoll.
 */
@OptIn(ExperimentalForeignApi::class)
internal class WindowsSocketReactor : SocketReactorBase() {

    @Volatile
    private var wakeSock: SOCKET = INVALID_SOCKET

    // Kept alive for the reactor's lifetime: signalWake sends a datagram to this address.
    private val wakeAddr = nativeHeap.alloc<sockaddr_in>()

    override fun openWake() {
        memScoped {
            val wsa = alloc<WSAData>()
            WSAStartup(0x0202u.convert(), wsa.ptr) // request Winsock 2.2
        }
        val s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        wakeAddr.sin_family = AF_INET.convert()
        wakeAddr.sin_addr.S_un.S_addr = inet_addr("127.0.0.1")
        wakeAddr.sin_port = 0u
        bind(s, wakeAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
        // Read back the OS-assigned port so signalWake can target this socket.
        memScoped {
            val len = alloc<IntVar>()
            len.value = sizeOf<sockaddr_in>().convert()
            getsockname(s, wakeAddr.ptr.reinterpret(), len.ptr)
        }
        memScoped {
            val mode = alloc<u_longVar>()
            mode.value = 1u
            ioctlsocket(s, FIONBIO.convert(), mode.ptr)
        }
        wakeSock = s
    }

    override fun signalWake() = memScoped {
        val byte = alloc<ByteVar>()
        byte.value = 1
        sendto(wakeSock, byte.ptr, 1, 0, wakeAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
        Unit
    }

    override fun pollWaiters(fds: IntArray, events: IntArray): BooleanArray = memScoped {
        val count = fds.size + 1
        val arr = allocArray<WSAPOLLFD>(count)
        arr[0].fd = wakeSock
        arr[0].events = POLLRDNORM.toShort()
        for (i in fds.indices) {
            arr[i + 1].fd = fds[i].convert()
            arr[i + 1].events = (if (events[i] == EV_WRITE) POLLWRNORM else POLLRDNORM).toShort()
        }
        WSAPoll(arr, count.convert(), -1)
        if (arr[0].revents.toInt() and POLLRDNORM != 0) drainWake()
        val readyMask = POLLRDNORM or POLLWRNORM or POLLERR or POLLHUP
        BooleanArray(fds.size) { (arr[it + 1].revents.toInt() and readyMask) != 0 }
    }

    private fun drainWake() = memScoped {
        val buf = allocArray<ByteVar>(BUFFER)
        @Suppress("ControlFlowWithEmptyBody")
        while (recvfrom(wakeSock, buf, BUFFER, 0, null, null) > 0) { }
        Unit
    }

    override fun closeWake() {
        if (wakeSock != INVALID_SOCKET) closesocket(wakeSock)
        nativeHeap.free(wakeAddr.ptr)
        WSACleanup()
    }

    private companion object {
        const val BUFFER = 64
    }
}

internal actual fun createSocketReactor(): SocketReactorBase = WindowsSocketReactor().apply { start() }
