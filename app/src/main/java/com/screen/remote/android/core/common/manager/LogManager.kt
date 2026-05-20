package com.screen.remote.android.core.common.manager

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.domain.model.AppSettings
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
        state.enableEventStreamLog = settings.enableEventStreamLog
        state.enableShellStreamLog = settings.enableShellStreamLog
        state.enableManagementLog = settings.enableManagementLog
    }

    fun isDetailLoggingEnabled(category: LogDetailCategory): Boolean =
        state.isEnabled &&
            when (category) {
                LogDetailCategory.AUDIO_STREAM -> state.enableAudioStreamLog
                LogDetailCategory.VIDEO_STREAM -> state.enableVideoStreamLog
                LogDetailCategory.CONTROL_STREAM -> state.enableControlStreamLog
                LogDetailCategory.EVENT_STREAM -> state.enableEventStreamLog
                LogDetailCategory.SHELL_STREAM -> state.enableShellStreamLog
                LogDetailCategory.MANAGEMENT -> state.enableManagementLog
            }

    fun v(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (!isDebugLoggingEnabledForTag(tag)) {
            return
        }
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
        if (!isDebugLoggingEnabledForTag(tag)) {
            return
        }
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
        if (!isDebugLoggingEnabledForTag(tag)) {
            return
        }
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

    private fun isDebugLoggingEnabledForTag(tag: String): Boolean {
        val category = detailCategoryForTag(tag) ?: return true
        return isDetailLoggingEnabled(category)
    }

    private fun detailCategoryForTag(tag: String): LogDetailCategory? =
        when (tag) {
            LogTags.VIDEO_DECODER,
            LogTags.VIDEO_CODEC_SELECTOR,
            LogTags.SCRCPY_PACKET,
            LogTags.REMOTE_DISPLAY,
                -> LogDetailCategory.VIDEO_STREAM

            LogTags.AUDIO_DECODER,
            LogTags.AUDIO_CODEC_SELECTOR,
            LogTags.AAC_ENCODE,
            LogTags.OPUS_ENCODE,
                -> LogDetailCategory.AUDIO_STREAM

            LogTags.CONTROL_HANDLER,
            LogTags.TOUCH_HANDLER,
            LogTags.CONTROL_VM,
            LogTags.CIRCLE_MENU,
                -> LogDetailCategory.CONTROL_STREAM

            LogTags.SCRCPY_SERVER,
                -> LogDetailCategory.SHELL_STREAM

            LogTags.SCRCPY_CLIENT,
            LogTags.SCRCPY_EVENT_BUS,
            LogTags.SDL,
            LogTags.SDL_HM,
                -> LogDetailCategory.EVENT_STREAM

            LogTags.APP,
            LogTags.SCRCPY_SERVICE,
            LogTags.SCRCPY_BRIDGE,
            LogTags.ADB_CONNECTION,
            LogTags.ADB_BRIDGE,
            LogTags.ADB_MANAGER,
            LogTags.ADB_KEEP_ALIVE_SERVICE,
            LogTags.ADB_PAIRING,
            LogTags.USB_CONNECTION,
            LogTags.CONNECTION_VM,
            LogTags.SCREEN_REMOTE_APP,
            LogTags.SESSION_DIALOG,
            LogTags.MAIN_SCREEN,
            LogTags.SESSION_VM,
            LogTags.GROUP_VM,
            LogTags.MAIN_VIEW_MODEL,
            LogTags.ADB_KEYS_VM,
            LogTags.SETTINGS_VM,
            LogTags.CODEC_TEST_SCREEN,
            LogTags.FLOATING_CONTROLLER,
            LogTags.FLOATING_CONTROLLER_MSG,
            LogTags.LOG_MANAGER,
            LogTags.TTS_MANAGER,
            LogTags.LOGCAT_CAPTURE,
            LogTags.NETWORK,
                -> LogDetailCategory.MANAGEMENT

            else -> null
        }
}
