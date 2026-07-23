package com.screen.remote.android.core.telemetry

import android.content.Context
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object TelemetryManager {
    suspend fun runDaily(context: Context) {
        withContext(Dispatchers.IO) {
            val preferences = TelemetryPreferences(context.applicationContext)
            val state = preferences.stateFlow.first()
            if (!state.enabled) return@withContext

            val client = TelemetryClient()
            if (!client.isConfigured()) {
                LogManager.w(LogTags.APP, "Telemetry endpoint is not configured")
                return@withContext
            }

            val logDate = previousLocalDate()
            val installationId = preferences.getOrCreateInstallationId()
            runCatching {
                if (state.lastUploadedLogDate == logDate) {
                    client.ping(
                        installationId = installationId,
                        logDate = logDate,
                        reason = "already_uploaded",
                    )
                    LogManager.i(LogTags.APP, "Telemetry ping completed for $logDate")
                    return@runCatching
                }

                val payload = TelemetryLogCollector.collect(logDate)
                if (payload == null) {
                    client.ping(
                        installationId = installationId,
                        logDate = logDate,
                        reason = "no_log",
                    )
                    LogManager.i(LogTags.APP, "Telemetry ping completed without a log for $logDate")
                    return@runCatching
                }

                client.uploadLog(
                    installationId = installationId,
                    logDate = logDate,
                    payload = payload,
                )
                preferences.markLogUploaded(logDate)
                LogManager.i(LogTags.APP, "Telemetry log upload completed for $logDate")
            }.onFailure { error ->
                LogManager.w(LogTags.APP, "Telemetry request failed: ${error.message}")
            }
        }
    }

    internal fun previousLocalDate(nowMillis: Long = System.currentTimeMillis()): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }
}
