# Windows true-async reactor — WIP notes

Linux/macOS have a true-async libpq path (a `poll`-based socket reactor; see
`SocketReactorBase` + `UnixSocketReactor`). Windows currently falls back to the blocking
offload. This note captures the state of the Windows reactor attempt so it can be finished
on an actual Windows machine.

## Goal
A `WindowsSocketReactor : SocketReactorBase` using `WSAPoll` over the waiter sockets plus a
loopback-UDP wake socket (Winsock can't poll a pipe), making `createSocketReactor()` return a
real reactor on mingwX64 instead of `null`.

## Blocker found
Kotlin/Native's `platform.windows` does **not** expose `WSAPoll`/`WSADATA`/`SOCKET`/etc. A
custom cinterop on `winsock2.h` (`src/nativeInterop/cinterop/winsock.def`) was added, but it
produces an **empty binding package**: cinterop runs without error yet the compiler resolves
none of `SOCKET`, `socket`, `WSAPoll`, `sockaddr_in`, ...

Confirmed identical on macOS cross-compile, the Linux cross-compile CI job, **and the native
Windows CI runner** — so it is a cinterop-configuration issue, not a host difference.

Tried, none populated the package:
- `headerFilter = *`, `headerFilter = **`, omitting it, `headers = winsock2.h`
- `compilerOpts = -D_WIN32_WINNT=0x0600`, `-DWIN32_LEAN_AND_MEAN`
- explicit `-I` to the mingw sysroot include dir
- note: `klib dump-abi` / `contents` show 0 for cinterop klibs even for the *working* libpq
  one, so they are useless to introspect here — only the compiler is ground truth.

## Next ideas (to try on Windows)
1. **Manual C bridge**: ship a small `.c`/`.h` that `#include <winsock2.h>` and wraps the few
   calls we need (`WSAPoll`, `socket`/`bind`/`getsockname`/`sendto`/`recvfrom`/`ioctlsocket`/
   `closesocket`, plus the `WSAPOLLFD`/`POLLRDNORM` constants), then cinterop the local header
   (a project-owned header is matched by `headerFilter`, unlike a system header).
2. Check whether a different `headers`/`headerFilter`/`excludeSystemLibs`/`noStringConversion`
   combination makes cinterop emit the system-header declarations.
3. The draft `WindowsSocketReactor.kt` (in this branch's history) has the intended logic; only
   the symbol source needs fixing. Verify Winsock field paths on Windows (`sin_addr.S_un.S_addr`,
   `WSAData` vs `WSADATA`, `socklen` as `int`).

The architecture is ready: `createSocketReactor()` is the only seam — return a working
`WindowsSocketReactor` and the rest (driver `useConnection`, async exec) is unchanged.
