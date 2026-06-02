package com.screen.remote.android.feature.codec.ui

import com.screen.remote.android.core.domain.model.DecoderCapability
import com.screen.remote.android.feature.codec.model.CodecInfo

fun buildVideoDecoderInfos(decoders: List<DecoderCapability>): List<CodecInfo> = buildLocalCodecInfos(decoders, "video/")

fun buildAudioDecoderInfos(decoders: List<DecoderCapability>): List<CodecInfo> = buildLocalCodecInfos(decoders, "audio/")

private fun buildLocalCodecInfos(
    decoders: List<DecoderCapability>,
    mimePrefix: String,
): List<CodecInfo> =
    decoders
        .map { decoder ->
            val types = decoder.mimeTypes.filter { it.startsWith(mimePrefix, ignoreCase = true) }
            CodecInfo(
                name = decoder.name,
                type = types.joinToString(),
                isEncoder = false,
                acceleration = decoder.acceleration,
                mimeTypes = types,
                lowLatencyMimeTypes = decoder.lowLatencyMimeTypes,
                capabilities =
                    buildList {
                        add(decoder.acceleration.name.lowercase())
                        if (decoder.isVendor) add("vendor")
                        if (decoder.isAlias) add("alias=${decoder.aliasOf}")
                        if (decoder.lowLatencyMimeTypes.isNotEmpty()) add("low-latency")
                    }.joinToString(),
            )
        }.sortedWith(compareBy({ it.type }, { it.name }))
