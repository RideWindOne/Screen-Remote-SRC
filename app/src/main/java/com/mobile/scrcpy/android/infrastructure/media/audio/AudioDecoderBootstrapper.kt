package com.mobile.scrcpy.android.infrastructure.media.audio

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager

internal class AudioDecoderBootstrapper(
    private val formatHandler: AudioFormatHandler,
) {
    fun readBootstrap(
        audioStream: AudioStream,
        codec: String,
    ): DecodeBootstrap? {
        val firstPacket = audioStream.read()
        if (firstPacket !is dadb.AdbShellPacket.StdOut || firstPacket.payload.isEmpty()) {
            LogManager.e(LogTags.AUDIO_DECODER, "无法读取第一个包")
            return null
        }

        val firstData = firstPacket.payload
        logFirstPacket(codec = codec, data = firstData)
        return bootstrapDecode(codec = codec, firstData = firstData)
    }

    private fun bootstrapDecode(
        codec: String,
        firstData: ByteArray,
    ): DecodeBootstrap? {
        if (codec.lowercase() == "opus") {
            return if (formatHandler.isOpusHead(firstData)) {
                AudioDebugLog.d(LogTags.AUDIO_DECODER) { "检测到 OpusHead 配置包" }
                DecodeBootstrap(configData = firstData, firstAudioPacket = null)
            } else {
                AudioDebugLog.d(LogTags.AUDIO_DECODER) { "检测到裸 Opus 帧，跳过配置包" }
                DecodeBootstrap(configData = null, firstAudioPacket = firstData)
            }
        }

        if (!formatHandler.validateConfigPacket(codec, firstData)) {
            LogManager.e(LogTags.AUDIO_DECODER, "配置包格式错误")
            return null
        }
        return DecodeBootstrap(configData = firstData, firstAudioPacket = null)
    }

    private fun logFirstPacket(
        codec: String,
        data: ByteArray,
    ) {
        AudioDebugLog.d(LogTags.AUDIO_DECODER) {
            "第一个包: codec=$codec, size=${data.size}, data=${data.take(16).joinToString(" ") { "%02X".format(it) }}..."
        }
    }
}

internal data class DecodeBootstrap(
    val configData: ByteArray?,
    val firstAudioPacket: ByteArray?,
)
