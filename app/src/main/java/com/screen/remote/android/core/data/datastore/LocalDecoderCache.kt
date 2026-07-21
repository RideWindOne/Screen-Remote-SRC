package com.screen.remote.android.core.data.datastore

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.CodecAcceleration
import com.screen.remote.android.core.domain.model.CodecCatalog
import com.screen.remote.android.core.domain.model.DecoderCapability

/**
 * 本地解码器缓存单例
 * 在 Application 初始化时注入 Context，之后无需再传 Context
 */
object LocalDecoderCache {
    private lateinit var manager: LocalDecoderManager

    /**
     * 初始化（在 Application.onCreate() 中调用）
     */
    fun init(context: Context) {
        manager = LocalDecoderManager(context.applicationContext)
    }

    /**
     * 获取本地视频解码器列表（优先使用持久化数据）
     */
    suspend fun getVideoDecoders(): List<DecoderCapability> {
        val runtimeSignature = manager.getLocalRuntimeSignature()
        val data = manager.getData()

        return if (data.isValid(runtimeSignature) && data.videoDecoders.isNotEmpty()) {
            // 使用持久化数据
            LogManager.d(LogTags.VIDEO_DECODER, "Use persistent list of local video decoders (${data.videoDecoders.size})")
            data.videoDecoders
        } else {
            // 重新检测并保存
            LogManager.d(LogTags.VIDEO_DECODER, "Detect local video decoder...")
            val decoders = detectAllVideoDecoders()
            if (decoders.isNotEmpty()) {
                manager.saveVideoDecoders(decoders)
                LogManager.d(LogTags.VIDEO_DECODER, "Saved local video decoder list (${decoders.size})")
            }
            decoders
        }
    }

    /**
     * 获取本地音频解码器列表（优先使用持久化数据）
     */
    suspend fun getAudioDecoders(): List<DecoderCapability> {
        val runtimeSignature = manager.getLocalRuntimeSignature()
        val data = manager.getData()

        return if (data.isValid(runtimeSignature) && data.audioDecoders.isNotEmpty()) {
            // 使用持久化数据
            LogManager.d(LogTags.AUDIO_DECODER, "Use a persistent list of local audio decoders (${data.audioDecoders.size})")
            data.audioDecoders
        } else {
            // 重新检测并保存
            LogManager.d(LogTags.AUDIO_DECODER, "Detect local audio codec...")
            val decoders = detectAllAudioDecoders()
            if (decoders.isNotEmpty()) {
                manager.saveAudioDecoders(decoders)
                LogManager.d(LogTags.AUDIO_DECODER, "Saved local audio codec list (${decoders.size})")
            }
            decoders
        }
    }

    /**
     * 清空持久化数据（用于调试或重置）
     */
    suspend fun clear() {
        manager.clearData()
        LogManager.d(LogTags.VIDEO_DECODER, "Local decoder data cleared")
    }

    /**
     * 检测所有本地视频解码器
     */
    private fun detectAllVideoDecoders(): List<DecoderCapability> =
        try {
            val supportedMimes = CodecCatalog.videoSpecs.map { it.mimeType }.toSet()
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .filter { !it.isEncoder }
                .filter { codecInfo ->
                    codecInfo.supportedTypes.any { type -> supportedMimes.any { it.equals(type, ignoreCase = true) } }
                }.map { it.toCapability(supportedMimes) }
                .distinctBy { it.name }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "Failed to detect local video decoder: ${e.message}", e)
            emptyList()
        }

    /**
     * 检测所有本地音频解码器
     */
    private fun detectAllAudioDecoders(): List<DecoderCapability> =
        try {
            val supportedMimes = CodecCatalog.audioSpecs.filterNot { it.name == "raw" }.map { it.mimeType }.toSet()
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .filter { !it.isEncoder }
                .filter { codecInfo ->
                    codecInfo.supportedTypes.any { type -> supportedMimes.any { it.equals(type, ignoreCase = true) } }
                }.map { it.toCapability(supportedMimes) }
                .distinctBy { it.name }
        } catch (e: Exception) {
            LogManager.e(LogTags.AUDIO_DECODER, "Failed to detect local audio codec: ${e.message}", e)
            emptyList()
        }

    private fun MediaCodecInfo.toCapability(allowedMimeTypes: Set<String>): DecoderCapability {
        val mimeTypes =
            supportedTypes
                .filter { type -> allowedMimeTypes.any { it.equals(type, ignoreCase = true) } }
                .sorted()
        val acceleration =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                when {
                    isSoftwareOnly -> CodecAcceleration.SOFTWARE
                    isHardwareAccelerated -> CodecAcceleration.HARDWARE
                    else -> CodecAcceleration.HYBRID
                }
            } else {
                when {
                    name.startsWith("OMX.google", ignoreCase = true) ||
                        name.startsWith("c2.android", ignoreCase = true) -> CodecAcceleration.SOFTWARE
                    else -> CodecAcceleration.UNKNOWN
                }
            }
        val lowLatencyMimeTypes =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mimeTypes.filter { mimeType ->
                    runCatching {
                        getCapabilitiesForType(mimeType)
                            .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)
                    }.getOrDefault(false)
                }
            } else {
                emptyList()
            }

        return DecoderCapability(
            name = name,
            mimeTypes = mimeTypes,
            acceleration = acceleration,
            isVendor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isVendor,
            aliasOf =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isAlias) {
                    canonicalName.takeUnless { it == name }
                } else {
                    null
                },
            lowLatencyMimeTypes = lowLatencyMimeTypes,
        )
    }
}
