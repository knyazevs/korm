package io.github.kormium.postgres.async

/**
 * Windows has no socket reactor yet (Kotlin/Native does not expose WSAPoll and a winsock2
 * cinterop produced empty bindings on every host — see the feat/native-async-windows-wip
 * branch). Returning null makes the driver use the blocking offload for useConnection on
 * Windows, exactly as it did before the async path existed. No regression; true-async on
 * Windows is a follow-up.
 */
internal actual fun createSocketReactor(): SocketReactorBase? = null
