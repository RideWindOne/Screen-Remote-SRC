package com.screen.remote.android.infrastructure.media.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.domain.model.CodecCatalog
import com.screen.remote.android.core.domain.model.CodecMediaType

/**
 * VideoCodecManager - 视频编解码器管理
 * 负责解码器的创建、选择和缓存
 */
class VideoCodecManager(
    private val videoCodec: String,
    cachedDecoderName: String? = null,
    private val allowHardwareDecoders: Boolean = true,
    internal val decoderSelectionPinned: Boolean = false,
    initialRejectedDecoderNames: Set<String> = emptySet(),
) {
    private var selectedDecoderName: String? = cachedDecoderName
    private val rejectedDecoderNames = linkedSetOf<String>().apply { addAll(initialRejectedDecoderNames) }
    private var _lastSizeFailure: DecoderSizeFailure? = null

    internal val lastSizeFailure: DecoderSizeFailure?
        get() = _lastSizeFailure

    var onDecoderSelected: ((decoderName: String) -> Unit)? = null
    var onDecoderRejected: ((decoderName: String) -> Unit)? = null

    val mimeType: String
        get() =
            requireNotNull(CodecCatalog.mimeType(CodecMediaType.VIDEO, videoCodec)) {
                "Unsupported video format: $videoCodec"
            }

    /**
     * 创建解码器 - 优先使用缓存，避免重复检测
     */
    fun createDecoder(
        width: Int,
        height: Int,
    ): MediaCodec? {
        try {
            _lastSizeFailure = null
            val format = MediaFormat.createVideoFormat(mimeType, width, height)
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val decoderInfos =
                codecList.codecInfos.filter { info ->
                    !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
                }
            val policyDecoderInfos =
                decoderInfos.filter { info -> allowHardwareDecoders || !isLikelyHardware(info) }
            var hasSizeCompatibleCandidate = false

            // 1. 优先使用缓存的解码器
            selectedDecoderName?.let { cachedName ->
                val cachedInfo = decoderInfos.firstOrNull { it.name == cachedName }
                val cachedSupportsSize = cachedInfo?.let { supportsCurrentAndRotation(it, width, height) }
                val violatesHardwarePolicy = !allowHardwareDecoders && cachedInfo?.let(::isLikelyHardware) == true
                if (cachedInfo == null || cachedSupportsSize == false || violatesHardwarePolicy || cachedName in rejectedDecoderNames) {
                    val detail =
                        when {
                            cachedInfo == null -> "no longer exists"
                            cachedSupportsSize == false -> "Not supported ${width}x$height"
                            violatesHardwarePolicy -> "Does not comply with pure software decoding strategy"
                            else -> "This session has failed"
                        }
                    LogManager.w(LogTags.VIDEO_DECODER, "Preferred decoder $detail, downgrade to reselect: $cachedName")
                    if (decoderSelectionPinned) {
                        if (cachedSupportsSize == false) {
                            _lastSizeFailure =
                                DecoderSizeFailure(
                                    mimeType = mimeType,
                                    width = width,
                                    height = height,
                                    suggestedMaxSize = findSuggestedMaxSize(listOf(cachedInfo), width, height),
                                )
                        }
                        LogManager.e(
                            LogTags.VIDEO_DECODER,
                            "User fixed video decoder is unavailable and does not perform automatic downgrade: $cachedName"
                        )
                        return null
                    }
                    selectedDecoderName = null
                } else {
                    hasSizeCompatibleCandidate = true
                    try {
                        VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Use cache decoder: $cachedName" }
                        return MediaCodec.createByCodecName(cachedName)
                    } catch (error: Exception) {
                        LogManager.w(LogTags.VIDEO_DECODER, "Cache decoder invalid: $cachedName, recheck")
                        rejectDecoder(cachedName, error)
                        if (decoderSelectionPinned) {
                            return null
                        }
                        selectedDecoderName = null
                    }
                }
            }

            if (decoderSelectionPinned) {
                LogManager.e(LogTags.VIDEO_DECODER, "User fixed video codec not provided or cannot be created")
                return null
            }

            // 2. 缓存失效或不存在，开始检测
            // 系统推荐
            if (allowHardwareDecoders) codecList.findDecoderForFormat(format)?.let { name ->
                val info = codecList.codecInfos.firstOrNull { it.name == name }
                if (info != null && name !in rejectedDecoderNames && isLikelyHardware(info) && !isGoldfishCodec(name) && supportsCurrentAndRotation(
                        info,
                        width,
                        height
                    ) != false
                ) {
                    hasSizeCompatibleCandidate = true
                    try {
                        selectedDecoderName = name
                        VideoDebugLog.d(LogTags.VIDEO_DECODER) { "System recommendation: $name" }
                        return MediaCodec.createByCodecName(name)
                    } catch (error: Exception) {
                        rejectDecoder(name, error)
                    }
                }
            }

            // 手动选择硬件解码器
            for (info in codecList.codecInfos) {
                if (info.isEncoder || !info.supportedTypes.contains(mimeType)) continue
                if (!allowHardwareDecoders || !isLikelyHardware(info) || isGoldfishCodec(info.name) || info.name in rejectedDecoderNames) continue
                if (supportsCurrentAndRotation(info, width, height) == false) continue

                try {
                    hasSizeCompatibleCandidate = true
                    selectedDecoderName = info.name
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Hardware decoding: ${info.name}" }
                    return MediaCodec.createByCodecName(info.name)
                } catch (error: Exception) {
                    rejectDecoder(info.name, error)
                }
            }

            // 软件解码器兜底：优先 OMX.google，其次其他非 c2.android 软件实现，最后才是 c2.android。
            for (info in codecList.codecInfos) {
                if (info.isEncoder || !info.supportedTypes.contains(mimeType)) continue
                if (isLikelyHardware(info) || isGoldfishCodec(info.name) || !isGoogleSoftwareCodec(info.name) || info.name in rejectedDecoderNames) continue
                if (supportsCurrentAndRotation(info, width, height) == false) continue

                try {
                    hasSizeCompatibleCandidate = true
                    selectedDecoderName = info.name
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Software decoding: ${info.name}" }
                    return MediaCodec.createByCodecName(info.name)
                } catch (error: Exception) {
                    rejectDecoder(info.name, error)
                }
            }

            for (info in codecList.codecInfos) {
                if (info.isEncoder || !info.supportedTypes.contains(mimeType)) continue
                if (isLikelyHardware(info) || isGoldfishCodec(info.name) || isAndroidSoftwareCodec(info.name) || info.name in rejectedDecoderNames) continue
                if (supportsCurrentAndRotation(info, width, height) == false) continue

                try {
                    hasSizeCompatibleCandidate = true
                    selectedDecoderName = info.name
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Software decoding: ${info.name}" }
                    return MediaCodec.createByCodecName(info.name)
                } catch (error: Exception) {
                    rejectDecoder(info.name, error)
                }
            }

            for (info in codecList.codecInfos) {
                if (info.isEncoder || !info.supportedTypes.contains(mimeType)) continue
                if (isLikelyHardware(info) || isGoldfishCodec(info.name) || !isAndroidSoftwareCodec(info.name) || info.name in rejectedDecoderNames) continue
                if (supportsCurrentAndRotation(info, width, height) == false) continue

                try {
                    hasSizeCompatibleCandidate = true
                    selectedDecoderName = info.name
                    LogManager.w(LogTags.VIDEO_DECODER, "Finally, the software uses c2.android decoder: ${info.name}")
                    return MediaCodec.createByCodecName(info.name)
                } catch (error: Exception) {
                    rejectDecoder(info.name, error)
                }
            }

            // goldfish 只作为最后兜底。
            for (info in codecList.codecInfos) {
                if (info.isEncoder || !info.supportedTypes.contains(mimeType)) continue
                if (!isGoldfishCodec(info.name) || info.name in rejectedDecoderNames) continue
                if (supportsCurrentAndRotation(info, width, height) == false) continue

                try {
                    hasSizeCompatibleCandidate = true
                    selectedDecoderName = info.name
                    LogManager.w(LogTags.VIDEO_DECODER, "Finally, use the goldfish decoder: ${info.name}")
                    return MediaCodec.createByCodecName(info.name)
                } catch (error: Exception) {
                    rejectDecoder(info.name, error)
                }
            }

            if (!hasSizeCompatibleCandidate && policyDecoderInfos.isNotEmpty()) {
                val suggestedMaxSize = findSuggestedMaxSize(policyDecoderInfos, width, height)
                _lastSizeFailure =
                    DecoderSizeFailure(
                        mimeType = mimeType,
                        width = width,
                        height = height,
                        suggestedMaxSize = suggestedMaxSize,
                    )
                LogManager.e(
                    LogTags.VIDEO_DECODER,
                    "No codec supports ${width}x$height mime=$mimeType suggestedMaxSize=${suggestedMaxSize ?: "none"}",
                )
                return null
            }

            if (!allowHardwareDecoders) {
                LogManager.e(
                    LogTags.VIDEO_DECODER,
                    "There is no decoder available under the software-only decoding strategy"
                )
                return null
            }

            LogManager.e(LogTags.VIDEO_DECODER, "All $mimeType decoder candidates have failed")
            return null
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "Failed to create decoder", e)
            return null
        }
    }

    private fun isLikelyHardware(info: MediaCodecInfo): Boolean = ApiCompatHelper.isHardwareAccelerated(info)

    internal fun rejectDecoder(
        decoderName: String,
        cause: Throwable? = null,
    ) {
        rejectedDecoderNames += decoderName
        onDecoderRejected?.invoke(decoderName)
        if (selectedDecoderName == decoderName) {
            selectedDecoderName = null
        }
        LogManager.w(
            LogTags.VIDEO_DECODER,
            "Decoder eliminated in this session: $decoderName${cause?.message?.let { ", reason=$it" }.orEmpty()}",
        )
    }

    /** 只有 decoder configure + start 成功后，才允许把候选记为本次解析结果。 */
    internal fun reportDecoderSelected(decoderName: String) {
        selectedDecoderName = decoderName
        onDecoderSelected?.invoke(decoderName)
    }

    private fun isGoldfishCodec(name: String): Boolean = name.contains("goldfish", ignoreCase = true)

    private fun isAndroidSoftwareCodec(name: String): Boolean = name.startsWith("c2.android", ignoreCase = true)

    private fun isGoogleSoftwareCodec(name: String): Boolean = name.startsWith("OMX.google", ignoreCase = true)

    private fun supportsSize(
        info: MediaCodecInfo,
        width: Int,
        height: Int,
    ): Boolean? =
        runCatching {
            info.getCapabilitiesForType(mimeType).videoCapabilities?.isSizeSupported(width, height)
        }.getOrNull()

    private fun supportsCurrentAndRotation(
        info: MediaCodecInfo,
        width: Int,
        height: Int,
    ): Boolean? {
        val current = supportsSize(info, width, height)
        val rotated = supportsSize(info, height, width)
        return when {
            current == false || rotated == false -> false
            current == true && rotated == true -> true
            else -> null
        }
    }

    private fun findSuggestedMaxSize(
        decoderInfos: List<MediaCodecInfo>,
        width: Int,
        height: Int,
    ): Int? {
        val currentLongEdge = maxOf(width, height)
        return decoderFallbackLongEdges(currentLongEdge).firstOrNull { maxLongEdge ->
            val candidate = scaleVideoSizeToLongEdge(width, height, maxLongEdge)
            decoderInfos.any { info ->
                supportsSize(info, candidate.width, candidate.height) == true &&
                    supportsSize(info, candidate.height, candidate.width) == true
            }
        }
    }
}

internal data class DecoderSizeFailure(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val suggestedMaxSize: Int?,
)

internal data class ScaledVideoSize(
    val width: Int,
    val height: Int,
)

internal fun decoderFallbackLongEdges(currentLongEdge: Int): List<Int> =
    listOf(3840, 2560, 2048, 1920, 1600, 1280, 1080, 720, 540).filter { it < currentLongEdge }

internal fun scaleVideoSizeToLongEdge(
    width: Int,
    height: Int,
    maxLongEdge: Int,
): ScaledVideoSize {
    require(width > 0 && height > 0 && maxLongEdge > 0)
    val currentLongEdge = maxOf(width, height)
    if (currentLongEdge <= maxLongEdge) return ScaledVideoSize(width, height)

    val scale = maxLongEdge.toDouble() / currentLongEdge.toDouble()
    fun aligned(value: Int): Int = ((value * scale).toInt().coerceAtLeast(2) / 2) * 2
    return ScaledVideoSize(
        width = aligned(width),
        height = aligned(height),
    )
}
