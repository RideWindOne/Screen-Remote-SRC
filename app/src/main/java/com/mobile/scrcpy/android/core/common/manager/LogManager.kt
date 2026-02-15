package com.mobile.scrcpy.android.core.common.manager

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.domain.model.AppSettings
import java.io.File

@SuppressLint("StaticFieldLeak")
object LogManager {
    private val state = LogManagerState()
    private val fileController =
        LogFileController(
            state = state,
            onInitSuccess = {
                i(LogTags.LOG_MANAGER, it)
            },
        )
    private val messageWriter =
        LogMessageWriter(
            state = state,
            reopenLogFile = fileController::reopenLogFile,
        )

    fun init(
        context: Context,
        enabled: Boolean = true,
    ) {
        fileController.init(context, enabled)
    }

    fun setEnabled(enabled: Boolean) {
        fileController.setEnabled(enabled)
    }

    fun applySettings(settings: AppSettings) {
        if (state.isEnabled != settings.enableActivityLog) {
            fileController.setEnabled(settings.enableActivityLog)
        }
        state.enableAudioStreamLog = settings.enableAudioStreamLog
        state.enableVideoStreamLog = settings.enableVideoStreamLog
        state.enableControlStreamLog = settings.enableControlStreamLog
        state.enableShellStreamLog = settings.enableShellStreamLog
        state.enableManagementLog = settings.enableManagementLog
    }

    fun isDetailLoggingEnabled(category: LogDetailCategory): Boolean =
        state.isEnabled &&
            when (category) {
                LogDetailCategory.AUDIO_STREAM -> state.enableAudioStreamLog
                LogDetailCategory.VIDEO_STREAM -> state.enableVideoStreamLog
                LogDetailCategory.CONTROL_STREAM -> state.enableControlStreamLog
                LogDetailCategory.SHELL_STREAM -> state.enableShellStreamLog
                LogDetailCategory.MANAGEMENT -> state.enableManagementLog
            }

    fun v(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable != null) {
            Log.v(tag, message, throwable)
            messageWriter.writeLog("V", tag, "$message: ${throwable.message}")
        } else {
            Log.v(tag, message)
            messageWriter.writeLog("V", tag, message)
        }
    }

    fun d(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable != null) {
            Log.d(tag, message, throwable)
            messageWriter.writeLog("D", tag, "$message: ${throwable.message}")
        } else {
            Log.d(tag, message)
            messageWriter.writeLog("D", tag, message)
        }
    }

    fun i(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable != null) {
            Log.i(tag, message, throwable)
            messageWriter.writeLog("I", tag, "$message: ${throwable.message}")
        } else {
            Log.i(tag, message)
            messageWriter.writeLog("I", tag, message)
        }
    }

    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
            messageWriter.writeLog("W", tag, "$message: ${throwable.message}")
        } else {
            Log.w(tag, message)
            messageWriter.writeLog("W", tag, message)
        }
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
            messageWriter.writeLog("E", tag, "$message: ${throwable.message}")
        } else {
            Log.e(tag, message)
            messageWriter.writeLog("E", tag, message)
        }
    }

    fun getLogFiles(): List<File> = fileController.getLogFiles()

    fun getTotalLogSize(): Long = fileController.getTotalLogSize()

    fun clearAllLogs() {
        fileController.clearAllLogs()
    }

    fun clearOldLogs() {
        fileController.clearOldLogs()
    }

    fun deleteLogFile(file: File): Boolean = fileController.deleteLogFile(file)

    fun readLogFile(file: File): String = fileController.readLogFile(file)

    fun writeRawLog(
        level: String,
        tag: String,
        message: String,
    ) {
        messageWriter.writeRawLog(level, tag, message)
    }

    @JvmStatic
    fun writeRawLogJNI(
        level: String,
        tag: String,
        message: String,
    ) {
        writeRawLog(level, tag, message)
    }
}
