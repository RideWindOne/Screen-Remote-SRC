/*
 * API 版本兼容性辅助工具 - 主入口
 * 
 * 文件拆分说明：
 * - MediaApiCompat.kt: MediaCodec、音视频编解码器相关 API
 * - NetworkApiCompat.kt: 网络、广播接收器相关 API
 * - StorageApiCompat.kt: USB、Intent、Parcelable 相关 API
 * - UiApiCompat.kt: 窗口、系统栏、触觉反馈、震动相关 API
 * 
 * 本文件保留：系统服务、通知、PendingIntent、权限等核心 API
 */

package com.screen.remote.android.core.common.util

import android.content.Context
import android.content.Intent
import android.os.Build
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.compat.*

/**
 * API 版本兼容性辅助工具类
 *
 * 用于统一管理不同 Android 版本之间的 API 差异，提供向后兼容的方法。
 * 所有版本相关的判断和兼容处理都应该通过这个类进行。
 */
object ApiCompatHelper {
    /**
     * 当前设备的 API 级别
     */
    val currentApiLevel = Build.VERSION.SDK_INT

    /**
     * 判断当前 API 级别是否大于等于指定级别
     */
    fun isApiLevel(apiLevel: Int): Boolean = currentApiLevel >= apiLevel

    // ============ API 级别常量（语义化） ============

    /** Android 6.0 Marshmallow - API 23 (项目最小 SDK) */
    const val API_23_MARSHMALLOW = Build.VERSION_CODES.M

    /** Android 7.0 Nougat - API 24 */
    const val API_24_NOUGAT = Build.VERSION_CODES.N

    /** Android 8.0 Oreo - API 26 */
    const val API_26_OREO = Build.VERSION_CODES.O

    /** Android 10 - API 29 */
    const val API_29_Q = 29

    /** Android 11 - API 30 */
    const val API_30_R = Build.VERSION_CODES.R

    /** Android 13 - API 33 */
    const val API_33_TIRAMISU = Build.VERSION_CODES.TIRAMISU

    /** Android 14 - API 34 */
    const val API_34_UPSIDE_DOWN_CAKE = Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** Android 15 - API 35 */
    const val API_35_VANILLA_ICE_CREAM = 35

    /** Android 16 - API 36 (Preview) */
    const val API_36_BAKLAVA = 36

    /**
     * 获取 USB 设备序列号（兼容不同 API 级别）
     */
    fun getUsbDeviceSerialNumber(device: android.hardware.usb.UsbDevice): String? =
        com.screen.remote.android.core.common.util.compat.getUsbDeviceSerialNumber(device)

    // ============ 窗口/系统栏兼容 ============

    /**
     * 设置窗口适配系统栏
     */
    fun setDecorFitsSystemWindows(
        window: android.view.Window?,
        decorFitsSystemWindows: Boolean,
    ) = com.screen.remote.android.core.common.util.compat.setDecorFitsSystemWindows(window, decorFitsSystemWindows)

    // ============ 共享内存（ashmem）兼容说明 ============

    /**
     * Android 共享内存（ashmem）机制说明
     *
     * Android 10 (API 29) 废弃了 ashmem pinning 机制，改用其他内存管理方式。
     * 如果看到 "Pinning is deprecated since Android Q. Please use trim or other methods" 警告，
     * 这是正常的系统日志，不影响功能。
     *
     * 常见触发场景：
     * 1. Compose/View UI 初始化 - AssetManager 加载资源时（最常见）
     * 2. UsbManager.openDevice() - USB 设备连接时
     * 3. MediaCodec 使用 Surface 时
     * 4. 其他使用共享内存的系统 API
     *
     * 注意：这是 Android 系统底层的警告，应用层无法避免，可以安全忽略。
     */

    // ============ MediaCodec 兼容 ============

    fun getVideoMimeType(codecName: String): String? = com.screen.remote.android.core.common.util.compat.getVideoMimeType(codecName)

    fun isAV1Supported(): Boolean = com.screen.remote.android.core.common.util.compat.isAV1Supported()

    fun getSupportedVideoCodecs(): List<String> = com.screen.remote.android.core.common.util.compat.getSupportedVideoCodecs()

    fun isHardwareAccelerated(info: android.media.MediaCodecInfo): Boolean =
        com.screen.remote.android.core.common.util.compat.isHardwareAccelerated(info)

    fun setLowLatencyIfSupported(format: android.media.MediaFormat, lowLatency: Int) =
        com.screen.remote.android.core.common.util.compat.setLowLatencyIfSupported(format, lowLatency)

    fun setAllowFrameDropIfSupported(format: android.media.MediaFormat, allowFrameDrop: Int) =
        com.screen.remote.android.core.common.util.compat.setAllowFrameDropIfSupported(format, allowFrameDrop)

    fun getCropRectIfSupported(format: android.media.MediaFormat): android.graphics.Rect? =
        com.screen.remote.android.core.common.util.compat.getCropRectIfSupported(format)

    // ============ 权限兼容 ============

    /**
     * 判断是否需要请求通知权限
     *
     * Android 13 (API 33) 引入了 POST_NOTIFICATIONS 运行时权限
     *
     * @return 是否需要请求通知权限
     */
    fun needsNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // ============ Vibrator 兼容 ============

    fun getVibratorCompat(context: Context): android.os.Vibrator? =
        com.screen.remote.android.core.common.util.compat.getVibratorCompat(context)

    fun vibrateCompat(vibrator: android.os.Vibrator?, type: String = "tick") =
        com.screen.remote.android.core.common.util.compat.vibrateCompat(vibrator, type)

    // ============ 触觉反馈兼容 ============

    fun getHapticFeedbackConstant(feedbackType: String): Int =
        com.screen.remote.android.core.common.util.compat.getHapticFeedbackConstant(feedbackType)

    // ============ 日志输出 ============

    /**
     * 输出当前设备的 API 级别信息
     */
    fun logApiInfo() {
        LogManager.i(
            LogTags.APP,
            "设备 API 信息: SDK_INT=${Build.VERSION.SDK_INT}, " +
                "RELEASE=${Build.VERSION.RELEASE}, " +
                "CODENAME=${Build.VERSION.CODENAME}",
        )
    }

    // ============ BroadcastReceiver 兼容 ============

    fun registerReceiver(
        context: Context,
        receiver: android.content.BroadcastReceiver,
        filter: android.content.IntentFilter,
        exported: Boolean = false,
    ) = com.screen.remote.android.core.common.util.compat.registerReceiverCompat(context, receiver, filter, exported)

    fun registerReceiverCompat(
        context: Context,
        receiver: android.content.BroadcastReceiver,
        filter: android.content.IntentFilter,
        exported: Boolean = false,
    ) = registerReceiver(context, receiver, filter, exported)

    // ============ Intent 兼容 ============

    fun <T : android.os.Parcelable> getParcelableExtraCompat(
        intent: Intent,
        key: String,
        clazz: Class<T>,
    ): T? = com.screen.remote.android.core.common.util.compat.getParcelableExtraCompat(intent, key, clazz)

    // ============ 全屏模式兼容 ============

    fun setFullScreen(window: android.view.Window?, fullscreen: Boolean) =
        com.screen.remote.android.core.common.util.compat.setFullScreen(window, fullscreen)
}
