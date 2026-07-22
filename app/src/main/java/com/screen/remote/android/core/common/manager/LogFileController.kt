package com.screen.remote.android.core.common.manager

import android.content.Context
import android.util.Log
import com.screen.remote.android.core.common.AppConstants
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.i18n.LogTexts
import java.io.File
import java.io.FileWriter
import java.util.Date

internal class LogFileController(
    private val state: LogManagerState,
    private val onInitSuccess: (String) -> Unit,
) {
    fun init(
        context: Context,
        enabled: Boolean,
    ) {
        state.context = context.applicationContext
        state.isEnabled = enabled
        if (enabled) {
            initLogFile()
        }
    }

    fun setEnabled(enabled: Boolean) {
        state.isEnabled = enabled
        if (enabled) {
            initLogFile()
        } else {
            closeLogFile()
        }
    }

    fun reopenLogFile() {
        closeLogFile()
        initLogFile()
    }

    fun getLogFiles(): List<File> {
        val context = state.context ?: return emptyList()
        val logDir = File(context.filesDir, LOG_DIR)
        if (!logDir.exists()) {
            return emptyList()
        }

        return logDir
            .listFiles()
            ?.filter { it.extension == "log" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun getTotalLogSize(): Long = getLogFiles().sumOf { it.length() }

    fun clearAllLogs() {
        closeLogFile()
        val context = state.context ?: return
        val logDir = File(context.filesDir, LOG_DIR)
        if (logDir.exists()) {
            logDir.listFiles()?.forEach { it.delete() }
        }
        if (state.isEnabled) {
            initLogFile()
        }
    }

    fun clearOldLogs() {
        val context = state.context ?: return
        val logDir = File(context.filesDir, LOG_DIR)
        if (!logDir.exists()) {
            return
        }

        val currentLogFile = state.logFile
        logDir.listFiles()?.forEach { file ->
            if (file != currentLogFile && file.extension == "log") {
                try {
                    file.delete()
                    LogManager.i(LogTags.LOG_MANAGER, "${LogTexts.LOG_DELETE_FILE_SUCCESS.english}: ${file.name}")
                } catch (e: Exception) {
                    LogManager.e(LogTags.LOG_MANAGER, "${LogTexts.LOG_DELETE_FILE_FAILED.english}: ${file.name}", e)
                }
            }
        }
    }

    fun deleteLogFile(file: File): Boolean =
        try {
            if (file == state.logFile) {
                closeLogFile()
                val result = file.delete()
                if (state.isEnabled) {
                    initLogFile()
                }
                result
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(LogTags.LOG_MANAGER, LogTexts.LOG_DELETE_FILE_FAILED.english, e)
            false
        }

    fun readLogFile(file: File): String =
        try {
            file.readText()
        } catch (e: Exception) {
            Log.e(LogTags.LOG_MANAGER, LogTexts.LOG_READ_FILE_FAILED.english, e)
            "${LogTexts.LOG_READ_FILE_ERROR.english}: ${e.message}"
        }

    private fun initLogFile() {
        try {
            val context = state.context ?: return
            val logDir = File(context.filesDir, LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val version = AppConstants.APP_VERSION
            val date = state.fileNameFormat.format(Date())
            val fileName = "Scrcpy_Remote_${version}_$date.log"
            state.logFile = File(logDir, fileName)

            if (state.logFile?.exists() == true && (state.logFile?.length() ?: 0) > MAX_LOG_SIZE) {
                val timestamp = System.currentTimeMillis()
                val rotatedFileName = "Scrcpy_Remote_${version}_${date}_$timestamp.log"
                state.logFile = File(logDir, rotatedFileName)
            }

            state.fileWriter = FileWriter(state.logFile, true)
            onInitSuccess(LogTexts.LOG_SYSTEM_INIT_SUCCESS.english)
        } catch (e: Exception) {
            Log.e(LogTags.LOG_MANAGER, LogTexts.LOG_INIT_FILE_FAILED.english, e)
        }
    }

    private fun closeLogFile() {
        try {
            state.fileWriter?.close()
            state.fileWriter = null
        } catch (e: Exception) {
            Log.e(LogTags.LOG_MANAGER, LogTexts.LOG_CLOSE_FILE_FAILED.english, e)
        }
    }

    companion object {
        private const val LOG_DIR = "logs"
        internal const val MAX_LOG_SIZE = 10 * 1024 * 1024
    }
}
