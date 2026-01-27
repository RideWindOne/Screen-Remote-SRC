package com.mobile.scrcpy.android.core.common.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.Window
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager

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
    val currentApiLevel: Int
        get() = Build.VERSION.SDK_INT

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
    fun getPendingIntentFlags(mutable: Boolean = false): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mutable) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            }
        } else {
            // API 23 以下不支持 FLAG_IMMUTABLE/FLAG_MUTABLE（理论上不会执行，因为 minSdk=23）
            PendingIntent.FLAG_UPDATE_CURRENT
        }
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
    fun createUsbPermissionPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(action).apply {
            if (Build.VERSION.SDK_INT >= API_34_UPSIDE_DOWN_CAKE) {
                // Android 14+ 要求显式 Intent 才能使用 FLAG_MUTABLE
                setPackage(context.packageName)
            }
        }
        
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            getPendingIntentFlags(mutable = true)  // 使用 FLAG_MUTABLE 以显示"始终允许"选项
        )
    }

    /**
     * 获取 USB 设备序列号（兼容不同 API 级别）
     *
     * Android 6.0 (API 23) 之前无法获取序列号
     *
     * @param device USB 设备对象
     * @return 序列号，如果无法获取则返回 null
     */
    fun getUsbDeviceSerialNumber(device: android.hardware.usb.UsbDevice): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                device.serialNumber
            } else {
                null
            }
        } catch (e: SecurityException) {
            // 没有权限时无法获取序列号
            null
        }
    }

    // ============ 前台服务兼容 ============

    /**
     * 启动前台服务（兼容不同 API 级别）
     *
     * Android 8.0 (API 26) 引入了 startForegroundService
     *
     * @param context Context 对象
     * @param intent 服务 Intent
     */
    fun startForegroundServiceCompat(context: Context, intent: Intent) {
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
            service.startForeground(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
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
    fun stopForegroundCompat(service: android.app.Service, removeNotification: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val flags = if (removeNotification) {
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
     *
     * Android 11 (API 30) 引入了 setDecorFitsSystemWindows
     *
     * @param window 窗口对象
     * @param decorFitsSystemWindows 是否适配系统窗口
     */
    @Suppress("DEPRECATION")
    fun setDecorFitsSystemWindows(window: Window?, decorFitsSystemWindows: Boolean) {
        window ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(decorFitsSystemWindows)
        } else {
            if (!decorFitsSystemWindows) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            }
        }
    }

    // ============ MediaCodec 兼容 ============

    /**
     * 获取视频编解码器的 MIME 类型（兼容不同 API 级别）
     *
     * Android 10 (API 29) 引入了 MediaFormat.MIMETYPE_VIDEO_AV1
     *
     * @param codecName 编解码器名称（如 "h264", "h265", "av1"）
     * @return MIME 类型字符串，如果不支持则返回 null
     */
    fun getVideoMimeType(codecName: String): String? {
        return when (codecName.lowercase()) {
            "h264", "avc" -> android.media.MediaFormat.MIMETYPE_VIDEO_AVC
            "h265", "hevc" -> android.media.MediaFormat.MIMETYPE_VIDEO_HEVC
            "av1" -> {
                if (Build.VERSION.SDK_INT >= 29) {
                    android.media.MediaFormat.MIMETYPE_VIDEO_AV1
                } else {
                    // API 29 以下不支持 AV1
                    null
                }
            }
            "vp8" -> android.media.MediaFormat.MIMETYPE_VIDEO_VP8
            "vp9" -> android.media.MediaFormat.MIMETYPE_VIDEO_VP9
            else -> null
        }
    }

    /**
     * 判断当前设备是否支持 AV1 编解码器
     *
     * Android 10 (API 29) 引入了 AV1 支持
     *
     * @return 是否支持 AV1
     */
    fun isAV1Supported(): Boolean {
        return Build.VERSION.SDK_INT >= 29
    }

    /**
     * 获取支持的视频编解码器列表（根据 API 级别过滤）
     *
     * @return 当前设备支持的编解码器列表
     */
    fun getSupportedVideoCodecs(): List<String> {
        val codecs = mutableListOf("h264", "h265")
        if (isAV1Supported()) {
            codecs.add("av1")
        }
        return codecs
    }

    /**
     * 判断 MediaCodecInfo 是否为硬件加速编解码器
     *
     * Android 10 (API 29) 引入了 isHardwareAccelerated 和 isSoftwareOnly
     *
     * @param info MediaCodecInfo 对象
     * @return 是否为硬件加速编解码器
     */
    fun isHardwareAccelerated(info: android.media.MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= 29) {
            info.isHardwareAccelerated && !info.isSoftwareOnly
        } else {
            // API 29 以下通过名称判断（Google 的软件编解码器以 OMX.google 开头）
            !info.name.startsWith("OMX.google", ignoreCase = true)
        }
    }

    /**
     * 安全地设置 MediaFormat 的 KEY_LOW_LATENCY
     *
     * Android 11 (API 30) 引入了 KEY_LOW_LATENCY
     *
     * @param format MediaFormat 对象
     * @param lowLatency 是否启用低延迟模式（1 启用，0 禁用）
     */
    fun setLowLatencyIfSupported(format: android.media.MediaFormat, lowLatency: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            format.setInteger(android.media.MediaFormat.KEY_LOW_LATENCY, lowLatency)
        }
        // API 30 以下不支持此参数，忽略
    }

    /**
     * 安全地设置 MediaFormat 的 KEY_ALLOW_FRAME_DROP
     *
     * Android 11 (API 30) 引入了 KEY_ALLOW_FRAME_DROP
     *
     * @param format MediaFormat 对象
     * @param allowFrameDrop 是否允许丢帧
     */
    fun setAllowFrameDropIfSupported(format: android.media.MediaFormat, allowFrameDrop: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            format.setInteger(android.media.MediaFormat.KEY_ALLOW_FRAME_DROP, allowFrameDrop)
        }
        // API 30 以下不支持此参数，忽略
    }

    /**
     * 安全地从 MediaFormat 获取裁剪区域
     *
     * Android 6.0 (API 23) 正式支持 crop-left/right/top/bottom
     *
     * @param format MediaFormat 对象
     * @return 裁剪区域，如果不支持或不存在则返回 null
     */
    fun getCropRectIfSupported(format: android.media.MediaFormat): android.graphics.Rect? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                format.containsKey("crop-left")) {
                val left = format.getInteger("crop-left")
                val right = format.getInteger("crop-right")
                val top = format.getInteger("crop-top")
                val bottom = format.getInteger("crop-bottom")
                android.graphics.Rect(left, top, right, bottom)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ============ 权限兼容 ============

    /**
     * 判断是否需要请求通知权限
     *
     * Android 13 (API 33) 引入了 POST_NOTIFICATIONS 运行时权限
     *
     * @return 是否需要请求通知权限
     */
    fun needsNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    // ============ Vibrator 兼容 ============

    /**
     * 获取兼容的 Vibrator 实例
     *
     * Android 12 (API 31) 引入了 VibratorManager
     *
     * @param context Context 对象
     * @return Vibrator 实例，如果设备不支持则返回 null
     */
    fun getVibratorCompat(context: Context): android.os.Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }

    /**
     * 触发震动反馈（兼容不同 API 级别）
     *
     * Android 10 (API 29) 引入了预定义震动效果
     * Android 8.0 (API 26) 引入了 VibrationEffect
     * Android 6.0-7.1 (API 23-25) 使用旧版 vibrate(long)
     *
     * @param vibrator Vibrator 实例
     * @param type 震动类型：tick(轻点), click(点击), heavy(重击), double(双击)
     */
    fun vibrateCompat(vibrator: android.os.Vibrator?, type: String = "tick") {
        vibrator ?: return
        if (!vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                // Android 10+ 使用预定义效果
                val effect = when (type) {
                    "tick" -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK)
                    "click" -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK)
                    "heavy" -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_HEAVY_CLICK)
                    "double" -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_DOUBLE_CLICK)
                    else -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK)
                }
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8-9 使用 VibrationEffect.createOneShot
                val duration = when (type) {
                    "tick" -> 10L
                    "click" -> 20L
                    "heavy" -> 50L
                    "double" -> 30L
                    else -> 10L
                }
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                // Android 6.0-7.1 使用旧版 vibrate(long)
                val duration = when (type) {
                    "tick" -> 10L
                    "click" -> 20L
                    "heavy" -> 50L
                    "double" -> 30L
                    else -> 10L
                }
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.APP, "震动失败: ${e.message}", e)
        }
    }

    // ============ 触觉反馈兼容 ============

    /**
     * 获取兼容的触觉反馈常量
     *
     * Android 11 (API 30) 引入了 REJECT, CONFIRM, GESTURE_START, GESTURE_END 等新常量
     *
     * @param feedbackType 反馈类型：reject(拒绝), confirm(确认), gesture_start(手势开始), gesture_end(手势结束)
     * @return 适用于当前 API 级别的触觉反馈常量
     */
    fun getHapticFeedbackConstant(feedbackType: String): Int {
        return when (feedbackType) {
            "reject" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.view.HapticFeedbackConstants.REJECT
                } else {
                    // API 30 以下使用 LONG_PRESS 作为替代（较强的反馈）
                    android.view.HapticFeedbackConstants.LONG_PRESS
                }
            }
            "confirm" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.view.HapticFeedbackConstants.CONFIRM
                } else {
                    // API 30 以下使用 CONTEXT_CLICK 作为替代
                    android.view.HapticFeedbackConstants.CONTEXT_CLICK
                }
            }
            "gesture_start" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.view.HapticFeedbackConstants.GESTURE_START
                } else {
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY
                }
            }
            "gesture_end" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.view.HapticFeedbackConstants.GESTURE_END
                } else {
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY
                }
            }
            else -> android.view.HapticFeedbackConstants.CLOCK_TICK
        }
    }

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
        showBadge: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                channelName,
                importance
            ).apply {
                description?.let { this.description = it }
                setShowBadge(showBadge)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
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
    fun createNotificationBuilder(context: Context, channelId: String): androidx.core.app.NotificationCompat.Builder {
        return if (isApiLevel(API_26_OREO)) {
            // Android 8.0+ 必须指定 channelId
            androidx.core.app.NotificationCompat.Builder(context, channelId)
        } else {
            // Android 6.0-7.1 不需要 channelId
            @Suppress("DEPRECATION")
            androidx.core.app.NotificationCompat.Builder(context)
        }
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
            "CODENAME=${Build.VERSION.CODENAME}"
        )
    }
    
    // ============ BroadcastReceiver 兼容 ============
    
    /**
     * 注册广播接收器（兼容不同 API 级别）
     * 
     * Android 13 (API 33) 引入了 RECEIVER_NOT_EXPORTED 标志
     * 
     * @param context Context 对象
     * @param receiver BroadcastReceiver 实例
     * @param filter IntentFilter
     * @param exported 是否导出（默认不导出）
     */
    fun registerReceiver(
        context: Context,
        receiver: android.content.BroadcastReceiver,
        filter: android.content.IntentFilter,
        exported: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= API_33_TIRAMISU) {
            val flags = if (exported) {
                Context.RECEIVER_EXPORTED
            } else {
                Context.RECEIVER_NOT_EXPORTED
            }
            context.registerReceiver(receiver, filter, flags)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }
    
    /**
     * 注册广播接收器（别名方法，保持兼容性）
     */
    fun registerReceiverCompat(
        context: Context,
        receiver: android.content.BroadcastReceiver,
        filter: android.content.IntentFilter,
        exported: Boolean = false
    ) = registerReceiver(context, receiver, filter, exported)
    
    // ============ Intent 兼容 ============
    
    /**
     * 从 Intent 中获取 Parcelable 对象（兼容不同 API 级别）
     * 
     * Android 13 (API 33) 引入了类型安全的 getParcelableExtra
     * 
     * @param intent Intent 对象
     * @param key 键名
     * @param clazz Parcelable 类型
     * @return Parcelable 对象，如果不存在则返回 null
     */
    fun <T : android.os.Parcelable> getParcelableExtraCompat(
        intent: Intent,
        key: String,
        clazz: Class<T>
    ): T? {
        return if (Build.VERSION.SDK_INT >= API_33_TIRAMISU) {
            intent.getParcelableExtra(key, clazz)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
    
}

    // ============ 全屏模式兼容 ============

    /**
     * 设置全屏模式（隐藏状态栏和导航栏）
     *
     * Android 11 (API 30) 引入了 WindowInsetsController
     * Android 6.0-10 (API 23-29) 使用 systemUiVisibility
     *
     * @param window 窗口对象
     * @param fullscreen 是否启用全屏模式
     */
    @Suppress("DEPRECATION")
    fun setFullScreen(window: Window?, fullscreen: Boolean) {
        window ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用 WindowInsetsController
            val controller = window.insetsController
            if (fullscreen) {
                // 隐藏状态栏和导航栏
                controller?.hide(
                    android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
                )
                // 设置沉浸式粘性模式（滑动时不会永久显示系统栏）
                controller?.systemBarsBehavior = 
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                // 显示状态栏和导航栏
                controller?.show(
                    android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
                )
            }
        } else {
            // Android 6.0-10 使用 systemUiVisibility
            if (fullscreen) {
                window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or              // 隐藏状态栏
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or           // 稳定布局
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or  // 布局延伸到导航栏
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or       // 布局延伸到状态栏
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or         // 隐藏导航栏
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY           // 沉浸式粘性模式
                )
            } else {
                window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }
}
