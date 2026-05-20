package com.screen.remote.android.infrastructure.adb.connection

import android.content.Context
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ADB encoder detection facade.
 */
object AdbEncoderDetector {
    suspend fun detectEncoders(
        dadb: Dadb,
        context: Context,
        openShellStream: suspend (String) -> dadb.AdbShellStream?,
        skipPush: Boolean = false,
    ): Result<EncoderDetectionResult> =
        withContext(Dispatchers.IO) {
            try {
                LogManager.d(LogTags.ADB_CONNECTION, "检测远程编码器...")

                val launcher =
                    AdbEncoderDetectionLauncher(
                        dadb = dadb,
                        context = context,
                        openShellStream = openShellStream,
                    )
                val output = launcher.loadEncoderOutput(skipPush = skipPush)
                val result = AdbEncoderOutputParser.parse(output)

                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "检测到编码器: 视频=${result.videoEncoders.size}, 音频=${result.audioEncoders.size}",
                )

                Result.success(result)
            } catch (e: Exception) {
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "检测编码器失败: ${e.javaClass.simpleName} - ${e.message ?: "未知错误"}",
                    e,
                )
                Result.failure(e)
            }
        }
}
