package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.infrastructure.adb.mdns.MdnsSessionDiscoveryManager
import dadb.android.wireless.AdbMdnsService

object AdbMdnsServiceResolver {
    private const val DEFAULT_TIMEOUT_MS = 12_000L

    suspend fun resolveTlsConnectService(
        serviceName: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): AdbMdnsService =
        MdnsSessionDiscoveryManager
            .get()
            .resolveTlsConnectService(serviceName = serviceName, timeoutMs = timeoutMs)
}
