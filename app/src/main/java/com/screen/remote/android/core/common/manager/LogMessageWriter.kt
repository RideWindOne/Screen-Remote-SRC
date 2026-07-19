package com.screen.remote.android.core.common.manager

import android.util.Log
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.i18n.LogTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

internal class LogMessageWriter(
    private val state: LogManagerState,
    private val reopenLogFile: () -> Unit,
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun writeLog(
        level: String,
        tag: String,
        message: String,
    ) {
        if (!state.isEnabled || state.runtimeLoggingSuppressed) {
            return
        }

        scope.launch {
            try {
                appendLogLine(
                    level = level,
                    tag = tag,
                    message = message,
                    appendTrailingNewline = true,
                )
            } catch (_: Exception) {
                Log.e(LogTags.LOG_MANAGER, LogTexts.LOG_WRITE_FAILED.get())
            }
        }
    }

    fun writeRawLog(
        level: String,
        tag: String,
        message: String,
    ) {
        if (!state.isEnabled || state.runtimeLoggingSuppressed) {
            return
        }

        scope.launch {
            try {
                appendLogLine(
                    level = level,
                    tag = tag,
                    message = message,
                    appendTrailingNewline = !message.endsWith("\n"),
                )
            } catch (e: Exception) {
                Log.e(LogTags.LOG_MANAGER, "${LogTexts.LOG_WRITE_RAW_FAILED.get()}: ${e.message}")
            }
        }
    }

    private fun appendLogLine(
        level: String,
        tag: String,
        message: String,
        appendTrailingNewline: Boolean,
    ) {
        if (state.runtimeLoggingSuppressed) return
        val timestamp = state.dateFormat.format(Date())
        val logLine =
            buildString {
                append(timestamp)
                append(" ")
                append(level)
                append("/")
                append(tag)
                append(": ")
                append(message)
                if (appendTrailingNewline) {
                    append("\n")
                }
            }

        state.fileWriter?.apply {
            write(logLine)
            flush()
        }

        if ((state.logFile?.length() ?: 0) > LogFileController.MAX_LOG_SIZE) {
            reopenLogFile()
        }
    }
}
