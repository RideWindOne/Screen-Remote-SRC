package com.screen.remote.android.infrastructure.adb.connection

import android.content.Context
import com.screen.remote.android.core.common.AppConstants
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.infrastructure.scrcpy.protocol.ScrcpyProtocol
import dadb.Dadb

internal class AdbEncoderDetectionLauncher(
    private val dadb: Dadb,
    private val context: Context,
    private val openShellStream: suspend (String) -> dadb.AdbShellStream?,
) {
    suspend fun loadEncoderOutput(skipPush: Boolean): String {
        ensureScrcpyServer(skipPush = skipPush)
        val command =
            ScrcpyProtocol.buildScrcpyServerCommand(
                "list_encoders=true",
                serverPath =
                    if (skipPush) {
                        AppConstants.SCRCPY_SERVER_2_PATH
                    } else {
                        AppConstants.SCRCPY_SERVER_PATH
                    },
            )
        LogManager.d(LogTags.ADB_CONNECTION, "${SessionTexts.LABEL_EXECUTE_COMMAND.get()}: $command")

        val shellStream = openShellStream(command)
        if (shellStream == null) {
            LogManager.e(LogTags.ADB_CONNECTION, AdbTexts.ADB_CANNOT_OPEN_SHELL_STREAM.get())
            throw Exception(AdbTexts.ADB_CANNOT_OPEN_SHELL_STREAM.get())
        }

        return AdbEncoderShellStreamReader.read(shellStream)
    }

    private suspend fun ensureScrcpyServer(skipPush: Boolean) {
        if (skipPush) {
            return
        }

        val pushResult = AdbFileOperations.pushScrcpyServer(dadb, context, AppConstants.SCRCPY_SERVER_PATH)
        if (pushResult.isFailure) {
            LogManager.e(LogTags.ADB_CONNECTION, AdbTexts.ADB_PUSH_SERVER_FAILED_CANNOT_DETECT.get())
            throw pushResult.exceptionOrNull() ?: Exception(AdbTexts.ADB_PUSH_FAILED.get())
        }
    }
}
