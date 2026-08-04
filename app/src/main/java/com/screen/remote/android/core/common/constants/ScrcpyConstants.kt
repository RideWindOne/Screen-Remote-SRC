package com.screen.remote.android.core.common.constants

/**
 * Scrcpy 常量
 * 包含视频/音频编码、连接监控、解码器、手势、控制流等参数
 */
object ScrcpyConstants {
    // 视频编码

    /** 默认码率（整数，单位：bps） */
    const val DEFAULT_VIDEO_BITRATE_INT = 4_000_000 // 4Mbps

    /** 默认码率（字符串） */
    const val DEFAULT_VIDEO_BITRATE = "4M"

    /** 默认帧率 */
    const val DEFAULT_MAX_FPS = 60

    /** 默认编码器配置。保持为空，避免在部分设备上因 profile/level 不兼容导致 server 启动失败。 */
    const val DEFAULT_CODEC_OPTIONS = ""

    // 音频编码

    // 连接监控

    /** Socket 健康检查间隔（毫秒） */
    const val HEALTH_CHECK_INTERVAL_MS = 3000L

    /**
     * Control socket 上普通 MOVE 的最小发送间隔。
     *
     * scrcpy 的触控协议没有背压反馈，持续把本机 120/240Hz 的多指 MOVE 全量写入 socket
     * 会让 server/InputManager 队列积压，后续 DOWN/UP 也只能排在旧 MOVE 后面。5ms 是全局
     * MOVE 上限；DOWN/UP 会作为顺序屏障立即发送，不受这个间隔限制。
     */
    const val GAME_CONTROL_TOUCH_MOVE_INTERVAL_MS = 5L

    /**
     * 被控端可观察到的最短按压时长。
     *
     * 控制协议不携带客户端事件时间。如果发送协程在一次 drain 中同时拿到 DOWN 和 UP，
     * server 会给两者记录几乎相同的时间，游戏可能漏掉这个短按。这里只延后 UP，不延后 DOWN。
     */
    const val GAME_CONTROL_MIN_TOUCH_HOLD_MS = 20L

    /** 游戏模式等待硬解输出的短窗口；命中时可少等一个视频包周期。 */
    const val GAME_VIDEO_OUTPUT_DEQUEUE_TIMEOUT_US = 2_000L

    // 连接参数

    /** Socket 读取超时（毫秒） */
    const val SOCKET_READ_TIMEOUT = 10000L

    /** 默认重连延迟（毫秒） */
    const val DEFAULT_RECONNECT_DELAY = 2000L

    /** 最大重连次数 */
    const val MAX_RECONNECT_ATTEMPTS = 3

    // 解码器参数

    // 手势参数

    // 菜单位置参数

    // 震动反馈参数

    // 控制流参数

    /** 控制流空闲保活间隔（毫秒）
     * 用于避免空闲时 control channel 被底层链路回收。
     */
    const val CONTROL_KEEPALIVE_INTERVAL_MS = 3000L
}
