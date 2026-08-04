package com.screen.remote.android.infrastructure.adb.pairing

import android.content.Context
import android.os.Build
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.infrastructure.adb.AdbRuntimeDiagnostics
import com.screen.remote.android.infrastructure.adb.AdbRuntimeProvider
import dadb.android.runtime.AdbRuntime
import dadb.android.runtime.ExperimentalDadbAndroidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException

/**
 * ADB pairing flow orchestrator.
 *
 * The app layer only provides key access and logging. Pairing transport/wire protocol now lives
 * under `external/dadb` so a future native SPAKE2-backed implementation can replace it without
 * changing the UI layer.
 */
@OptIn(ExperimentalDadbAndroidApi::class)
class AdbPairingManager(
    private val context: Context,
) {
    private val adbRuntime: AdbRuntime
        get() = AdbRuntimeProvider.get()

    suspend fun pairWithCode(
        ipAddress: String,
        port: Int,
        pairingCode: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    return@withContext Result.failure(
                        IllegalStateException("Wireless Debugging pairing requires Android 11 or later"),
                    )
                }

                LogManager.d(
                    LogTags.ADB_PAIRING,
                    "Starting Wireless Debugging pairing via dadb runtime: $ipAddress:$port"
                )
                LogManager.d(
                    LogTags.ADB_PAIRING,
                    "Pairing identity: ${AdbRuntimeDiagnostics.identitySummary(adbRuntime)}"
                )
                LogManager.d(
                    LogTags.ADB_PAIRING,
                    "Pairing TLS state before request: ${
                        AdbRuntimeDiagnostics.endpointSummary(
                            context,
                            ipAddress,
                            port
                        )
                    }"
                )

                val pairingResult = adbRuntime.pairWithCode(ipAddress, port, pairingCode)
                if (pairingResult.isSuccess) {
                    LogManager.d(LogTags.ADB_PAIRING, "Pairing completed successfully")
                    LogManager.d(
                        LogTags.ADB_PAIRING,
                        "Pairing TLS state after request: ${
                            AdbRuntimeDiagnostics.endpointSummary(
                                context,
                                ipAddress,
                                port
                            )
                        }"
                    )
                    Result.success(Unit)
                } else {
                    val error =
                        pairingResult.exceptionOrNull() ?: IllegalStateException("Wireless Debugging pairing failed")
                    if (error is ConnectException) {
                        LogManager.e(LogTags.ADB_PAIRING, "Pairing failed: ${error.message}")
                    } else {
                        LogManager.e(LogTags.ADB_PAIRING, "Pairing failed: ${error.message}", error)
                    }
                    Result.failure(error)
                }
            } catch (e: ConnectException) {
                LogManager.e(LogTags.ADB_PAIRING, "Pairing failed: ${e.message}")
                Result.failure(e)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_PAIRING, "Pairing failed: ${e.message}", e)
                Result.failure(e)
            }
        }
}
