package com.screen.remote.android.feature.codec.ui

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import com.screen.remote.android.feature.codec.model.CodecInfo

fun buildVideoDecoderInfos(decoderNames: List<String>): List<CodecInfo> =
    buildLocalCodecInfos(
        codecNames = decoderNames,
        mimePrefix = "video/",
        isEncoder = false,
        fallbackType = ::inferVideoTypesFromName,
    )

fun buildAudioDecoderInfos(decoderNames: List<String>): List<CodecInfo> =
    buildLocalCodecInfos(
        codecNames = decoderNames,
        mimePrefix = "audio/",
        isEncoder = false,
        fallbackType = ::inferAudioTypesFromName,
    )

fun inferVideoTypesFromName(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.contains("avc") || lower.contains("h264") || lower.contains("264") -> "video/avc"
        lower.contains("hevc") || lower.contains("h265") || lower.contains("265") -> "video/hevc"
        lower.contains("av01") || lower.contains("av1") -> "video/av01"
        lower.contains("vp9") -> "video/x-vnd.on2.vp9"
        lower.contains("vp8") -> "video/x-vnd.on2.vp8"
        else -> ""
    }
}

fun inferAudioTypesFromName(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.contains("opus") -> "audio/opus"
        lower.contains("aac") || lower.contains("mp4a") -> "audio/mp4a-latm"
        lower.contains("flac") -> "audio/flac"
        lower.contains("vorbis") -> "audio/vorbis"
        lower.contains("amr") || lower.contains("3gpp") -> "audio/3gpp"
        lower.contains("raw") -> "audio/raw"
        else -> ""
    }
}

private fun buildLocalCodecInfos(
    codecNames: List<String>,
    mimePrefix: String,
    isEncoder: Boolean,
    fallbackType: (String) -> String,
): List<CodecInfo> {
    val codecNameSet = codecNames.toSet()
    val codecInfos =
        runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                .filter { it.isEncoder == isEncoder }
                .filter { it.name in codecNameSet }
                .map { codecInfo ->
                    codecInfo.toCodecInfo(mimePrefix, isEncoder)
                }
        }.getOrDefault(emptyList())

    if (codecInfos.isNotEmpty()) {
        return codecInfos.sortedWith(compareBy({ it.type }, { it.name }))
    }

    return codecNames.map { name ->
        CodecInfo(
            name = name,
            type = fallbackType(name),
            isEncoder = isEncoder,
            capabilities = "",
        )
    }
}

private fun MediaCodecInfo.toCodecInfo(
    mimePrefix: String,
    isEncoder: Boolean,
): CodecInfo {
    val types = supportedTypes
        .filter { it.startsWith(mimePrefix, ignoreCase = true) }
        .sorted()
    return CodecInfo(
        name = name,
        type = types.joinToString(),
        isEncoder = isEncoder,
        capabilities = types.joinToString(),
    )
}
