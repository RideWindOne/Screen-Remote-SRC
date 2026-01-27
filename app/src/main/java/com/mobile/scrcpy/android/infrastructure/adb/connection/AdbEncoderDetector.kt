package com.mobile.scrcpy.android.infrastructure.adb.connection

import android.content.Context
import com.mobile.scrcpy.android.core.common.AppConstants
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.ScrcpyProtocol

import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
/**
 * ADB 编码器检测器
 * 负责检测设备支持的视频和音频编码器
 */
object AdbEncoderDetector {
    
    /**
     * 检测可用的视频编码器
     * 启动 scrcpy-server 并传入 list_encoders=true 参数，读取设备的编码器列表
     * @param context Android Context，用于推送 scrcpy-server.jar（如果需要）
     */
    suspend fun detectVideoEncoders(
        dadb: Dadb,
        context: Context,
        openShellStream: suspend (String) -> dadb.AdbShellStream?
    ): Result<List<VideoEncoderInfo>> = withContext(Dispatchers.IO) {
        try {
            LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_DETECTING_VIDEO_ENCODERS.get())
            
            // 自动推送 scrcpy-server.jar（如果不存在）
            val pushResult = AdbFileOperations.pushScrcpyServer(dadb, context, AppConstants.SCRCPY_SERVER_PATH)
            if (pushResult.isFailure) {
                LogManager.e(LogTags.ADB_CONNECTION, AdbTexts.ADB_PUSH_SERVER_FAILED_CANNOT_DETECT.get())
                return@withContext Result.failure(pushResult.exceptionOrNull() ?: Exception(AdbTexts.ADB_PUSH_FAILED.get()))
            }
            
            // 启动 scrcpy-server 并传入 list_encoders=true 参数
            val command = ScrcpyProtocol.buildScrcpyServerCommand("list_encoders=true")
            LogManager.d(LogTags.ADB_CONNECTION, "${SessionTexts.LABEL_EXECUTE_COMMAND.get()}: $command")
            
            // 使用 openShellStream 读取输出
            val shellStream = openShellStream(command)
            if (shellStream == null) {
                LogManager.e(LogTags.ADB_CONNECTION, AdbTexts.ADB_CANNOT_OPEN_SHELL_STREAM.get())
                return@withContext Result.failure(Exception(AdbTexts.ADB_CANNOT_OPEN_SHELL_STREAM.get()))
            }
            
            val output = readShellStreamOutput(shellStream)
            
            LogManager.d(LogTags.ADB_CONNECTION, "${SessionTexts.LABEL_RECEIVED_OUTPUT.get()} (${output.length} ${CommonTexts.LABEL_CHARACTERS.get()})")
            
            // 解析输出
            val encoders = parseVideoEncoderList(output)
            
            LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_DETECTED_VIDEO_ENCODERS.get()} ${encoders.size} ${CommonTexts.LABEL_ITEMS.get()}")
            if (encoders.isEmpty()) {
                LogManager.w(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_NO_ENCODERS_DETECTED_OUTPUT.get()}：\n$output")
            }
            Result.success(encoders)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_DETECT_ENCODERS_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 检测音频编码器
     */
    suspend fun detectAudioEncoders(
        dadb: Dadb,
        context: Context,
        openShellStream: suspend (String) -> dadb.AdbShellStream?
    ): Result<List<AudioEncoderInfo>> = withContext(Dispatchers.IO) {
        try {
            LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_DETECTING_AUDIO_ENCODERS.get())
            
            // 自动推送 scrcpy-server.jar（如果不存在）
            val pushResult = AdbFileOperations.pushScrcpyServer(dadb, context, AppConstants.SCRCPY_SERVER_PATH)
            if (pushResult.isFailure) {
                LogManager.e(LogTags.ADB_CONNECTION, AdbTexts.ADB_PUSH_SERVER_FAILED_CANNOT_DETECT.get())
                return@withContext Result.failure(pushResult.exceptionOrNull() ?: Exception(AdbTexts.ADB_PUSH_FAILED.get()))
            }
            
            // 启动 scrcpy-server 并传入 list_encoders=true 参数
            val command = ScrcpyProtocol.buildScrcpyServerCommand("list_encoders=true")
            LogManager.d(LogTags.ADB_CONNECTION, "${SessionTexts.LABEL_EXECUTE_COMMAND.get()}: $command")
            
            // 使用 openShellStream 读取输出
            val shellStream = openShellStream(command)
            if (shellStream == null) {
                LogManager.e(LogTags.ADB_CONNECTION, AdbTexts.ADB_CANNOT_OPEN_SHELL_STREAM.get())
                return@withContext Result.failure(Exception(AdbTexts.ADB_CANNOT_OPEN_SHELL_STREAM.get()))
            }
            
            val output = readShellStreamOutput(shellStream)
            
            LogManager.d(LogTags.ADB_CONNECTION, "${SessionTexts.LABEL_RECEIVED_OUTPUT.get()} (${output.length} ${CommonTexts.LABEL_CHARACTERS.get()})")
            
            // 解析输出
            val encoders = parseAudioEncoderList(output)
            
            LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_DETECTED_AUDIO_ENCODERS.get()} ${encoders.size} ${CommonTexts.LABEL_ITEMS.get()}")
            if (encoders.isEmpty()) {
                LogManager.w(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_NO_AUDIO_ENCODERS_DETECTED_OUTPUT.get()}：\n$output")
            }
            Result.success(encoders)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_DETECT_AUDIO_ENCODERS_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 读取 Shell Stream 输出
     */
    private suspend fun readShellStreamOutput(shellStream: dadb.AdbShellStream): String {
        val output = StringBuilder()
        var lineCount = 0
        val maxLines = 200 // 最多读取 200 行
        
        try {
            while (lineCount < maxLines) {
                when (val packet = shellStream.read()) {
                    is dadb.AdbShellPacket.StdOut -> {
                        val text = String(packet.payload, Charsets.UTF_8)
                        output.append(text)
                        lineCount++
                        
                        // 如果读到了编码器列表的结束标志，可以提前退出
                        if (text.contains("List of audio encoders:")) {
                            // 继续读取音频编码器部分
                            repeat(50) {
                                val audioPacket = shellStream.read()
                                if (audioPacket is dadb.AdbShellPacket.StdOut) {
                                    output.append(String(audioPacket.payload, Charsets.UTF_8))
                                }
                            }
                            break
                        }
                    }
                    is dadb.AdbShellPacket.Exit -> {
                        LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_SHELL_STREAM_EXIT.get())
                        break
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            LogManager.w(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_READ_OUTPUT_ERROR.get()}: ${e.message}")
        } finally {
            shellStream.close()
        }
        
        return output.toString()
    }
    
    /**
     * 解析 scrcpy-server 输出的编码器列表
     * 格式示例：
     * List of video encoders:
     *     --video-codec=h264 --video-encoder='c2.android.avc.encoder'       (hw)
     *     --video-codec=h265 --video-encoder='c2.qti.hevc.encoder'          (hw) [vendor]
     */
    private fun parseVideoEncoderList(output: String): List<VideoEncoderInfo> {
        val encoders = mutableListOf<VideoEncoderInfo>()
        
        // 只解析视频编码器部分（在 "List of video encoders:" 和 "List of audio encoders:" 之间）
        val videoSection = if (output.contains("List of video encoders:")) {
            val start = output.indexOf("List of video encoders:")
            val end = if (output.contains("List of audio encoders:")) {
                output.indexOf("List of audio encoders:")
            } else {
                output.length
            }
            output.substring(start, end)
        } else {
            output
        }
        
        val lines = videoSection.lines()
        for (line in lines) {
            val trimmed = line.trim()
            
            // 匹配 --video-encoder=xxx 或 --video-encoder='xxx' 格式
            val encoderMatch = Regex("--video-encoder='?([^'\\s]+)'?").find(trimmed)
            val codecMatch = Regex("--video-codec=(\\w+)").find(trimmed)
            
            if (encoderMatch != null) {
                // 去掉引号
                val encoderName = encoderMatch.groupValues[1].trim('\'')
                val codecName = codecMatch?.groupValues?.get(1) ?: "unknown"
                
                // 推断 MIME 类型（使用 ApiCompatHelper 处理兼容性）
                val mimeType = when (codecName.lowercase()) {
                    "h264" -> "video/avc"
                    "h265" -> "video/hevc"
                    "h263" -> "video/3gpp"
                    "av1" -> {
                        // AV1 需要 API 29+，低版本设备跳过
                        if (ApiCompatHelper.isAV1Supported()) {
                            "video/av01"
                        } else {
                            null  // 不支持的编解码器返回 null，后续过滤
                        }
                    }
                    "vp8" -> "video/x-vnd.on2.vp8"
                    "vp9" -> "video/x-vnd.on2.vp9"
                    "mpeg4" -> "video/mp4v-es"
                    else -> "video/$codecName"
                }
                
                // 只添加支持的编解码器
                if (mimeType != null) {
                    encoders.add(VideoEncoderInfo(encoderName, mimeType))
                }
            }
        }
        
        return encoders
    }
    
    /**
     * 解析音频编码器列表
     * 格式示例：
     * List of audio encoders:
     *     --audio-codec=opus --audio-encoder='c2.android.opus.encoder'
     *     --audio-codec=aac --audio-encoder='c2.android.aac.encoder'
     */
    private fun parseAudioEncoderList(output: String): List<AudioEncoderInfo> {
        val encoders = mutableListOf<AudioEncoderInfo>()
        
        // 只解析音频编码器部分（在 "List of audio encoders:" 之后）
        val audioSection = if (output.contains("List of audio encoders:")) {
            val start = output.indexOf("List of audio encoders:")
            output.substring(start)
        } else {
            return encoders
        }
        
        val lines = audioSection.lines()
        for (line in lines) {
            val trimmed = line.trim()
            
            // 匹配 --audio-encoder=xxx 或 --audio-encoder='xxx' 格式
            val encoderMatch = Regex("--audio-encoder='?([^'\\s]+)'?").find(trimmed)
            val codecMatch = Regex("--audio-codec=(\\w+)").find(trimmed)
            
            if (encoderMatch != null) {
                val encoderName = encoderMatch.groupValues[1].trim('\'')
                val codecName = codecMatch?.groupValues?.get(1) ?: "unknown"
                
                // 推断 MIME 类型
                val mimeType = when (codecName.lowercase()) {
                    "opus" -> "audio/opus"
                    "aac" -> "audio/mp4a-latm"
                    "flac" -> "audio/flac"
                    "raw" -> "audio/raw"
                    "3gpp", "amrnb" -> "audio/3gpp"
                    "amrwb" -> "audio/amr-wb"
                    else -> "audio/$codecName"
                }
                
                encoders.add(AudioEncoderInfo(encoderName, mimeType))
            }
        }
        
        return encoders
    }
}
