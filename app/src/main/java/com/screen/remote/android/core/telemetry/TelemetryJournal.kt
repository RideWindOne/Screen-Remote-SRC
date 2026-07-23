package com.screen.remote.android.core.telemetry

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small, structured journal that keeps core diagnostic facts available even
 * when verbose application logging is disabled.
 */
object TelemetryJournal {
    @Volatile
    private var enabled = false
    private var appContext: Context? = null
    private val lock = Any()
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val lineTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun recordDiagnostic(
        level: String,
        tag: String,
        message: String,
    ) {
        if (!enabled) return
        val event = normalizeDiagnostic(message) ?: return
        val context = appContext ?: return
        synchronized(lock) {
            val directory = File(context.filesDir, JOURNAL_DIR).apply { mkdirs() }
            val now = Date()
            val file = File(directory, "Telemetry_${fileDateFormat.format(now)}.log")
            file.appendText(
                "${lineTimeFormat.format(now)} $level/$JOURNAL_TAG: source_tag=$tag $event\n",
                Charsets.UTF_8,
            )
        }
    }

    fun getLogFiles(logDate: String): List<File> {
        val context = appContext ?: return emptyList()
        val directory = File(context.filesDir, JOURNAL_DIR)
        return directory
            .listFiles()
            ?.filter { it.isFile && it.name == "Telemetry_$logDate.log" }
            .orEmpty()
    }

    internal fun normalizeDiagnostic(message: String): String? {
        val fields =
            FIELD_REGEX
                .findAll(message)
                .associate { match -> match.groupValues[1] to match.groupValues[2] }
        return when {
            message.startsWith("DIAG session-start ") ->
                buildString {
                    append("event=session_start")
                    append(" transport=${transportOf(fields["device"])}")
                    append(" reconnecting=${fields["reconnecting"].toBoolean()}")
                }

            message.startsWith("DIAG session-clear ") ->
                buildString {
                    append("event=session_clear")
                    append(" transport=${transportOf(fields["device"])}")
                    append(" reason=${safeToken(fields["reason"])}")
                }

            message.startsWith("DIAG first-break ") ->
                buildString {
                    append("event=first_break")
                    append(" source=${safeToken(fields["source"])}")
                    append(" elapsed_ms=${fields["elapsed"]?.removeSuffix("ms")?.toLongOrNull() ?: -1}")
                    append(" transport=${transportOf(fields["device"])}")
                }

            message.endsWith("Connection established") ->
                "event=connection_success transport=${transportOf(bracketedEndpoint(message))}"

            message.contains("Management connection is ready:") ->
                "event=management_connection"

            message.contains("candidate failed:", ignoreCase = true) ->
                "event=candidate_failure transport=${candidateTransport(message)}"

            message == "Pairing completed successfully" || message == "Pairing successful" ->
                "event=pairing_success"

            message.startsWith("Pairing failed") ->
                "event=pairing_failure"

            else -> null
        }
    }

    private fun bracketedEndpoint(message: String): String? =
        message.substringAfter('[', missingDelimiterValue = "")
            .substringBefore(']', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }

    private fun candidateTransport(message: String): String {
        val candidate = CANDIDATE_TRANSPORT_REGEX.find(message)?.groupValues?.getOrNull(1)
        return when (candidate?.lowercase(Locale.US)) {
            "usb" -> "usb"
            "tcp" -> "tcp"
            "mdns" -> "mdns"
            "tls" -> "wireless_debugging"
            else -> "unknown"
        }
    }

    private fun transportOf(device: String?): String =
        when {
            device == null -> "unknown"
            device.startsWith("usb:", ignoreCase = true) -> "usb"
            device.startsWith("adb-tls:", ignoreCase = true) -> "wireless_debugging"
            device.startsWith("mdns:", ignoreCase = true) -> "mdns"
            device.startsWith("tcp:", ignoreCase = true) -> "tcp"
            else -> "unknown"
        }

    private fun safeToken(value: String?): String =
        value
            ?.lowercase(Locale.US)
            ?.replace(UNSAFE_TOKEN_REGEX, "_")
            ?.take(MAX_TOKEN_LENGTH)
            ?.takeIf { it.isNotBlank() }
            ?: "unknown"

    private const val JOURNAL_DIR = "telemetry"
    private const val JOURNAL_TAG = "TLM"
    private const val MAX_TOKEN_LENGTH = 64
    private val FIELD_REGEX = Regex("""([a-zA-Z_]+)=([^\s]+)""")
    private val UNSAFE_TOKEN_REGEX = Regex("""[^a-z0-9_.-]""")
    private val CANDIDATE_TRANSPORT_REGEX = Regex("""candidate failed:\s*([A-Za-z_-]+):""", RegexOption.IGNORE_CASE)
}
