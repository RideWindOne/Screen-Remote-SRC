package com.screen.remote.android.infrastructure.media.audio

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager

internal class AudioDecoderBootstrapper(
    private val formatHandler: AudioFormatHandler,
) {
    fun readBootstrap(
        audioStream: AudioStream,
        codec: String,
    ): DecodeBootstrap? {
        val firstPacket = readFirstPayload(audioStream) ?: return null

        val firstData = firstPacket.payload
        val frameInfo = audioStream.currentFrameInfo()
        logFirstPacket(codec = codec, data = firstData)
        return bootstrapDecode(codec = codec, firstData = firstData, frameInfo = frameInfo)
    }

    private fun readFirstPayload(audioStream: AudioStream): dadb.AdbShellPacket.StdOut? {
        repeat(MAX_BOOTSTRAP_READS) {
            when (val packet = audioStream.read()) {
                is dadb.AdbShellPacket.StdOut -> if (packet.payload.isNotEmpty()) return packet
                is dadb.AdbShellPacket.Exit -> return null
                else -> Unit
            }
        }
        LogManager.e(LogTags.AUDIO_DECODER, "Unable to read first audio packet")
        return null
    }

    private fun bootstrapDecode(
        codec: String,
        firstData: ByteArray,
        frameInfo: AudioFrameInfo?,
    ): DecodeBootstrap? {
        val announcedConfig = frameInfo?.isConfig == true
        if (codec.lowercase() == "opus") {
            return if (announcedConfig || formatHandler.isOpusHead(firstData)) {
                if (!formatHandler.validateConfigPacket(codec, firstData)) {
                    LogManager.e(LogTags.AUDIO_DECODER, "Opus configuration package format error")
                    return null
                }
                AudioDebugLog.d(LogTags.AUDIO_DECODER) { "OpusHead configuration package detected" }
                DecodeBootstrap(configData = firstData, firstAudioPacket = null, firstAudioPts = null)
            } else {
                AudioDebugLog.d(LogTags.AUDIO_DECODER) { "Naked Opus frame detected, configuration packet skipped" }
                DecodeBootstrap(configData = null, firstAudioPacket = firstData, firstAudioPts = frameInfo?.pts)
            }
        }

        // AAC 压缩帧没有可安全依赖的内容签名；长度判断会把任意首帧误当作 ASC。
        // 对 AAC 必须以 scrcpy frame metadata 的 config flag 为唯一依据。
        val looksLikeConfig =
            codec.equals("flac", ignoreCase = true) && formatHandler.validateConfigPacket(codec, firstData)
        return if (announcedConfig) {
            if (!formatHandler.validateConfigPacket(codec, firstData)) {
                LogManager.e(LogTags.AUDIO_DECODER, "$codec Configuration package format error")
                null
            } else {
                DecodeBootstrap(configData = firstData, firstAudioPacket = null, firstAudioPts = null)
            }
        } else if (looksLikeConfig) {
            DecodeBootstrap(configData = firstData, firstAudioPacket = null, firstAudioPts = null)
        } else {
            AudioDebugLog.d(LogTags.AUDIO_DECODER) { "No configuration package is received, and the first $codec audio frame is used directly." }
            DecodeBootstrap(configData = null, firstAudioPacket = firstData, firstAudioPts = frameInfo?.pts)
        }
    }

    private fun logFirstPacket(
        codec: String,
        data: ByteArray,
    ) {
        AudioDebugLog.d(LogTags.AUDIO_DECODER) {
            "The first package: codec=$codec, size=${data.size}, data=${data.take(16).joinToString(" ") { "%02X".format(it) }}..."
        }
    }

    private companion object {
        const val MAX_BOOTSTRAP_READS = 3
    }
}

internal data class DecodeBootstrap(
    val configData: ByteArray?,
    val firstAudioPacket: ByteArray?,
    val firstAudioPts: Long?,
)
