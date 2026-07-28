package com.screen.remote.android.infrastructure.adb.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.formatHostPort
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.domain.model.ConnectionTransport
import java.io.File
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NearbyAdbProtocol {
    TCP,
    TLS,
}

data class NearbyTcpAdbDevice(
    val host: String,
    val port: Int,
    val protocol: NearbyAdbProtocol,
)

enum class NearbyAdbScanStage {
    DISCOVERING_HOSTS,
    CHECKING_HISTORY,
    CHECKING_COMMON_PORTS,
    CHECKING_DYNAMIC_PORTS,
    COMPLETE,
}

data class NearbyAdbScanProgress(
    val stage: NearbyAdbScanStage,
    val completed: Int = 0,
    val total: Int = 0,
    val candidateHosts: Int = 0,
)

sealed interface NearbyAdbScanEvent {
    data class Progress(val value: NearbyAdbScanProgress) : NearbyAdbScanEvent

    data class DeviceFound(val device: NearbyTcpAdbDevice) : NearbyAdbScanEvent

    data class Failed(val message: String) : NearbyAdbScanEvent
}

class NearbyAdbScanner(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun scan(
        sessions: List<SessionData>,
    ): Flow<NearbyAdbScanEvent> =
        channelFlow {
            try {
                send(NearbyAdbScanEvent.Progress(NearbyAdbScanProgress(NearbyAdbScanStage.DISCOVERING_HOSTS)))
                val network = currentIpv4Network(appContext)
                val historicalEndpoints = historicalTcpEndpoints(sessions)
                val subnetHosts = network?.candidateHosts.orEmpty()
                val localHost = network?.localAddress
                val dynamicScanExcludedHosts =
                    setOfNotNull(network?.gatewayAddress)
                val commonScanExcludedHosts = setOfNotNull(network?.gatewayAddress)

                warmNeighborTable(subnetHosts)
                val reachableHosts =
                    buildList {
                        localHost?.let(::add)
                        addAll(discoverActiveHosts(subnetHosts))
                    }.distinct()
                        .filterNot { it in commonScanExcludedHosts }
                val activeHosts =
                    buildList {
                        localHost?.let(::add)
                        addAll(
                            filterDynamicScanCandidates(
                                reachableHosts.filterNot { it == localHost || it in dynamicScanExcludedHosts },
                            ),
                        )
                    }.distinct()
                        .take(MAX_DYNAMIC_SCAN_HOSTS)
                val historicalHosts = historicalEndpoints.mapTo(linkedSetOf()) { it.host }
                val commonPortHosts =
                    buildList {
                        localHost?.let(::add)
                        addAll(historicalHosts)
                        addAll(subnetHosts)
                    }.filterNot { canonicalIpv4Address(it) in commonScanExcludedHosts }
                        .distinct()
                val localCommonPortHosts =
                    listOfNotNull(localHost)
                        .filter { it in commonPortHosts }
                val remoteCommonPortHosts = commonPortHosts.filterNot { it in localCommonPortHosts }
                val localDynamicPortHosts = listOfNotNull(localHost).filter { it in activeHosts }
                val remoteDynamicPortHosts = activeHosts.filterNot { it in localDynamicPortHosts }

                LogManager.i(
                    LogTags.ADB_CONNECTION,
                    "Nearby ADB scan prepared: localHost=${network?.localAddress.orEmpty()} subnetHosts=${subnetHosts.size} reachableHosts=${reachableHosts.size} dynamicCandidates=${activeHosts.size} history=${historicalEndpoints.size}",
                )

                val foundKeys = Collections.synchronizedSet(mutableSetOf<String>())
                suspend fun publish(device: NearbyTcpAdbDevice) {
                    if (foundKeys.add(formatHostPort(device.host, device.port))) {
                        send(NearbyAdbScanEvent.DeviceFound(device))
                    }
                }

                scanExplicitEndpoints(
                    endpoints = historicalEndpoints,
                    stage = NearbyAdbScanStage.CHECKING_HISTORY,
                    onProgress = { send(NearbyAdbScanEvent.Progress(it)) },
                    onFound = ::publish,
                )

                scanPortRange(
                    hosts = localCommonPortHosts,
                    ports = COMMON_ADB_PORTS,
                    stage = NearbyAdbScanStage.CHECKING_COMMON_PORTS,
                    workers = LOCAL_COMMON_SCAN_WORKERS,
                    onProgress = { send(NearbyAdbScanEvent.Progress(it)) },
                    onFound = ::publish,
                )

                val localDynamicScanComplete = AtomicBoolean(localDynamicPortHosts.isEmpty())
                coroutineScope {
                    launch {
                        scanPortRange(
                            hosts = localDynamicPortHosts,
                            ports = DYNAMIC_ADB_PORTS,
                            stage = NearbyAdbScanStage.CHECKING_DYNAMIC_PORTS,
                            workers = LOCAL_DYNAMIC_SCAN_WORKERS,
                            onProgress = { send(NearbyAdbScanEvent.Progress(it)) },
                            onFound = ::publish,
                        )
                        localDynamicScanComplete.set(true)
                    }
                    launch {
                        val remoteProgress: suspend (NearbyAdbScanProgress) -> Unit = { value ->
                            if (localDynamicScanComplete.get()) {
                                send(NearbyAdbScanEvent.Progress(value))
                            }
                        }
                        scanPortRange(
                            hosts = remoteCommonPortHosts,
                            ports = COMMON_ADB_PORTS,
                            stage = NearbyAdbScanStage.CHECKING_COMMON_PORTS,
                            workers = REMOTE_SCAN_WORKERS,
                            onProgress = remoteProgress,
                            onFound = ::publish,
                        )
                        scanPortRange(
                            hosts = remoteDynamicPortHosts,
                            ports = DYNAMIC_ADB_PORTS,
                            stage = NearbyAdbScanStage.CHECKING_DYNAMIC_PORTS,
                            workers = REMOTE_SCAN_WORKERS,
                            onProgress = remoteProgress,
                            onFound = ::publish,
                        )
                    }
                }

                send(
                    NearbyAdbScanEvent.Progress(
                        NearbyAdbScanProgress(
                            stage = NearbyAdbScanStage.COMPLETE,
                            candidateHosts = activeHosts.size,
                        ),
                    ),
                )
                LogManager.i(LogTags.ADB_CONNECTION, "Nearby ADB scan completed: devices=${foundKeys.size}")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "Nearby ADB scan failed", error)
                send(NearbyAdbScanEvent.Failed(error.message ?: "Nearby ADB scan failed"))
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun scanExplicitEndpoints(
        endpoints: List<HistoricalTcpEndpoint>,
        stage: NearbyAdbScanStage,
        onProgress: suspend (NearbyAdbScanProgress) -> Unit,
        onFound: suspend (NearbyTcpAdbDevice) -> Unit,
    ) {
        val completed = AtomicInteger(0)
        onProgress(NearbyAdbScanProgress(stage, total = endpoints.size))
        coroutineScope {
            endpoints
                .map { endpoint ->
                    async(SCAN_DISPATCHER) {
                        probeAdbEndpoint(endpoint.host, endpoint.port)?.let { protocol ->
                            onFound(NearbyTcpAdbDevice(endpoint.host, endpoint.port, protocol))
                        }
                        val count = completed.incrementAndGet()
                        if (count == endpoints.size || count % PROGRESS_BATCH_SIZE == 0) {
                            onProgress(NearbyAdbScanProgress(stage, count, endpoints.size))
                        }
                    }
                }.awaitAll()
        }
    }

    private suspend fun scanPortRange(
        hosts: List<String>,
        ports: IntRange,
        stage: NearbyAdbScanStage,
        workers: Int = SCAN_WORKERS,
        onProgress: suspend (NearbyAdbScanProgress) -> Unit,
        onFound: suspend (NearbyTcpAdbDevice) -> Unit,
    ) {
        val portCount = ports.count()
        val total = hosts.size * portCount
        onProgress(NearbyAdbScanProgress(stage, total = total, candidateHosts = hosts.size))
        if (total == 0) return

        val nextIndex = AtomicInteger(0)
        val completed = AtomicInteger(0)
        val foundHosts = Collections.synchronizedSet(mutableSetOf<String>())
        coroutineScope {
            repeat(minOf(workers, total)) {
                launch(SCAN_DISPATCHER) {
                    while (isActive) {
                        val index = nextIndex.getAndIncrement()
                        if (index >= total) break
                        val host = hosts[index % hosts.size]
                        val port = ports.first + (index / hosts.size)
                        if (host !in foundHosts) {
                            probeAdbEndpoint(host, port)?.let { protocol ->
                                if (foundHosts.add(host)) {
                                    onFound(NearbyTcpAdbDevice(host, port, protocol))
                                }
                            }
                        }
                        val count = completed.incrementAndGet()
                        if (count == total || count % PROGRESS_BATCH_SIZE == 0) {
                            onProgress(NearbyAdbScanProgress(stage, count, total, hosts.size))
                        }
                    }
                }
            }
        }
    }

    private suspend fun discoverActiveHosts(hosts: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            val arpHosts = readArpHosts().filter { it in hosts }.toMutableSet()
            if (arpHosts.size < MIN_ARP_HOSTS_BEFORE_REACHABILITY_FALLBACK) {
                coroutineScope {
                    hosts
                        .chunked(REACHABILITY_BATCH_SIZE)
                        .flatMap { batch ->
                            batch
                                .map { host ->
                                    async {
                                        host.takeIf {
                                            runCatching {
                                                InetAddress.getByName(host).isReachable(REACHABILITY_TIMEOUT_MS)
                                            }.getOrDefault(false)
                                        }
                                    }
                                }.awaitAll()
                                .filterNotNull()
                        }.forEach(arpHosts::add)
                }
            }
            arpHosts.sortedBy(::ipv4SortKey)
        }

    private suspend fun filterDynamicScanCandidates(hosts: List<String>): List<String> =
        coroutineScope {
            hosts
                .map { host ->
                    async(SCAN_DISPATCHER) {
                        host to NON_ANDROID_HINT_PORTS.any { port -> isTcpPortOpen(host, port) }
                    }
                }.awaitAll()
                .filterNot { (_, hasNonAndroidService) -> hasNonAndroidService }
                .map { (host, _) -> host }
        }

    private suspend fun warmNeighborTable(hosts: List<String>) {
        if (hosts.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                DatagramSocket().use { socket ->
                    val payload = byteArrayOf(0)
                    hosts.forEach { host ->
                        socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName(host), NEIGHBOR_WARMUP_PORT))
                    }
                }
            }
            delay(NEIGHBOR_SETTLE_MS)
        }
    }

    companion object {
        val COMMON_ADB_PORTS = 5550..5560
        val DYNAMIC_ADB_PORTS = 30000..65535
        val NON_ANDROID_HINT_PORTS = listOf(515, 548, 631, 9100, 62078)

        private const val MAX_DYNAMIC_SCAN_HOSTS = 32
        private const val SCAN_WORKERS = 128
        private const val LOCAL_COMMON_SCAN_WORKERS = 11
        private const val LOCAL_DYNAMIC_SCAN_WORKERS = 64
        private const val REMOTE_SCAN_WORKERS = SCAN_WORKERS - LOCAL_DYNAMIC_SCAN_WORKERS
        private const val PROGRESS_BATCH_SIZE = 128
        private const val REACHABILITY_BATCH_SIZE = 64
        private const val REACHABILITY_TIMEOUT_MS = 160
        private const val MIN_ARP_HOSTS_BEFORE_REACHABILITY_FALLBACK = 2
        private const val NEIGHBOR_WARMUP_PORT = 9
        private const val NEIGHBOR_SETTLE_MS = 250L
        private val SCAN_DISPATCHER = Dispatchers.IO.limitedParallelism(SCAN_WORKERS)
    }
}

private data class HistoricalTcpEndpoint(
    val host: String,
    val port: Int,
)

private data class Ipv4Network(
    val localAddress: String,
    val gatewayAddress: String?,
    val candidateHosts: List<String>,
)

private fun historicalTcpEndpoints(sessions: List<SessionData>): List<HistoricalTcpEndpoint> =
    sessions
        .flatMap(SessionData::toConnectionCandidates)
        .asSequence()
        .filter { it.transport == ConnectionTransport.TCP && it.port in 1..65535 }
        .sortedByDescending { it.lastSuccessfulAtMillis }
        .map { HistoricalTcpEndpoint(it.host, it.port) }
        .distinct()
        .toList()

private fun currentIpv4Network(context: Context): Ipv4Network? {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = manager.activeNetwork ?: return null
    val properties = manager.getLinkProperties(activeNetwork) ?: return null
    val linkAddress =
        properties.linkAddresses
            .firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?: return null
    val localAddress = linkAddress.address.hostAddress ?: return null
    val gateway = properties.defaultIpv4Gateway()
    return Ipv4Network(
        localAddress = localAddress,
        gatewayAddress = gateway,
        candidateHosts = ipv4SubnetHosts(linkAddress, MAX_SUBNET_HOSTS),
    )
}

private fun LinkProperties.defaultIpv4Gateway(): String? =
    routes
        .firstOrNull { route ->
            route.isDefaultRoute && route.gateway is Inet4Address
        }?.gateway
        ?.hostAddress

internal fun ipv4SubnetHosts(
    linkAddress: LinkAddress,
    maxHosts: Int,
): List<String> {
    val address = linkAddress.address as? Inet4Address ?: return emptyList()
    val bytes = address.address
    val addressValue = ByteBuffer.wrap(bytes).int.toLong() and 0xffffffffL
    val effectivePrefix = linkAddress.prefixLength.coerceIn(24, 30)
    val hostBits = 32 - effectivePrefix
    val hostMask = (1L shl hostBits) - 1L
    val network = addressValue and hostMask.inv()
    val broadcast = network or hostMask
    return ((network + 1) until broadcast)
        .asSequence()
        .filter { it != addressValue }
        .take(maxHosts)
        .map(::ipv4String)
        .toList()
}

private fun ipv4String(value: Long): String =
    listOf(24, 16, 8, 0).joinToString(".") { shift -> ((value shr shift) and 0xff).toString() }

private fun ipv4SortKey(host: String): Long =
    canonicalIpv4Address(host)
        ?.split('.')
        ?.fold(0L) { result, octet -> (result shl 8) or octet.toLong() }
        ?: Long.MAX_VALUE

private fun canonicalIpv4Address(host: String): String? =
    runCatching { InetAddress.getByName(host) }
        .getOrNull()
        ?.takeIf { it is Inet4Address }
        ?.hostAddress

private fun readArpHosts(): Set<String> =
    runCatching {
        File("/proc/net/arp")
            .useLines { lines ->
                lines
                    .drop(1)
                    .map { it.trim().split(Regex("\\s+")) }
                    .filter { columns ->
                        columns.size >= 6 &&
                            columns[2].removePrefix("0x").toIntOrNull(16)?.and(0x2) != 0 &&
                            columns[3] != "00:00:00:00:00:00"
                    }.map { it[0] }
                    .toSet()
            }
    }.getOrDefault(emptySet())

private fun isTcpPortOpen(
    host: String,
    port: Int,
): Boolean =
    runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), CLASSIFICATION_CONNECT_TIMEOUT_MS)
            true
        }
    }.getOrDefault(false)

internal fun probeAdbEndpoint(
    host: String,
    port: Int,
): NearbyAdbProtocol? =
    try {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            socket.getOutputStream().write(buildAdbConnectPacket())
            socket.getOutputStream().flush()
            val header = ByteArray(ADB_HEADER_SIZE)
            var offset = 0
            while (offset < header.size) {
                val count = socket.getInputStream().read(header, offset, header.size - offset)
                if (count < 0) return null
                offset += count
            }
            parseAdbResponseHeader(header)
        }
    } catch (_: ConnectException) {
        null
    } catch (_: Exception) {
        null
    }

internal fun buildAdbConnectPacket(): ByteArray {
    val payload = "host::".toByteArray(Charsets.UTF_8)
    val checksum = payload.sumOf { it.toInt() and 0xff }
    return ByteBuffer
        .allocate(ADB_HEADER_SIZE + payload.size)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(ADB_CNXN)
        .putInt(ADB_VERSION)
        .putInt(ADB_MAX_DATA)
        .putInt(payload.size)
        .putInt(checksum)
        .putInt(ADB_CNXN xor -1)
        .put(payload)
        .array()
}

internal fun parseAdbResponseHeader(header: ByteArray): NearbyAdbProtocol? {
    if (header.size < ADB_HEADER_SIZE) return null
    val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
    val command = buffer.int
    buffer.int
    buffer.int
    val payloadLength = buffer.int
    buffer.int
    val magic = buffer.int
    if (magic != command xor -1 || payloadLength !in 0..ADB_MAX_RESPONSE_PAYLOAD) return null
    return when (command) {
        ADB_STLS -> NearbyAdbProtocol.TLS
        ADB_AUTH, ADB_CNXN -> NearbyAdbProtocol.TCP
        else -> null
    }
}

private const val ADB_HEADER_SIZE = 24
private const val ADB_CNXN = 0x4e584e43
private const val ADB_AUTH = 0x48545541
private const val ADB_STLS = 0x534c5453
private const val ADB_VERSION = 0x01000001
private const val ADB_MAX_DATA = 256 * 1024
private const val ADB_MAX_RESPONSE_PAYLOAD = 1024 * 1024
private const val CONNECT_TIMEOUT_MS = 90
private const val READ_TIMEOUT_MS = 180
private const val CLASSIFICATION_CONNECT_TIMEOUT_MS = 70
private const val MAX_SUBNET_HOSTS = 254
