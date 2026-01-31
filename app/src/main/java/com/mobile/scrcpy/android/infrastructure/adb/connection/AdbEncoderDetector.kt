package com.mobile.scrcpy.android.infrastructure.adb.connection

import android.content.Context
import com.mobile.scrcpy.android.core.common.AppConstants
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.ScrcpyProtocol
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ADB 编码器检测器
 * 负责检测设备支持的视频和音频编码器
 */
object AdbEncoderDetector {
    /**
     * 检测设备的视频和音频编码器
     * @param skipPush 是否跳过 push server 步骤 如果跳过则使用 SCRCPY_SERVER_2_PATH
     * @return 编码器检测结果
     */
    suspend fun detectEncoders(
        dadb: Dadb,
        context: Context,
        openShellStream: suspend (String) -> dadb.AdbShellStream?,
        skipPush: Boolean = false,
    ): Result<EncoderDetectionResult> =
        withContext(Dispatchers.IO) {
            try {
                LogManager.d(LogTags.ADB_CONNECTION, "检测远程编码器...")

                // 自动推送 scrcpy-server.jar（如果不存在且未跳过）
                if (!skipPush) {
                    val pushResult = AdbFileOperations.pushScrcpyServer(dadb, context, AppConstants.SCRCPY_SERVER_PATH)
                    if (pushResult.isFailure) {
                        LogManager.e(LogTags.ADB_CONNECTION, AdbTexts.ADB_PUSH_SERVER_FAILED_CANNOT_DETECT.get())
                        return@withContext Result.failure(
                            pushResult.exceptionOrNull() ?: Exception(AdbTexts.ADB_PUSH_FAILED.get()),
                        )
                    }
                }

                // 启动 scrcpy-server 并传入 list_encoders=true 参数
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

                // 使用 openShellStream 读取输出
                val shellStream = openShellStream(command)
                if (shellStream == null) {
                    LogManager.e(LogTags.ADB_CONNECTION, AdbTexts.ADB_CANNOT_OPEN_SHELL_STREAM.get())
                    return@withContext Result.failure(Exception(AdbTexts.ADB_CANNOT_OPEN_SHELL_STREAM.get()))
                }

                // 解析输出
                val output = readShellStreamOutput(shellStream)
                val videoEncoders = parseVideoEncoderList(output)
                val audioEncoders = parseAudioEncoderList(output)

                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "检测到编码器: 视频=${videoEncoders.size}, 音频=${audioEncoders.size}",
                )

                Result.success(EncoderDetectionResult(videoEncoders, audioEncoders))
            } catch (e: Exception) {
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "检测编码器失败: ${e.javaClass.simpleName} - ${e.message ?: "未知错误"}",
                    e,
                )
                Result.failure(e)
            }
        }

    /**
     * 读取 Shell Stream 输出
     */
    private fun readShellStreamOutput(shellStream: dadb.AdbShellStream): String {
        val output = StringBuilder()
        val errorOutput = StringBuilder()
        var lineCount = 0
        val maxLines = 200 // 最多读取 200 行
        var hasReceivedData = false

        try {
            while (lineCount < maxLines) {
                val packet =
                    try {
                        shellStream.read()
                    } catch (e: java.io.EOFException) {
                        // EOF 说明 stream 提前关闭
                        if (!hasReceivedData) {
                            // 完全没收到数据，可能是命令执行失败
                            LogManager.w(
                                LogTags.ADB_CONNECTION,
                                "${AdbTexts.ADB_READ_OUTPUT_ERROR.get()}: " +
                                    "scrcpy-server 立即退出，未输出任何内容",
                            )
                            throw Exception(
                                "scrcpy-server 启动失败：进程立即退出，未输出任何内容。可能原因：\n" +
                                    "1. scrcpy-server.jar 文件损坏\n" +
                                    "2. 设备不支持该版本的 scrcpy\n" +
                                    "3. Android 版本过低",
                            )
                        }

                        val errorMsg =
                            if (errorOutput.isNotEmpty()) {
                                "scrcpy-server 启动失败\nstderr: $errorOutput"
                            } else if (output.isNotEmpty()) {
                                "scrcpy-server 输出不完整\nstdout: $output"
                            } else {
                                "scrcpy-server 启动失败，未收到任何输出"
                            }
                        LogManager.w(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_READ_OUTPUT_ERROR.get()}: $errorMsg")
                        throw Exception(errorMsg)
                    }

                hasReceivedData = true

                when (packet) {
                    is dadb.AdbShellPacket.StdOut -> {
                        val text = String(packet.payload, Charsets.UTF_8)
                        output.append(text)
                        lineCount++
                        LogManager.d(LogTags.ADB_CONNECTION, "stdout: $text")

                        // 如果读到了音频编码器列表，说明已经读完了
                        if (text.contains("List of audio encoders:")) {
                            // 音频编码器列表已经在当前 packet 中，不需要继续读取
                            break
                        }
                    }

                    is dadb.AdbShellPacket.StdError -> {
                        val text = String(packet.payload, Charsets.UTF_8)
                        errorOutput.append(text)
                        LogManager.w(LogTags.ADB_CONNECTION, "scrcpy-server stderr: $text")
                    }

                    is dadb.AdbShellPacket.Exit -> {
                        val exitCode = if (packet.payload.isNotEmpty()) packet.payload[0].toInt() else 0
                        LogManager.d(
                            LogTags.ADB_CONNECTION,
                            "${AdbTexts.ADB_SHELL_STREAM_EXIT.get()}, exitCode: $exitCode",
                        )
                        if (exitCode != 0) {
                            val errorMsg =
                                if (errorOutput.isNotEmpty()) {
                                    "scrcpy-server 执行失败 (exitCode=$exitCode)\nstderr: $errorOutput"
                                } else {
                                    "scrcpy-server 执行失败 (exitCode=$exitCode)，无错误输出"
                                }
                            throw Exception(errorMsg)
                        }
                        break
                    }

                    else -> {
                        LogManager.d(LogTags.ADB_CONNECTION, "Unknown packet: ${packet.javaClass.simpleName}") // TODO
                    }
                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("scrcpy-server") == true) {
                // 已经是我们自己抛出的异常，直接重新抛出
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

    /**
     * 解析编码器列表
     * @param output scrcpy-server 输出
     * @return 编码器信息列表
     *
     * 示例输出格式：
     *     --video-codec=h264 --video-encoder='OMX.qcom.video.encoder.avc'  (hw) [vendor]
     *     --video-codec=h265 --video-encoder='c2.qti.hevc.encoder'          (hw) [vendor]
     */
    private fun parseVideoEncoderList(output: String): List<EncoderInfo.Video> {
        val encoders = mutableListOf<EncoderInfo.Video>()

        // 提取视频编码器部分（在 "List of video encoders:" 和 "List of audio encoders:" 之间）
        val section =
            if (output.contains("List of video encoders:")) {
                val start = output.indexOf("List of video encoders:")
                val end =
                    if (output.contains("List of audio encoders:")) {
                        output.indexOf("List of audio encoders:")
                    } else {
                        output.length
                    }
                output.substring(start, end)
            } else {
                output
            }

        // 解析每一行
        val lines = section.lines()
        for (line in lines) {
            val trimmed = line.trim()

            // 提取 codec 和 encoder
            val codecMatch = Regex("--video-codec=([^\\s]+)").find(trimmed)
            val encoderMatch = Regex("--video-encoder='?([^'\\s]+)'?").find(trimmed)

            if (codecMatch != null && encoderMatch != null) {
                val codec = codecMatch.groupValues[1]
                val encoderName = encoderMatch.groupValues[1].trim('\'')

                // 转换为 MIME type
                val mimeType = codecToMimeType(codec, isVideo = true)

                // 只添加支持的编解码器
                if (mimeType != null) {
                    encoders.add(EncoderInfo.Video(encoderName, mimeType))
                }
            }
        }

        return encoders
    }

    /**
     * 解析音频编码器列表
     */
    private fun parseAudioEncoderList(output: String): List<EncoderInfo.Audio> {
        val encoders = mutableListOf<EncoderInfo.Audio>()

        // 提取音频编码器部分（在 "List of audio encoders:" 之后）
        val section =
            if (output.contains("List of audio encoders:")) {
                val start = output.indexOf("List of audio encoders:")
                output.substring(start)
            } else {
                return encoders
            }

        // 解析每一行
        val lines = section.lines()
        for (line in lines) {
            val trimmed = line.trim()

            // 提取 codec 和 encoder
            val codecMatch = Regex("--audio-codec=([^\\s]+)").find(trimmed)
            val encoderMatch = Regex("--audio-encoder='?([^'\\s]+)'?").find(trimmed)

            if (codecMatch != null && encoderMatch != null) {
                val codec = codecMatch.groupValues[1]
                val encoderName = encoderMatch.groupValues[1].trim('\'')

                // 转换为 MIME type
                val mimeType = codecToMimeType(codec, isVideo = false)

                // 只添加支持的编解码器
                if (mimeType != null) {
                    encoders.add(EncoderInfo.Audio(encoderName, mimeType))
                }
            }
        }

        return encoders
    }

    /**
     * 将 codec 名称转换为 MIME type
     */
    private fun codecToMimeType(
        codec: String,
        isVideo: Boolean,
    ): String? =
        if (isVideo) {
            when (codec.lowercase()) {
                "h264" -> "video/avc"
                "h265", "hevc" -> "video/hevc"
                "av1" -> "video/av01"
                "vp8" -> "video/x-vnd.on2.vp8"
                "vp9" -> "video/x-vnd.on2.vp9"
                else -> null
            }
        } else {
            when (codec.lowercase()) {
                "opus" -> "audio/opus"
                "aac" -> "audio/mp4a-latm"
                "flac" -> "audio/flac"
                "raw" -> "audio/raw"
                else -> null
            }
        }
}
