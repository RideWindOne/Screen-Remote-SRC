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

package com.mobile.scrcpy.android.core.common.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.util.compat.*

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

    // ============ PendingIntent 兼容 ============

    /**
     * 获取兼容的 PendingIntent Flags
     *
     * Android 6.0 (API 23) 引入了 FLAG_IMMUTABLE
     * Android 12 (API 31) 强制要求指定 FLAG_IMMUTABLE 或 FLAG_MUTABLE
     *
     * @param mutable 是否需要可变的 PendingIntent (默认为不可变)
     * @return 适用于当前 API 级别的 flags
     */
    fun getPendingIntentFlags(mutable: Boolean = false): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mutable) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            }
        } else {
            // API 23 以下不支持 FLAG_IMMUTABLE/FLAG_MUTABLE（理论上不会执行，因为 minSdk=23）
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    /**
     * 创建 USB 权限请求的 PendingIntent
     *
     * Android 14 (API 34) 要求使用显式 Intent 才能使用 FLAG_MUTABLE
     * 使用 FLAG_MUTABLE 可以让系统弹窗显示"始终允许"选项
     *
     * @param context Context 对象
     * @param action 权限请求的 Action
     * @return 适用于 USB 权限请求的 PendingIntent
     */
    fun createUsbPermissionPendingIntent(
        context: Context,
        action: String,
    ): PendingIntent {
        val intent =
            Intent(action).apply {
                if (Build.VERSION.SDK_INT >= API_34_UPSIDE_DOWN_CAKE) {
                    // Android 14+ 要求显式 Intent 才能使用 FLAG_MUTABLE
                    setPackage(context.packageName)
                }
            }

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            getPendingIntentFlags(mutable = true), // 使用 FLAG_MUTABLE 以显示"始终允许"选项
        )
    }

    /**
     * 获取 USB 设备序列号（兼容不同 API 级别）
     */
    fun getUsbDeviceSerialNumber(device: android.hardware.usb.UsbDevice): String? =
        com.mobile.scrcpy.android.core.common.util.compat.getUsbDeviceSerialNumber(device)

    // ============ 前台服务兼容 ============

    /**
     * 启动前台服务（兼容不同 API 级别）
     *
     * Android 8.0 (API 26) 引入了 startForegroundService
     *
     * @param context Context 对象
     * @param intent 服务 Intent
     */
    fun startForegroundServiceCompat(
        context: Context,
        intent: Intent,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * 启动前台通知（兼容不同 API 级别）
     *
     * Android 14 (API 34) 引入了前台服务类型参数
     *
     * @param service 服务实例
     * @param notificationId 通知 ID
     * @param notification 通知对象
     * @param foregroundServiceType 前台服务类型（API 34+），默认为 CONNECTED_DEVICE
     */
    fun startForegroundCompat(
        service: android.app.Service,
        notificationId: Int,
        notification: android.app.Notification,
    ) {
        if (Build.VERSION.SDK_INT >= API_34_UPSIDE_DOWN_CAKE) {
            service.startForeground(
                notificationId,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            service.startForeground(notificationId, notification)
        }
    }

    /**
     * 停止前台服务（兼容不同 API 级别）
     *
     * Android 7.0 (API 24) 引入了 STOP_FOREGROUND_REMOVE 常量
     *
     * @param service 服务实例
     * @param removeNotification 是否移除通知
     */
    fun stopForegroundCompat(
        service: android.app.Service,
        removeNotification: Boolean = true,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val flags =
                if (removeNotification) {
                    android.app.Service.STOP_FOREGROUND_REMOVE
                } else {
                    android.app.Service.STOP_FOREGROUND_DETACH
                }
            service.stopForeground(flags)
        } else {
            @Suppress("DEPRECATION")
            service.stopForeground(removeNotification)
        }
    }

    // ============ 窗口/系统栏兼容 ============

    /**
     * 设置窗口适配系统栏
     */
    fun setDecorFitsSystemWindows(
        window: android.view.Window?,
        decorFitsSystemWindows: Boolean,
    ) = com.mobile.scrcpy.android.core.common.util.compat.setDecorFitsSystemWindows(window, decorFitsSystemWindows)

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

    fun getVideoMimeType(codecName: String): String? = com.mobile.scrcpy.android.core.common.util.compat.getVideoMimeType(codecName)

    fun isAV1Supported(): Boolean = com.mobile.scrcpy.android.core.common.util.compat.isAV1Supported()

    fun getSupportedVideoCodecs(): List<String> = com.mobile.scrcpy.android.core.common.util.compat.getSupportedVideoCodecs()

    fun isHardwareAccelerated(info: android.media.MediaCodecInfo): Boolean =
        com.mobile.scrcpy.android.core.common.util.compat.isHardwareAccelerated(info)

    fun setLowLatencyIfSupported(format: android.media.MediaFormat, lowLatency: Int) =
        com.mobile.scrcpy.android.core.common.util.compat.setLowLatencyIfSupported(format, lowLatency)

    fun setAllowFrameDropIfSupported(format: android.media.MediaFormat, allowFrameDrop: Int) =
        com.mobile.scrcpy.android.core.common.util.compat.setAllowFrameDropIfSupported(format, allowFrameDrop)

    fun getCropRectIfSupported(format: android.media.MediaFormat): android.graphics.Rect? =
        com.mobile.scrcpy.android.core.common.util.compat.getCropRectIfSupported(format)

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
        com.mobile.scrcpy.android.core.common.util.compat.getVibratorCompat(context)

    fun vibrateCompat(vibrator: android.os.Vibrator?, type: String = "tick") =
        com.mobile.scrcpy.android.core.common.util.compat.vibrateCompat(vibrator, type)

    // ============ 触觉反馈兼容 ============

    fun getHapticFeedbackConstant(feedbackType: String): Int =
        com.mobile.scrcpy.android.core.common.util.compat.getHapticFeedbackConstant(feedbackType)

    // ============ 通知兼容 ============

    /**
     * 创建通知渠道（兼容不同 API 级别）
     *
     * Android 8.0 (API 26) 引入了 NotificationChannel
     *
     * @param context Context 对象
     * @param channelId 通知渠道 ID
     * @param channelName 通知渠道名称
     * @param importance 重要性级别（NotificationManager.IMPORTANCE_*）
     * @param description 渠道描述（可选）
     * @param showBadge 是否显示角标（默认 false）
     */
    fun createNotificationChannelCompat(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
        description: String? = null,
        showBadge: Boolean = false,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                android.app
                    .NotificationChannel(
                        channelId,
                        channelName,
                        importance,
                    ).apply {
                        description?.let { this.description = it }
                        setShowBadge(showBadge)
                    }

            val notificationManager =
                context.getSystemService(
                    Context.NOTIFICATION_SERVICE,
                ) as? android.app.NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
        // API 26 以下不需要创建通知渠道
    }

    /**
     * 创建兼容的 NotificationCompat.Builder
     *
     * Android 8.0 (API 26) 引入了 NotificationChannel，必须指定 channelId
     * Android 6.0-7.1 (API 23-25) 不需要 channelId
     *
     * @param context Context 对象
     * @param channelId 通知渠道 ID（API 26+ 必需）
     * @return NotificationCompat.Builder 实例
     */
    fun createNotificationBuilder(
        context: Context,
        channelId: String,
    ): androidx.core.app.NotificationCompat.Builder =
        if (isApiLevel(API_26_OREO)) {
            // Android 8.0+ 必须指定 channelId
            androidx.core.app.NotificationCompat
                .Builder(context, channelId)
        } else {
            // Android 6.0-7.1 不需要 channelId
            @Suppress("DEPRECATION")
            androidx.core.app.NotificationCompat
                .Builder(context)
        }

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
    ) = com.mobile.scrcpy.android.core.common.util.compat.registerReceiverCompat(context, receiver, filter, exported)

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
    ): T? = com.mobile.scrcpy.android.core.common.util.compat.getParcelableExtraCompat(intent, key, clazz)

    // ============ 全屏模式兼容 ============

    fun setFullScreen(window: android.view.Window?, fullscreen: Boolean) =
        com.mobile.scrcpy.android.core.common.util.compat.setFullScreen(window, fullscreen)
}
