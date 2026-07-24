package com.screen.remote.android.core.telemetry

import com.screen.remote.android.core.common.constants.AppConstants
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class TelemetryClient(
    private val baseUrl: String = AppConstants.TELEMETRY_BASE_URL,
) {
    fun isConfigured(): Boolean = baseUrl.isNotBlank()

    fun uploadLog(
        identity: TelemetryIdentity,
        logDate: String,
        payload: TelemetryLogPayload,
    ) {
        request(
            path = "/v1/logs?date=$logDate",
            identity = identity,
            contentType = "application/gzip",
            body = payload.compressedBytes,
            extraHeaders =
                mapOf(
                    "X-Log-SHA256" to payload.sha256,
                    "X-Log-File-Count" to payload.sourceFileCount.toString(),
                ),
        )
    }

    fun ping(
        identity: TelemetryIdentity,
        logDate: String,
        reason: String,
    ) {
        val body = """{"logDate":"$logDate","reason":"$reason"}""".toByteArray(Charsets.UTF_8)
        request(
            path = "/v1/ping",
            identity = identity,
            contentType = "application/json",
            body = body,
        )
    }

    private fun request(
        path: String,
        identity: TelemetryIdentity,
        contentType: String,
        body: ByteArray,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        val connection =
            (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 15_000
                requestMethod = "POST"
                doOutput = true
                setFixedLengthStreamingMode(body.size)
                setRequestProperty("Content-Type", contentType)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Screen-Remote/${AppConstants.APP_VERSION}")
                setRequestProperty("X-Installation-ID", identity.installationId)
                identity.originalInstallationId?.let {
                    setRequestProperty("X-Original-Installation-ID", it)
                }
                setRequestProperty("X-App-Version", AppConstants.APP_VERSION)
                for ((name, value) in extraHeaders) {
                    setRequestProperty(name, value)
                }
            }

        try {
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode !in 200..299) {
                connection.errorStream?.close()
                throw IOException("Telemetry endpoint returned HTTP ${connection.responseCode}")
            }
            connection.inputStream.close()
        } finally {
            connection.disconnect()
        }
    }
}
