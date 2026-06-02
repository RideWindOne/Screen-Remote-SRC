package com.screen.remote.android.infrastructure.adb.connection

import android.content.Context
import com.screen.remote.android.core.common.AppConstants
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.LogManager.dShell
import com.screen.remote.android.core.domain.model.CodecAcceleration
import com.screen.remote.android.core.domain.model.CodecCatalog
import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.EncoderCapability
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.infrastructure.scrcpy.protocol.ScrcpyProtocol
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EncoderDetectionResult(
    val videoEncoders: List<EncoderCapability>,
    val audioEncoders: List<EncoderCapability>,
)

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

internal object AdbEncoderShellStreamReader {
    fun read(shellStream: dadb.AdbShellStream): String {
        val output = StringBuilder()
        val errorOutput = StringBuilder()
        var lineCount = 0
        var hasReceivedData = false

        try {
            while (lineCount < MAX_LINES) {
                val packet =
                    try {
                        shellStream.read()
                    } catch (e: java.io.EOFException) {
                        handleUnexpectedEof(
                            hasReceivedData = hasReceivedData,
                            output = output,
                            errorOutput = errorOutput,
                        )
                    }

                hasReceivedData = true

                when (packet) {
                    is dadb.AdbShellPacket.StdOut -> {
                        val text = String(packet.payload, Charsets.UTF_8)
                        dShell(LogTags.ADB_CONNECTION) {
                            "encoder shell packet type=stdout length=${text.length} payload=${shellLogPreview(text)}"
                        }
                        output.append(text)
                        lineCount++
                        LogManager.d(LogTags.ADB_CONNECTION, "stdout: $text")

                    }

                    is dadb.AdbShellPacket.StdError -> {
                        val text = String(packet.payload, Charsets.UTF_8)
                        dShell(LogTags.ADB_CONNECTION) {
                            "encoder shell packet type=stderr length=${text.length} payload=${shellLogPreview(text)}"
                        }
                        errorOutput.append(text)
                        LogManager.w(LogTags.ADB_CONNECTION, "scrcpy-server stderr: $text")
                    }

                    is dadb.AdbShellPacket.Exit -> {
                        val exitCode = if (packet.payload.isNotEmpty()) packet.payload[0].toInt() else 0
                        dShell(LogTags.ADB_CONNECTION) { "encoder shell packet type=exit exitCode=$exitCode" }
                        LogManager.d(
                            LogTags.ADB_CONNECTION,
                            "${AdbTexts.ADB_SHELL_STREAM_EXIT.get()}, exitCode: $exitCode",
                        )
                        if (exitCode != 0) {
                            throw Exception(
                                if (errorOutput.isNotEmpty()) {
                                    "scrcpy-server 执行失败 (exitCode=$exitCode)\nstderr: $errorOutput"
                                } else {
                                    "scrcpy-server 执行失败 (exitCode=$exitCode)，无错误输出"
                                },
                            )
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("scrcpy-server") == true) {
                throw e
            }
            LogManager.w(
                LogTags.ADB_CONNECTION,
                "${AdbTexts.ADB_READ_OUTPUT_ERROR.get()}: ${e.javaClass.simpleName} - ${e.message ?: "未知错误"}",
                e,
            )
            throw e
        } finally {
            try {
                shellStream.close()
            } catch (e: Exception) {
                LogManager.w(LogTags.ADB_CONNECTION, "关闭 shell stream 失败: ${e.message}")
            }
        }

        return output.toString()
    }

    private fun handleUnexpectedEof(
        hasReceivedData: Boolean,
        output: StringBuilder,
        errorOutput: StringBuilder,
    ): Nothing {
        if (!hasReceivedData) {
            LogManager.w(
                LogTags.ADB_CONNECTION,
                "${AdbTexts.ADB_READ_OUTPUT_ERROR.get()}: scrcpy-server 立即退出，未输出任何内容",
            )
            throw Exception(
                "scrcpy-server 启动失败：进程立即退出，未输出任何内容。可能原因：\n" +
                    "1. scrcpy-server.jar 文件损坏\n" +
                    "2. 设备不支持该版本的 scrcpy\n" +
                    "3. Android 版本过低",
            )
        }

        val errorMessage =
            if (errorOutput.isNotEmpty()) {
                "scrcpy-server 启动失败\nstderr: $errorOutput"
            } else if (output.isNotEmpty()) {
                "scrcpy-server 输出不完整\nstdout: $output"
            } else {
                "scrcpy-server 启动失败，未收到任何输出"
            }
        LogManager.w(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_READ_OUTPUT_ERROR.get()}: $errorMessage")
        throw Exception(errorMessage)
    }

    private const val MAX_LINES = 200
}

internal object AdbEncoderOutputParser {
    fun parse(output: String): EncoderDetectionResult =
        EncoderDetectionResult(
            videoEncoders = parseEncoderList(output, CodecMediaType.VIDEO),
            audioEncoders = parseEncoderList(output, CodecMediaType.AUDIO),
        )

    private fun parseEncoderList(
        output: String,
        mediaType: CodecMediaType,
    ): List<EncoderCapability> {
        val typeName = if (mediaType == CodecMediaType.VIDEO) "video" else "audio"
        val codecRegex = Regex("--$typeName-codec=([^\\s]+)")
        val encoderRegex = Regex("--$typeName-encoder=(?:'([^']+)'|([^\\s]+))")
        val accelerationRegex = Regex("\\((hw|sw|hybrid)\\)", RegexOption.IGNORE_CASE)
        val aliasRegex = Regex("\\(alias for ([^)]+)\\)", RegexOption.IGNORE_CASE)
        val capabilities = linkedMapOf<Pair<String, String>, EncoderCapability>()

        output.lineSequence().forEach { line ->
            val codecValue = codecRegex.find(line)?.groupValues?.get(1) ?: return@forEach
            val encoderMatch = encoderRegex.find(line) ?: return@forEach
            val encoderName = encoderMatch.groupValues[1].ifBlank { encoderMatch.groupValues[2] }.trim('\'')
            val spec = CodecCatalog.find(mediaType, codecValue) ?: return@forEach
            val acceleration =
                when (accelerationRegex.find(line)?.groupValues?.get(1)?.lowercase()) {
                    "hw" -> CodecAcceleration.HARDWARE
                    "sw" -> CodecAcceleration.SOFTWARE
                    "hybrid" -> CodecAcceleration.HYBRID
                    else -> CodecAcceleration.UNKNOWN
                }
            val capability =
                EncoderCapability(
                    name = encoderName,
                    codec = spec.name,
                    mimeType = spec.mimeType,
                    mediaType = mediaType,
                    acceleration = acceleration,
                    isVendor = line.contains("[vendor]", ignoreCase = true),
                    aliasOf = aliasRegex.find(line)?.groupValues?.get(1)?.trim()?.ifBlank { null },
                )
            val key = spec.name to encoderName
            if (key !in capabilities) {
                capabilities[key] = capability
            }
        }

        return capabilities.values.toList()
    }
}
