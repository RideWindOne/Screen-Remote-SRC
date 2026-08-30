package com.screen.remote.android.infrastructure.adb.connection

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.screen.remote.android.app.ScreenRemoteApp
import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.infrastructure.adb.AdbRuntimeProvider
import dadb.Dadb
import dadb.android.runtime.ExperimentalDadbAndroidApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

data class AdbLatencyRoundResult(
    val round: Int,
    val connectMillis: Double? = null,
    val shellRoundTripMillis: List<Double> = emptyList(),
    val resolvedEndpoint: String? = null,
    val failureMillis: Double? = null,
    val error: String? = null,
) {
    val successful: Boolean
        get() = error == null && connectMillis != null
}

/**
 * 临时连接测速器。
 *
 * 每一轮都创建并关闭独立 Dadb，不写入正式连接池，也不触发 scrcpy 会话。
 */
@OptIn(ExperimentalDadbAndroidApi::class)
class AdbLatencyBenchmark {
    private val context: Context
        get() = ScreenRemoteApp.instance

    private val connectionManager: AdbConnectionManager
        get() = AdbConnectionManager.getInstance(context)

    suspend fun prepareUsbCandidates(
        candidates: List<ConnectionCandidate>,
    ): Map<ConnectionCandidate, Result<UsbDevice>> =
        withContext(Dispatchers.IO) {
            val usbCandidates = candidates.filter { it.transport == ConnectionTransport.USB }
            if (usbCandidates.isEmpty()) return@withContext emptyMap()

            val scanned = connectionManager.scanUsbDevices().getOrElse { error ->
                return@withContext usbCandidates.associateWith { Result.failure(error) }
            }

            val prepared =
                usbCandidates.associateWith { candidate ->
                    runCatching {
                        val requestedId = DeviceTransportSerial.stripUsbPrefix(candidate.host)
                        val deviceInfo =
                            scanned.firstOrNull { info ->
                                info.serialNumber == requestedId ||
                                    info.deviceName == requestedId ||
                                    info.device.deviceName == requestedId
                            } ?: scanned.singleOrNull()
                            ?: error("USB ADB device not found: $requestedId")

                        val granted = connectionManager.requestUsbPermission(deviceInfo.device).getOrThrow()
                        check(granted) { "USB permission not granted: $requestedId" }
                        deviceInfo.device
                    }
                }

            // Permission is settled before the test starts. The benchmark then owns USB, so any
            // formal pooled USB connection must be released before independent rounds begin.
            connectionManager.getAllConnections()
                .filter { it.deviceId.startsWith("usb:") }
                .forEach { connection ->
                    connectionManager.disconnectDevice(connection.deviceId)
                }
            prepared
        }

    suspend fun runRound(
        candidate: ConnectionCandidate,
        round: Int,
        shellRounds: Int = DEFAULT_SHELL_ROUNDS,
        usbDevice: UsbDevice? = null,
        shellPassword: String = "",
    ): AdbLatencyRoundResult =
        withContext(Dispatchers.IO) {
            var dadb: Dadb? = null
            var resolvedEndpoint: String? = null
            val connectStartedAt = System.nanoTime()
            try {
                val endpoint =
                    when (candidate.transport) {
                        ConnectionTransport.MDNS -> {
                            val service =
                                AdbMdnsServiceResolver.resolveTlsConnectService(
                                    serviceName = candidate.host,
                                    timeoutMs = MDNS_RESOLVE_TIMEOUT_MS,
                                )
                            service.host to service.port
                        }

                        ConnectionTransport.TCP -> candidate.host to candidate.port
                        ConnectionTransport.USB -> null
                    }
                resolvedEndpoint =
                    endpoint?.let { "${it.first}:${it.second}" }
                        ?: usbDevice?.deviceName
                            ?: candidate.host

                dadb =
                    if (candidate.transport == ConnectionTransport.USB) {
                        val preparedDevice =
                            requireNotNull(usbDevice) { "USB device permission preparation is incomplete" }
                        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                        AdbRuntimeProvider.get().createUsbDadb(
                            usbManager = usbManager,
                            usbDevice = preparedDevice,
                            description = candidate.host,
                            features = Dadb.connectFeatures(withDelayedAck = true),
                            passwordProvider = passwordProviderForBenchmark(shellPassword),
                        )
                    } else {
                        val networkEndpoint = requireNotNull(endpoint)
                        AdbRuntimeProvider.get().connectNetworkDadb(
                            host = networkEndpoint.first,
                            port = networkEndpoint.second,
                            connectTimeout = CONNECT_TIMEOUT_MS,
                            socketTimeout = SOCKET_TIMEOUT_MS,
                            features = Dadb.connectFeatures(withDelayedAck = true),
                            passwordProvider = passwordProviderForBenchmark(shellPassword),
                        )
                    }

                // 连接耗时只统计实际 ADB 建链；mDNS 另包含服务解析。
                val connectMillis = elapsedMillis(connectStartedAt)
                val shellExecutor = AdbConnectionShellExecutor(
                    dadb = dadb,
                    deviceId = candidate.deviceIdentifier(),
                )

                val shellSamples =
                    buildList(shellRounds) {
                        repeat(shellRounds) { sampleIndex ->
                            val token = "sr-latency-$round-$sampleIndex"
                            val startedAt = System.nanoTime()
                            val response =
                                withTimeout(SHELL_TIMEOUT_MS.milliseconds) {
                                    shellExecutor.execute(
                                        command = "echo -n $token",
                                        retryOnFailure = false,
                                    ).getOrThrow()
                                }
                            check(response.trim() == token) {
                                "Invalid RTT response: output=${response.trim()}"
                            }
                            add(elapsedMillis(startedAt))
                        }
                    }

                AdbLatencyRoundResult(
                    round = round,
                    connectMillis = connectMillis,
                    shellRoundTripMillis = shellSamples,
                    resolvedEndpoint = resolvedEndpoint,
                )
            } catch (error: TimeoutCancellationException) {
                AdbLatencyRoundResult(
                    round = round,
                    resolvedEndpoint = resolvedEndpoint,
                    failureMillis = elapsedMillis(connectStartedAt),
                    error = "Timeout",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AdbLatencyRoundResult(
                    round = round,
                    resolvedEndpoint = resolvedEndpoint,
                    failureMillis = elapsedMillis(connectStartedAt),
                    error = error.shortMessage(),
                )
            } finally {
                runCatching { dadb?.close() }
            }
        }

    companion object {
        const val DEFAULT_CONNECT_ROUNDS = 10
        const val DEFAULT_SHELL_ROUNDS = 10
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val SOCKET_TIMEOUT_MS = 5_000
        private const val SHELL_TIMEOUT_MS = 3_000L
        private const val MDNS_RESOLVE_TIMEOUT_MS = 5_000L
    }
}

private fun elapsedMillis(startedAtNanos: Long): Double =
    (System.nanoTime() - startedAtNanos) / 1_000_000.0

private fun passwordProviderForBenchmark(shellPassword: String): (() -> String)? =
    if (shellPassword.isNotBlank()) { { shellPassword } } else null

private fun Throwable.shortMessage(): String {
    val detail = message?.trim().orEmpty()
    return if (detail.isBlank()) javaClass.simpleName else "${javaClass.simpleName}: $detail"
}
