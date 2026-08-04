package com.screen.remote.android.core.i18n

/**
 * 远程控制相关文本（RemoteDisplayScreen & ScrcpyClient）
 */
object RemoteTexts {
    val REMOTE_STATS_RENDER = TextPair("渲染", "Render")
    val REMOTE_STATS_DECODE = TextPair("解码", "Decode")
    val REMOTE_STATS_VIDEO = TextPair("视频", "Video")
    val REMOTE_STATS_WIFI_LINK = TextPair("Wi-Fi 链路", "Wi-Fi Link")
    val REMOTE_STATS_NETWORK_ACTUAL = TextPair("网络实际", "Network Actual")

    // 远程控制错误
    val ERROR_CONTROL_NOT_READY = TextPair("控制连接未就绪", "Control connection not ready")
    val ERROR_TEXT_TOO_LONG = TextPair("文本过长（最大 300 字节）", "Text too long (max 300 bytes)")

    // RemoteDisplayScreen
    val REMOTE_SWITCH_TO_BACKGROUND = TextPair("切换到后台", "Switch to background")
    val REMOTE_SCREEN_ROTATION_A = TextPair("A旋转", "A rotation")
    val REMOTE_LANDSCAPE = TextPair("横屏", "Landscape")
    val REMOTE_PORTRAIT = TextPair("竖屏", "Portrait")
    val REMOTE_ASPECT_RATIO = TextPair("宽高比", "Aspect ratio")
    val REMOTE_SCALE_STRATEGY = TextPair("缩放策略", "Scale strategy")
    val REMOTE_FILL_HEIGHT = TextPair("填满高度", "Fill height")
    val REMOTE_FILL_WIDTH = TextPair("填满宽度", "Fill width")
    val REMOTE_AUDIO_STREAM_EMPTY = TextPair("音频流为空，停止解码器", "Audio stream empty, stopping decoder")
    val REMOTE_AUDIO_STREAM_CHANGED =
        TextPair("音频流已变化，停止旧解码器", "Audio stream changed, stopping old decoder")
    val REMOTE_START_AUDIO_DECODER = TextPair("启动音频解码器", "Starting audio decoder")
    val REMOTE_AUDIO_CONNECTION_LOST =
        TextPair("音频连接丢失，触发完整清理", "Audio connection lost, triggering cleanup")
    val REMOTE_AUDIO_DECODER_CANCELLED = TextPair("音频解码器协程被取消", "Audio decoder coroutine cancelled")
    val REMOTE_AUDIO_DECODER_FAILED = TextPair("音频解码器失败", "Audio decoder failed")
    val REMOTE_INIT_AUDIO_DECODER_FAILED = TextPair("初始化音频解码器失败", "Failed to initialize audio decoder")
    val REMOTE_VIDEO_STREAM_CHANGED = TextPair("视频流已变化，重启解码器", "Video stream changed, restarting decoder")
    val REMOTE_PREPARE_VIDEO_DECODER = TextPair("准备启动视频解码器", "Preparing to start video decoder")
    val REMOTE_CANNOT_GET_VIDEO_RESOLUTION = TextPair("无法获取视频分辨率", "Cannot get video resolution")
    val REMOTE_VIDEO_RESOLUTION = TextPair("视频分辨率", "Video resolution")
    val REMOTE_RECEIVED_VIDEO_SIZE = TextPair("收到视频尺寸", "Received video size")
    val REMOTE_INVALID_VIDEO_SIZE = TextPair("无效的视频尺寸", "Invalid video size")
    val REMOTE_CONNECTION_LOST_CLEANUP = TextPair("连接丢失，触发完整清理", "Connection lost, triggering cleanup")
    val REMOTE_DECODER_CANCELLED_UI_CLOSED = TextPair("解码器已取消（界面关闭）", "Decoder cancelled (UI closed)")
    val REMOTE_DECODER_START_FAILED = TextPair("解码器启动失败", "Decoder start failed")
    val REMOTE_INIT_DECODER_FAILED = TextPair("初始化解码器失败", "Failed to initialize decoder")
    val REMOTE_DECODER_CONTINUE_RUNNING =
        TextPair("解码器继续运行，socket 保持活跃", "Decoder continues running, socket stays active")
    val REMOTE_RESUME_TO_FOREGROUND = TextPair("恢复到前台", "Resume to foreground")
    val REMOTE_FOREGROUND_RESUME_INVALID_SURFACE =
        TextPair("前台恢复但 Surface 无效", "Foreground resumed but Surface invalid")
    val REMOTE_START_CLEANUP_RESOURCES = TextPair("开始清理资源...", "Starting resource cleanup...")
    val REMOTE_CLEANUP_COMPLETE = TextPair("资源清理完成", "Resource cleanup complete")
    val REMOTE_CLEANUP_EXCEPTION = TextPair("资源清理异常", "Resource cleanup exception")
    val REMOTE_SURFACE_READY = TextPair("Surface 已就绪", "Surface ready")
    val REMOTE_SURFACE_DESTROYED = TextPair("Surface 已销毁", "Surface destroyed")
    val REMOTE_SURFACE_RESTORED =
        TextPair("Surface 已恢复，设置为就绪并恢复渲染", "Surface restored, set to ready and resume rendering")
    val REMOTE_SURFACE_UNAVAILABLE = TextPair("Surface 不可用", "Surface unavailable")
    val REMOTE_FOCUS_REQUEST_FAILED = TextPair("请求焦点失败", "Focus request failed")
    val REMOTE_ADAPT_DEVICE_RESOLUTION = TextPair("适配设备分辨率", "Adapt device resolution")
    val REMOTE_RESTORE_DEVICE_RESOLUTION = TextPair("恢复设备分辨率", "Restore device resolution")
    val REMOTE_DEVICE_RESOLUTION_ADAPTED = TextPair("已适配设备分辨率", "Device resolution adapted")
    val REMOTE_DEVICE_RESOLUTION_RESTORED = TextPair("已恢复设备分辨率", "Device resolution restored")
    val REMOTE_DEVICE_RESOLUTION_CHANGE_FAILED = TextPair("修改设备分辨率失败", "Failed to change device resolution")
    val REMOTE_FILE_UPLOADED = TextPair("已上传到 %s", "Uploaded to %s")
    val REMOTE_APK_INSTALLED = TextPair("APK 安装成功：%s", "APK installed: %s")
    val REMOTE_FILE_SEND_FAILED = TextPair("文件发送失败", "Failed to send file")
    val REMOTE_LAYOUT_RENDER_EMPTY = TextPair("当前页面没有可渲染的布局节点", "No renderable layout nodes found")
    val REMOTE_LAYOUT_RENDER_FAILED = TextPair("布局抓取失败", "Layout capture failed")
    val REMOTE_TARGET_KEYBOARD_OPEN =
        TextPair(
            "目标设备键盘已打开，可能影响底部按钮点击",
            "The target device keyboard is open and may interfere with the bottom buttons",
        )
    val REMOTE_DECODER_SIZE_UNSUPPORTED_TITLE =
        TextPair("当前解码器不支持此分辨率", "Decoder does not support this resolution")
    val REMOTE_DECODER_SIZE_UNSUPPORTED_MESSAGE =
        TextPair(
            "固定解码器 %s 无法解码 %d×%d。是否使用 maxSize=%d 临时重新连接？此操作不会修改会话配置。",
            "The fixed decoder %s cannot decode %d×%d. Reconnect temporarily with maxSize=%d? This will not change the session configuration.",
        )
    val REMOTE_DECODER_SIZE_RECOVERY_CONFIRM =
        TextPair("降低尺寸", "Reduce Size")
    val REMOTE_DECODER_SIZE_RECOVERY_CANCEL =
        TextPair("取消连接", "Cancel")
    val REMOTE_CAPTURE_SIZE_UNSUPPORTED_TITLE =
        TextPair("原画采集失败", "Native-resolution capture failed")
    val REMOTE_CAPTURE_SIZE_UNSUPPORTED_MESSAGE =
        TextPair(
            "目标设备无法以当前尺寸启动视频采集或编码。是否使用 maxSize=%d 临时重新连接？此操作不会修改会话配置。",
            "The target device could not start video capture or encoding at the current size. Reconnect temporarily with maxSize=%d? This will not change the session configuration.",
        )
    val REMOTE_VIDEO_SIZE_RECOVERY_EXHAUSTED =
        TextPair(
            "目标设备在最低恢复尺寸 maxSize=540 下仍无法完成视频解码。请更换视频编码器或解码器，或在会话设置中启用兼容模式。",
            "Video decoding still failed at the minimum recovery size maxSize=540. Choose another video encoder or decoder, or enable compatibility mode in the session settings.",
        )
    val REMOTE_DEFAULT_VIDEO_ENCODER = TextPair("默认视频编码器", "default video encoder")
    val REMOTE_VIDEO_ENCODER_RUNTIME_FAILED =
        TextPair(
            "视频编码器 %s 在目标设备上运行失败。请在会话设置中更换视频编码器；如果其他编码器仍不可用，请启用兼容模式。",
            "Video encoder %s failed on the target device. Choose another video encoder in the session settings; if no encoder works, enable compatibility mode.",
        )
    val REMOTE_SESSION_CONNECTION_FAILED_TITLE = TextPair("%s 连接失败", "%s connection failed")

    val SCRCPY_AUDIO_METADATA_READ = TextPair("音频元数据读取完成", "Audio metadata read complete")
    val SCRCPY_CLEANED_OLD_SERVER_PROCESS =
        TextPair("已清理旧的 scrcpy-server 进程", "Cleaned old scrcpy-server process")
    val SCRCPY_CLEANUP_OLD_RESOURCES_FAILED = TextPair("清理旧资源失败", "Failed to cleanup old resources")
    val SCRCPY_VIDEO_SOCKET_CONNECTED = TextPair("视频 Socket 已连接", "Video socket connected")
    val SCRCPY_VIDEO_SOCKET_NOT_CONNECTED = TextPair("视频 Socket 未连接", "Video socket not connected")
    val SCRCPY_SOCKET_CONNECTION_FAILED = TextPair("Socket 连接失败", "Socket connection failed")
    val SCRCPY_VIDEO_RESOLUTION = TextPair("视频分辨率", "Video resolution")

    // Server 推送和启动
    val REMOTE_PUSHING_SERVER = TextPair("推送 Server...", "Pushing Server...")
    val REMOTE_SERVER_PUSHED = TextPair("Server 推送成功", "Server pushed successfully")
    val REMOTE_PUSH_FAILED = TextPair("Server 推送失败", "Server push failed")
    val REMOTE_STARTING_SERVER = TextPair("启动 Server...", "Starting Server...")
    val REMOTE_SERVER_STARTED = TextPair("Server 启动成功", "Server started successfully")
    val REMOTE_START_FAILED = TextPair("Server 启动失败", "Server start failed")

    // Forward 端口转发
    val REMOTE_SETTING_FORWARD = TextPair("设置端口转发...", "Setting up port forwarding...")
    val REMOTE_FORWARD_SETUP = TextPair("端口转发成功", "Port forwarding successful")
    val REMOTE_FORWARD_FAILED = TextPair("端口转发失败", "Port forwarding failed")

    // Socket 连接
    val REMOTE_CONNECTING_SOCKET = TextPair("连接 Socket...", "Connecting Socket...")
    val REMOTE_SOCKET_CONNECTED = TextPair("Socket 连接成功", "Socket connected successfully")
    val REMOTE_SOCKET_ERROR = TextPair("Socket 错误", "Socket error")
    val SCRCPY_METADATA_READ_FAILED = TextPair("元数据读取失败", "Metadata read failed")
    val SCRCPY_SCREEN_WAKE_SIGNAL_SENT =
        TextPair("屏幕唤醒信号已发送（已触发关键帧）", "Screen wake signal sent (key frame triggered)")
    val SCRCPY_WAKE_SCREEN_FAILED = TextPair("唤醒屏幕失败", "Failed to wake screen")
    val SCRCPY_REMOVED_ADB_FORWARD = TextPair("已移除 ADB forward", "Removed ADB forward")
    val SCRCPY_REMOVE_FORWARD_FAILED = TextPair("移除 forward 失败", "Failed to remove forward")
    val SCRCPY_TERMINATED_SERVER_PROCESS = TextPair("已终止 scrcpy-server 进程", "Terminated scrcpy-server process")
    val SCRCPY_TERMINATE_SERVER_FAILED =
        TextPair("终止 scrcpy-server 进程失败", "Failed to terminate scrcpy-server process")
}
