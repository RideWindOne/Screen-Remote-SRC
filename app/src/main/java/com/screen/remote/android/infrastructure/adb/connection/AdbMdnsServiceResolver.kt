package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.infrastructure.adb.AdbRuntimeProvider
import dadb.android.runtime.ExperimentalDadbAndroidApi
import dadb.android.wireless.AdbMdnsConfig
import dadb.android.wireless.AdbMdnsService
import dadb.android.wireless.AdbMdnsServiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalDadbAndroidApi::class)
object AdbMdnsServiceResolver {
    private const val DEFAULT_TIMEOUT_MS = 12_000L

    suspend fun resolveTlsConnectService(
        serviceName: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): AdbMdnsService =
        withContext(Dispatchers.IO) {
            val monitor =
                AdbRuntimeProvider
                    .get()
                    .createMdnsMonitor(
                        AdbMdnsConfig(serviceTypes = setOf(AdbMdnsServiceType.TLS_CONNECT)),
                    )
            monitor.use { monitor ->
                monitor.start()
                withTimeout(timeoutMs.milliseconds) {
                    monitor.state.mapNotNull { state ->
                        state.connectServices.firstOrNull { service ->
                            service.matchesTlsConnectServiceName(serviceName)
                        }
                    }
                        .first()
                }
            }
        }

    private fun AdbMdnsService.matchesTlsConnectServiceName(rawServiceName: String): Boolean {
        val instanceName =
            DeviceTransportSerial.mdnsDeviceSerial(rawServiceName)
        return DeviceTransportSerial.mdnsDeviceSerial(name).equals(instanceName, ignoreCase = true)
    }
}
