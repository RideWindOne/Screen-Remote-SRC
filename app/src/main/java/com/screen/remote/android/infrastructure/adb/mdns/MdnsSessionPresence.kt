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
    val refreshing: Boolean = false,
    val onlineSessionIds: Set<String> = emptySet(),
    val onlineMdnsSerials: Set<String> = emptySet(),
    val confirmingMdnsSerials: Set<String> = emptySet(),
    val connectServices: List<MdnsDiscoveredConnectService> = emptyList(),
    val tcpServices: List<MdnsDiscoveredTcpService> = emptyList(),
)

data class MdnsDiscoveredConnectService(
    val name: String,
    val deviceSerial: String,
    val host: String = "",
    val port: Int = 0,
    val requiresPairing: Boolean,
    val previouslyPaired: Boolean,
    val confirming: Boolean = false,
)

data class MdnsDiscoveredTcpService(
    val host: String,
    val port: Int,
    val confirming: Boolean = false,
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

internal fun AdbMdnsService.toDiscoveredConnectService(
    previouslyPaired: Boolean,
    confirming: Boolean = false,
): MdnsDiscoveredConnectService =
    MdnsDiscoveredConnectService(
        name = DeviceTransportSerial.mdnsDisplayName(name),
        deviceSerial = DeviceTransportSerial.mdnsDeviceSerial(name),
        host = host,
        port = port,
        requiresPairing = serviceType == AdbMdnsServiceType.TLS_PAIRING,
        previouslyPaired = previouslyPaired,
        confirming = confirming,
    )

internal fun discoveredMdnsServices(
    connectServices: List<AdbMdnsService>,
    pairingServices: List<AdbMdnsService>,
    pairedDeviceKeys: Set<String>,
    retainedServices: List<AdbMdnsService> = emptyList(),
    refreshing: Boolean = false,
): List<MdnsDiscoveredConnectService> =
    mergeMdnsServices(
        currentServices =
            (connectServices + pairingServices).filter {
                it.serviceType == AdbMdnsServiceType.TLS_CONNECT ||
                    it.serviceType == AdbMdnsServiceType.TLS_PAIRING
            },
        retainedServices =
            retainedServices.filter {
                it.serviceType == AdbMdnsServiceType.TLS_CONNECT ||
                    it.serviceType == AdbMdnsServiceType.TLS_PAIRING
            },
        refreshing = refreshing,
    ).map { (service, confirming) ->
        service.toDiscoveredConnectService(
            previouslyPaired = DeviceTransportSerial.mdnsDeviceKey(service.name) in pairedDeviceKeys,
            confirming = confirming,
        )
    }

internal fun discoveredMdnsTcpServices(
    connectServices: List<AdbMdnsService>,
    retainedServices: List<AdbMdnsService> = emptyList(),
    refreshing: Boolean = false,
): List<MdnsDiscoveredTcpService> =
    mergeMdnsServices(
        currentServices = connectServices.filter { it.serviceType == AdbMdnsServiceType.ADB },
        retainedServices = retainedServices.filter { it.serviceType == AdbMdnsServiceType.ADB },
        refreshing = refreshing,
    ).map { (service, confirming) ->
        MdnsDiscoveredTcpService(
            host = service.host,
            port = service.port,
            confirming = confirming,
        )
    }

private fun mergeMdnsServices(
    currentServices: List<AdbMdnsService>,
    retainedServices: List<AdbMdnsService>,
    refreshing: Boolean,
): List<Pair<AdbMdnsService, Boolean>> {
    val currentKeys = currentServices.mapTo(hashSetOf(), AdbMdnsService::stableKey)
    val merged = LinkedHashMap<String, Pair<AdbMdnsService, Boolean>>()
    currentServices.forEach { service -> merged[service.stableKey()] = service to false }
    if (refreshing) {
        retainedServices.forEach { service ->
            if (service.stableKey() !in currentKeys) {
                merged.putIfAbsent(service.stableKey(), service to true)
            }
        }
    }
    return merged.values.sortedWith(
        compareBy<Pair<AdbMdnsService, Boolean>>(
            { it.first.name },
            { it.first.serviceType.name },
            { it.first.host },
            { it.first.port },
        ),
    )
}

private fun AdbMdnsService.stableKey(): String = "${serviceType.name}:${canonicalSerial()}"

internal fun mdnsRetryDelayMillis(failureCount: Int): Long =
    when {
        failureCount <= 1 -> 500L
        failureCount == 2 -> 1_000L
        failureCount == 3 -> 2_000L
        else -> 5_000L
    }

internal fun shouldRefreshMdns(
    nowMillis: Long,
    lastRefreshStartedAtMillis: Long,
    freshnessWindowMillis: Long,
): Boolean =
    lastRefreshStartedAtMillis <= 0L ||
        nowMillis - lastRefreshStartedAtMillis >= freshnessWindowMillis

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
