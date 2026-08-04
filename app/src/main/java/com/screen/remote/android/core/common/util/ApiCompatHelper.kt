/*
 * API 版本兼容性辅助工具 - 主入口
 *
 * 文件拆分说明：
 * - MediaApiCompat.kt: MediaCodec、音视频编解码器相关 API
 * - NetworkApiCompat.kt: 网络、广播接收器相关 API
 * - StorageApiCompat.kt: USB、Intent、Parcelable 相关 API
 * - UiApiCompat.kt: 窗口、系统栏、输入法、触觉反馈、震动相关 API
 *
 * 本文件保留：系统服务、通知、PendingIntent、权限等核心 API
 */

package com.screen.remote.android.core.common.util

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * API 版本兼容性辅助工具类
 *
 * 用于统一管理不同 Android 版本之间的 API 差异，提供向后兼容的方法。
 * 所有版本相关的判断和兼容处理都应该通过这个类进行。
 */
object ApiCompatHelper {

    // ============ API 级别常量（语义化） ============

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

    /**
     * 显示软键盘
     */
    fun showSoftInput(view: android.view.View) =
        com.screen.remote.android.core.common.util.compat.showSoftInputCompat(view)

    /** Android 9 以下返回零边距。 */
    fun getDisplayCutoutSafeInsets(windowInsets: android.view.WindowInsets): android.graphics.Rect =
        com.screen.remote.android.core.common.util.compat.getDisplayCutoutSafeInsets(windowInsets)

    // ============ 共享内存（ashmem）兼容说明 ============

    fun isHardwareAccelerated(info: android.media.MediaCodecInfo): Boolean =
        com.screen.remote.android.core.common.util.compat.isHardwareAccelerated(info)

    fun setLowLatencyIfSupported(format: android.media.MediaFormat, lowLatency: Int) =
        com.screen.remote.android.core.common.util.compat.setLowLatencyIfSupported(format, lowLatency)

    fun setAllowFrameDropIfSupported(format: android.media.MediaFormat, allowFrameDrop: Int) =
        com.screen.remote.android.core.common.util.compat.setAllowFrameDropIfSupported(format, allowFrameDrop)

    fun getPcmEncodingOrDefault(format: android.media.MediaFormat, defaultEncoding: Int): Int =
        com.screen.remote.android.core.common.util.compat.getPcmEncodingOrDefault(format, defaultEncoding)

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

    fun isValidWifiLinkSpeed(linkSpeedMbps: Int): Boolean =
        com.screen.remote.android.core.common.util.compat.isValidWifiLinkSpeed(linkSpeedMbps)

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
