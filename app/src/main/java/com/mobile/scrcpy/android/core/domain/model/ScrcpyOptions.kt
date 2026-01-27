package com.mobile.scrcpy.android.core.domain.model

/**
 * Scrcpy 运行时配置选项
 * 用于配置 scrcpy-server 的启动参数
 */
data class ScrcpyOptions(
    val maxSize: Int = 1920,
    val bitRate: Int = 8000000,
    val maxFps: Int = 60,
    val displayId: Int = 0,
    val showTouches: Boolean = false,
    val stayAwake: Boolean = true,
    val codecOptions: String = "profile=1,level=2",
    val encoderName: String? = null,
    val powerOffOnClose: Boolean = false,
    val enableAudio: Boolean = false,
    val videoCodec: String = "h264",
    val audioCodec: String = "opus",
    val audioEncoder: String? = null,
    val audioBufferMs: Int? = null
) {
    /**
     * 转换为 scrcpy-server 启动参数
     */
    fun toServerArgs(): List<String> {
        return buildList {
            add("log_level=info")
            add("max_size=$maxSize")
            add("video_bit_rate=$bitRate")
            add("max_fps=$maxFps")
            add("display_id=$displayId")
            add("show_touches=$showTouches")
            add("stay_awake=$stayAwake")
            add("video_codec_options=$codecOptions")
            add("tunnel_forward=true")
            add("video_codec=$videoCodec")
            if (encoderName != null) {
                add("video_encoder=$encoderName")
            }
            add("power_off_on_close=$powerOffOnClose")
            
            // 音频参数
            if (enableAudio) {
                add("audio_codec=$audioCodec")
                add("audio_bit_rate=128000")
                if (audioEncoder != null) {
                    add("audio_encoder=$audioEncoder")
                }
                if (audioBufferMs != null) {
                    add("audio_buffer=$audioBufferMs")
                }
            } else {
                add("audio=false")
            }
        }
    }
}

/**
 * 解析 maxSize 字符串为整数
 */
fun String.parseMaxSize(): Int? {
    return when {
        this.isEmpty() -> null
        this == "0" -> null
        else -> this.toIntOrNull()?.takeIf { it > 0 }
    }
}

/**
 * 将 maxSize 整数转换为字符串（用于存储）
 */
fun Int?.toMaxSizeString(): String {
    return this?.toString() ?: ""
}
