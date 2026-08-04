package com.screen.remote.android.core.telemetry

import com.screen.remote.android.core.common.manager.LogManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPOutputStream

internal data class TelemetryLogPayload(
    val compressedBytes: ByteArray,
    val sha256: String,
    val sourceFileCount: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TelemetryLogPayload

        if (sourceFileCount != other.sourceFileCount) return false
        if (!compressedBytes.contentEquals(other.compressedBytes)) return false
        if (sha256 != other.sha256) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sourceFileCount
        result = 31 * result + compressedBytes.contentHashCode()
        result = 31 * result + sha256.hashCode()
        return result
    }
}

internal object TelemetryLogCollector {
    private const val MAX_UNCOMPRESSED_BYTES = 20L * 1024L * 1024L

    fun collect(logDate: String): TelemetryLogPayload? {
        val fileDateVariants =
            buildSet {
                add(logDate)
                runCatching {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(logDate) ?: return@runCatching
                    add(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date))
                }
            }
        val matchingFiles =
            (
                LogManager
                    .getLogFiles()
                    .filter { file ->
                        file.isFile && fileDateVariants.any { date -> file.name.contains("_$date") }
                    } +
                    TelemetryJournal.getLogFiles(logDate)
                )
                .sortedBy(File::lastModified)
        if (matchingFiles.isEmpty()) return null

        val output = ByteArrayOutputStream()
        var remaining = MAX_UNCOMPRESSED_BYTES
        GZIPOutputStream(output).bufferedWriter(Charsets.UTF_8).use { writer ->
            matchingFiles.forEach { file ->
                if (remaining <= 0) return@forEach
                writer.appendLine("===== ${file.name} =====")
                file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        val encodedSize = line.toByteArray(Charsets.UTF_8).size + 1L
                        if (encodedSize > remaining) break
                        writer.appendLine(line)
                        remaining -= encodedSize
                    }
                }
            }
        }
        val bytes = output.toByteArray()
        return TelemetryLogPayload(
            compressedBytes = bytes,
            sha256 = bytes.sha256(),
            sourceFileCount = matchingFiles.size,
        )
    }

    private fun ByteArray.sha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte) }
}
