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
    data class ManualUploadResult(
        val uploadedCount: Int,
        val missingCount: Int,
        val failedCount: Int,
    )

    suspend fun pingOnStartup(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                val preferences = TelemetryPreferences(context.applicationContext)
                if (!preferences.stateFlow.first().enabled) return@runCatching

                val client = TelemetryClient()
                if (!client.isConfigured()) return@runCatching

                client.ping(
                    identity = preferences.getIdentity(),
                    logDate = previousLocalDate(),
                    reason = "startup",
                )
            }
        }
    }

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
            val identity = preferences.getIdentity()
            runCatching {
                if (state.lastUploadedLogDate == logDate) {
                    return@runCatching
                }

                val payload = TelemetryLogCollector.collect(logDate)
                if (payload == null) {
                    return@runCatching
                }

                client.uploadLog(
                    identity = identity,
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

    suspend fun uploadTodayAndYesterday(context: Context): ManualUploadResult =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val preferences = TelemetryPreferences(appContext)
            val client = TelemetryClient()
            if (!client.isConfigured()) {
                LogManager.w(LogTags.APP, "Telemetry endpoint is not configured")
                return@withContext ManualUploadResult(0, 0, 2)
            }

            val identity = preferences.getIdentity()
            var uploadedCount = 0
            var missingCount = 0
            var failedCount = 0
            localDatesTodayAndYesterday().forEach { logDate ->
                val payload = TelemetryLogCollector.collect(logDate)
                if (payload == null) {
                    missingCount += 1
                    return@forEach
                }
                runCatching {
                    client.uploadLog(
                        identity = identity,
                        logDate = logDate,
                        payload = payload,
                    )
                    if (logDate == previousLocalDate()) {
                        preferences.markLogUploaded(logDate)
                    }
                    uploadedCount += 1
                    LogManager.i(LogTags.APP, "Manual telemetry log upload completed for $logDate")
                }.onFailure { error ->
                    failedCount += 1
                    LogManager.w(LogTags.APP, "Manual telemetry log upload failed for $logDate: ${error.message}")
                }
            }
            ManualUploadResult(uploadedCount, missingCount, failedCount)
        }

    internal fun previousLocalDate(nowMillis: Long = System.currentTimeMillis()): String {
        return localDateDaysAgo(nowMillis, 1)
    }

    internal fun localDatesTodayAndYesterday(nowMillis: Long = System.currentTimeMillis()): List<String> =
        listOf(localDateDaysAgo(nowMillis, 0), localDateDaysAgo(nowMillis, 1))

    private fun localDateDaysAgo(
        nowMillis: Long,
        days: Int,
    ): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -days)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }
}
