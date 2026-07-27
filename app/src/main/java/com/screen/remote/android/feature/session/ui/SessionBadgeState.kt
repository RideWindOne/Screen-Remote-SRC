package com.screen.remote.android.feature.session.ui

import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport

internal data class SessionBadgeState(
    val displayTransport: ConnectionTransport,
    val status: SessionEndpointStatus,
)

enum class SessionEndpointStatus {
    ADB_CONNECTED,
    DISCOVERED,
    CONFIRMING,
    UNAVAILABLE,
}

internal fun resolveSessionBadgeState(
    sessionData: SessionData,
    connectedAdbDeviceIds: Set<String>,
    discoveredDeviceIds: Set<String>,
    confirmingDeviceIds: Set<String> = emptySet(),
): SessionBadgeState {
    val candidates = sessionData.toConnectionCandidates().sortedBy(ConnectionCandidate::priority)
    val mainCandidate =
        candidates.firstOrNull()
            ?: return SessionBadgeState(ConnectionTransport.TCP, SessionEndpointStatus.UNAVAILABLE)
    val adbConnectedCandidates =
        candidates.filter { candidate ->
            candidate.matchesAny(connectedAdbDeviceIds)
        }
    val discoveredCandidate = candidates.firstOrNull { candidate -> candidate.matchesAny(discoveredDeviceIds) }
    val confirmingCandidate = candidates.firstOrNull { candidate -> candidate.matchesAny(confirmingDeviceIds) }
    val displayCandidate =
        adbConnectedCandidates.firstOrNull()
            ?: discoveredCandidate
            ?: confirmingCandidate
            ?: mainCandidate
    val status =
        when {
            adbConnectedCandidates.isNotEmpty() -> SessionEndpointStatus.ADB_CONNECTED
            discoveredCandidate != null -> SessionEndpointStatus.DISCOVERED
            confirmingCandidate != null -> SessionEndpointStatus.CONFIRMING
            else -> SessionEndpointStatus.UNAVAILABLE
        }

    return SessionBadgeState(
        displayTransport = displayCandidate.transport,
        status = status,
    )
}

private fun ConnectionCandidate.matchesAny(deviceIds: Set<String>): Boolean =
    deviceIdentifier() in deviceIds
