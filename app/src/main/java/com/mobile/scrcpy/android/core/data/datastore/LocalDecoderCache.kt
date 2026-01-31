package com.mobile.scrcpy.android.core.data.datastore

import android.content.Context
import android.media.MediaCodecList
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager

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
    suspend fun getVideoDecoders(): List<String> {
        val deviceId = manager.getLocalDeviceId()
        val data = manager.getData()

        return if (data.isValid(deviceId) && data.videoDecoders.isNotEmpty()) {
            // 使用持久化数据
            LogManager.d(LogTags.VIDEO_DECODER, "使用持久化的本地视频解码器列表 (${data.videoDecoders.size} 个)")
            data.videoDecoders
        } else {
            // 重新检测并保存
            LogManager.d(LogTags.VIDEO_DECODER, "检测本地视频解码器...")
            val decoders = detectAllVideoDecoders()
            if (decoders.isNotEmpty()) {
                manager.saveVideoDecoders(decoders)
                LogManager.d(LogTags.VIDEO_DECODER, "已保存本地视频解码器列表 (${decoders.size} 个)")
            }
            decoders
        }
    }

    /**
     * 获取本地音频解码器列表（优先使用持久化数据）
     */
    suspend fun getAudioDecoders(): List<String> {
        val deviceId = manager.getLocalDeviceId()
        val data = manager.getData()

        return if (data.isValid(deviceId) && data.audioDecoders.isNotEmpty()) {
            // 使用持久化数据
            LogManager.d(LogTags.AUDIO_DECODER, "使用持久化的本地音频解码器列表 (${data.audioDecoders.size} 个)")
            data.audioDecoders
        } else {
            // 重新检测并保存
            LogManager.d(LogTags.AUDIO_DECODER, "检测本地音频解码器...")
            val decoders = detectAllAudioDecoders()
            if (decoders.isNotEmpty()) {
                manager.saveAudioDecoders(decoders)
                LogManager.d(LogTags.AUDIO_DECODER, "已保存本地音频解码器列表 (${decoders.size} 个)")
            }
            decoders
        }
    }

    /**
     * 清空持久化数据（用于调试或重置）
     */
    suspend fun clear() {
        manager.clearData()
        LogManager.d(LogTags.VIDEO_DECODER, "已清空本地解码器数据")
    }

    /**
     * 检测所有本地视频解码器
     */
    private fun detectAllVideoDecoders(): List<String> =
        try {
            MediaCodecList(MediaCodecList.ALL_CODECS)
                .codecInfos
                .filter { !it.isEncoder }
                .filter { codecInfo ->
                    codecInfo.supportedTypes.any { it.startsWith("video/") }
                }.map { it.name }
                .distinct()
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "检测本地视频解码器失败: ${e.message}", e)
            emptyList()
        }

    /**
     * 检测所有本地音频解码器
     */
    private fun detectAllAudioDecoders(): List<String> =
        try {
            MediaCodecList(MediaCodecList.ALL_CODECS)
                .codecInfos
                .filter { !it.isEncoder }
                .filter { codecInfo ->
                    codecInfo.supportedTypes.any { it.startsWith("audio/") }
                }.map { it.name }
                .distinct()
        } catch (e: Exception) {
            LogManager.e(LogTags.AUDIO_DECODER, "检测本地音频解码器失败: ${e.message}", e)
            emptyList()
        }
}
