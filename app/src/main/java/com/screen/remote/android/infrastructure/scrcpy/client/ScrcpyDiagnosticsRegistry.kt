package com.screen.remote.android.infrastructure.scrcpy.client

import java.lang.ref.WeakReference

/**
 * Read-only access to scrcpy clients for debug diagnostics.
 *
 * The exported diagnostics entry point exists only in the debug manifest. Keeping
 * weak references here avoids making session internals global, retaining obsolete
 * clients after Activity recreation, or duplicating runtime state for diagnostics.
 */
object ScrcpyDiagnosticsRegistry {
    private val lock = Any()
    private val clients = mutableListOf<WeakReference<ScrcpyClient>>()

    val currentClient: ScrcpyClient?
        get() =
            synchronized(lock) {
                clients.removeAll { it.get() == null }
                val liveClients = clients.mapNotNull(WeakReference<ScrcpyClient>::get)
                selectDiagnosticsCandidate(liveClients) { it.sessionManager.currentOrNull != null }
            }

    internal fun register(client: ScrcpyClient) {
        synchronized(lock) {
            clients.removeAll { reference ->
                val existing = reference.get()
                existing == null || existing === client
            }
            clients += WeakReference(client)
        }
    }
}

internal fun <T> selectDiagnosticsCandidate(
    candidates: List<T>,
    isActive: (T) -> Boolean,
): T? = candidates.lastOrNull(isActive) ?: candidates.lastOrNull()
