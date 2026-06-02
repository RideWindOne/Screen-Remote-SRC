package com.screen.remote.android.infrastructure.adb.mdns

import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.domain.model.ConnectionTransport
import dadb.android.wireless.AdbMdnsService
import dadb.android.wireless.AdbMdnsServiceType

data class MdnsTrackedSession(
    val sessionId: String,
    val sessionName: String,
    val mdnsSerial: String,
)

data class MdnsSessionPresenceState(
    val monitoring: Boolean = false,
    val loading: Boolean = false,
    val onlineSessionIds: Set<String> = emptySet(),
    val onlineMdnsSerials: Set<String> = emptySet(),
    val connectServices: List<MdnsDiscoveredConnectService> = emptyList(),
)

data class MdnsDiscoveredConnectService(
    val name: String,
    val deviceSerial: String,
    val requiresPairing: Boolean,
    val previouslyPaired: Boolean,
)

internal fun SessionData.mdnsTrackedSessions(): List<MdnsTrackedSession> =
    toConnectionCandidates()
        .asSequence()
        .filter { it.transport == ConnectionTransport.MDNS }
        .map { candidate ->
            MdnsTrackedSession(
                sessionId = id,
                sessionName = name,
                mdnsSerial = canonicalMdnsSerial(candidate.host),
            )
        }.filter { it.mdnsSerial.isNotBlank() }
        .distinctBy { it.mdnsSerial }
        .toList()

internal fun AdbMdnsService.canonicalSerial(): String =
    canonicalMdnsSerial(fullMdnsServiceName())

internal fun AdbMdnsService.fullMdnsServiceName(): String =
    "$name.${serviceType.dnsType}"

internal fun AdbMdnsService.toDiscoveredConnectService(previouslyPaired: Boolean): MdnsDiscoveredConnectService =
    MdnsDiscoveredConnectService(
        name = DeviceTransportSerial.mdnsDisplayName(name),
        deviceSerial = DeviceTransportSerial.mdnsDeviceSerial(name),
        requiresPairing = serviceType == AdbMdnsServiceType.TLS_PAIRING,
        previouslyPaired = previouslyPaired,
    )

internal fun discoveredMdnsServices(
    connectServices: List<AdbMdnsService>,
    pairingServices: List<AdbMdnsService>,
    pairedDeviceKeys: Set<String>,
): List<MdnsDiscoveredConnectService> =
    (connectServices + pairingServices).map { service ->
        service.toDiscoveredConnectService(
            previouslyPaired = DeviceTransportSerial.mdnsDeviceKey(service.name) in pairedDeviceKeys,
        )
    }

internal fun canonicalMdnsSerial(rawValue: String): String {
    val deviceSerial = DeviceTransportSerial.mdnsDeviceSerial(rawValue).lowercase()
    if (deviceSerial.isBlank()) {
        return ""
    }
    return DeviceTransportSerial.mdns(deviceSerial)
}

internal fun onlineTrackedSessions(
    trackedSessions: Collection<MdnsTrackedSession>,
    discoveredSerials: Set<String>,
): List<MdnsTrackedSession> =
    trackedSessions.filter { it.mdnsSerial in discoveredSerials }
