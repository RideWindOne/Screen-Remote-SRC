package com.screen.remote.android.core.domain.model

import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.core.common.util.HostPort
import com.screen.remote.android.core.common.util.normalizeEndpointHost
import com.screen.remote.android.core.common.util.parseHostPort

enum class ConnectionTransport {
    TCP,
    USB,
    MDNS,
}

data class ConnectionCandidate(
    val transport: ConnectionTransport,
    val host: String,
    val port: Int = 0,
    val priority: Int = 0,
    val lastSuccessfulAtMillis: Long = 0L,
    val failureCount: Int = 0,
) {
    fun deviceIdentifier(): String =
        when (transport) {
            ConnectionTransport.USB -> DeviceTransportSerial.usb(host)
            ConnectionTransport.MDNS -> DeviceTransportSerial.mdns(DeviceTransportSerial.mdnsDeviceSerial(host))
            ConnectionTransport.TCP -> DeviceTransportSerial.tcp(normalizeEndpointHost(host), port.toString())
        }
}

data class ConnectionCandidateAttempt(
    val candidate: ConnectionCandidate,
    val reachable: Boolean,
    val latencyMillis: Long? = null,
)

private fun transportPriority(transport: ConnectionTransport): Int =
    when (transport) {
        ConnectionTransport.USB -> 0
        ConnectionTransport.MDNS -> 1
        ConnectionTransport.TCP -> 2
    }

fun parseTcpHostPort(rawValue: String): HostPort? = parseHostPort(DeviceTransportSerial.stripTcpPrefix(rawValue))

fun parseSessionAddressCandidate(rawValue: String): ConnectionCandidate? {
    val value = rawValue.trim()
    if (value.isBlank()) return null

    return when {
        value.startsWith("usb:", ignoreCase = true) -> {
            val host = DeviceTransportSerial.stripUsbPrefix(value)
            if (host.isBlank()) null else ConnectionCandidate(ConnectionTransport.USB, host, 0)
        }

        value.startsWith("mdns:", ignoreCase = true) -> {
            val host = DeviceTransportSerial.mdnsDeviceSerial(value)
            if (host.isBlank()) null else ConnectionCandidate(ConnectionTransport.MDNS, host)
        }

        value.startsWith("tcp:", ignoreCase = true) ->
            parseTcpHostPort(value)?.let {
                ConnectionCandidate(ConnectionTransport.TCP, it.host, it.port)
            }

        else ->
            parseHostPort(value)?.let {
                ConnectionCandidate(ConnectionTransport.TCP, it.host, it.port)
            }
    }
}

fun ConnectionCandidate.toAddressEndpoint(): String =
    when (transport) {
        ConnectionTransport.USB -> DeviceTransportSerial.usb(host)
        ConnectionTransport.MDNS -> DeviceTransportSerial.mdns(DeviceTransportSerial.mdnsDeviceSerial(host))
        ConnectionTransport.TCP -> DeviceTransportSerial.tcp(host, port)
    }

fun formatSessionAddress(
    transport: ConnectionTransport,
    host: String,
    port: Int = 0,
): String = ConnectionCandidate(transport = transport, host = host, port = port).toAddressEndpoint()

fun parseTcpConnectionCandidates(
    rawEndpoints: String,
): List<ConnectionCandidate> =
    rawEndpoints
        .split(',', '\n', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapIndexedNotNull { index, raw ->
            val parsed = parseHostPort(raw) ?: return@mapIndexedNotNull null
            ConnectionCandidate(
                transport = ConnectionTransport.TCP,
                host = normalizeEndpointHost(parsed.host),
                port = parsed.port,
                priority = index,
            )
        }
        .distinctBy { "${it.transport}:${it.host}:${it.port}" }

fun orderedConnectionCandidates(attempts: List<ConnectionCandidateAttempt>): List<ConnectionCandidate> =
    attempts
        .sortedWith(
            compareBy<ConnectionCandidateAttempt> { transportPriority(it.candidate.transport) }
                .thenBy { it.candidate.priority },
        )
        .map { it.candidate }

fun markConnectionCandidateSuccess(
    candidates: List<ConnectionCandidate>,
    successful: ConnectionCandidate,
    nowMillis: Long,
): List<ConnectionCandidate> =
    candidates.map { candidate ->
        if (candidate.transport == successful.transport &&
            candidate.host == successful.host &&
            candidate.port == successful.port
        ) {
            candidate.copy(lastSuccessfulAtMillis = nowMillis, failureCount = 0)
        } else {
            candidate
        }
    }

fun markConnectionCandidateFailure(
    candidates: List<ConnectionCandidate>,
    failed: ConnectionCandidate,
): List<ConnectionCandidate> =
    candidates.map { candidate ->
        if (candidate.transport == failed.transport &&
            candidate.host == failed.host &&
            candidate.port == failed.port
        ) {
            candidate.copy(failureCount = candidate.failureCount + 1)
        } else {
            candidate
        }
    }
