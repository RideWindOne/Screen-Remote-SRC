package com.mobile.scrcpy.android.core.common.constants

import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper

/**
 * Scrcpy 常量
 * 包含视频/音频编码、连接监控、解码器、手势、控制流等参数
 */
object ScrcpyConstants {
    // 视频编码

    /** 默认视频编码格式 */
    const val DEFAULT_VIDEO_CODEC = "h264"

    /**
     * 支持的视频编码格式（根据 API 级别动态获取）
     * 使用 ApiCompatHelper.getSupportedVideoCodecs() 获取当前设备支持的编解码器
     */
    val VIDEO_CODECS: List<String>
        get() = ApiCompatHelper.getSupportedVideoCodecs()

    /**
     * 默认最大屏幕尺寸（推荐值）
     * 1080 更适合移动设备间的镜像，平衡性能和清晰度
     * 此值仅用于创建新会话时的预填充，不是运行时默认值
     */
    const val DEFAULT_MAX_SIZE = 1080

    /** 默认最大屏幕尺寸（字符串，用于 UI 预填充） */
    const val DEFAULT_MAX_SIZE_STR = "1080"

    /** 默认码率（整数，单位：bps） */
    const val DEFAULT_VIDEO_BITRATE_INT = 8000000 // 8Mbps

    /** 默认码率（字符串） */
    const val DEFAULT_VIDEO_BITRATE = "4M"

    /** 默认帧率 */
    const val DEFAULT_MAX_FPS = 60

    /** 默认显示 ID */
    const val DEFAULT_DISPLAY_ID = 0

    /** 默认编码器配置 */
    const val DEFAULT_CODEC_OPTIONS = "profile=1,level=52,intra-refresh-period=0"

    // 音频编码

    /** 默认音频编码格式 */
    const val DEFAULT_AUDIO_CODEC = "aac"

    /** 支持的音频编码格式 */
    val AUDIO_CODECS = listOf("opus", "aac", "flac", "raw")

    /** 默认音频码率（单位：bps） */
    const val DEFAULT_AUDIO_BITRATE = 128000 // 128kbps

    /** 默认音频码率（字符串） */
    const val DEFAULT_AUDIO_BITRATE_STR = "128k"

    // 连接监控

    /** Socket 健康检查间隔（毫秒） */
    const val HEALTH_CHECK_INTERVAL_MS = 3000L

    /** 默认音频缓冲（毫秒，Opus/AAC） */
    const val DEFAULT_AUDIO_BUFFER_MS = 50

    /** FLAC 音频缓冲（毫秒） */
    const val FLAC_AUDIO_BUFFER_MS = 120

    /** 默认视频缓冲（毫秒，实时模式） */
    const val DEFAULT_VIDEO_BUFFER_MS = 0

    /** 默认音量 */
    const val DEFAULT_AUDIO_VOLUME = 1.0f

    /** 最小音量 */
    const val MIN_AUDIO_VOLUME = 0.1f

    /** 最大音量 */
    const val MAX_AUDIO_VOLUME = 2.0f

    /** 音量调节步数 */
    const val AUDIO_VOLUME_STEPS = 18

    // 连接参数

    /** 默认连接超时（毫秒） */
    const val DEFAULT_CONNECT_TIMEOUT = 5000L

    /** Socket 读取超时（毫秒） */
    const val SOCKET_READ_TIMEOUT = 10000L

    /** 默认重连延迟（毫秒） */
    const val DEFAULT_RECONNECT_DELAY = 2000L

    /** 最大重连次数 */
    const val MAX_RECONNECT_ATTEMPTS = 3

    /** 本地转发端口 */
    const val LOCAL_FORWARD_PORT = 27183

    // 解码器参数

    /** 解码器输入缓冲区超时（微秒） */
    const val DECODER_INPUT_TIMEOUT_US = 10000L

    /** 解码器输出缓冲区超时（微秒） */
    const val DECODER_OUTPUT_TIMEOUT_US = 10000L

    /** PTS 时间单位转换（微秒转毫秒） */
    const val PTS_TO_MS_DIVISOR = 1000L

    // 手势参数

    /** 短按阈值（毫秒） - 小于此时间视为点击 */
    const val TAP_THRESHOLD = 200L

    /** 长按阈值 1（毫秒） - 显示辅助工具 */
    const val LONG_PRESS_THRESHOLD_1 = 1000L

    /** 长按阈值 2（毫秒） - 预留功能 */
    const val LONG_PRESS_THRESHOLD_2 = 2000L

    /** 滑动阈值（像素） */
    const val SWIPE_THRESHOLD = 100f

    /** 滑动最小距离（像素） - 用于判断是否为有效滑动 */
    const val SWIPE_MIN_DISTANCE = 50f

    // 菜单位置参数

    /** 菜单位置判断阈值（屏幕高度百分比） */
    const val MENU_POSITION_THRESHOLD = 0.3f

    // 震动反馈参数

    /** 短震动时长（毫秒） */
    const val HAPTIC_FEEDBACK_SHORT = 10L

    /** 中等震动时长（毫秒） */
    const val HAPTIC_FEEDBACK_MEDIUM = 20L

    /** 长震动时长（毫秒） */
    const val HAPTIC_FEEDBACK_LONG = 50L

    // 控制流参数

    /** 控制消息队列容量限制（参考 scrcpy SC_CONTROL_MSG_QUEUE_LIMIT） */
    const val CONTROL_MSG_QUEUE_LIMIT = 60
}
