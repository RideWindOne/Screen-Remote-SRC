package com.screen.remote.android.core.i18n

/**
 * ADB 相关文本
 */
object AdbTexts {
    // ========== ADB 密钥管理 ==========
    val ADB_KEY_MANAGEMENT_TITLE = TextPair("ADB 密钥管理", "ADB Key Management")
    val ADB_KEY_DIR_LABEL = TextPair("密钥目录", "Key Directory")
    val ADB_PRIVATE_KEY_LABEL = TextPair("私钥", "Private Key")
    val ADB_PUBLIC_KEY_LABEL = TextPair("公钥", "Public Key")
    val ADB_KEY_NOT_FOUND = TextPair("未找到密钥", "Keys not found")

    // 密钥操作结果
    val ADB_KEY_SAVE_SUCCESS = TextPair("密钥保存成功", "Keys saved successfully")
    val ADB_KEY_SAVE_FAILED = TextPair("密钥保存失败", "Failed to save keys")
    val ADB_KEY_IMPORT_SUCCESS = TextPair("密钥导入成功", "Keys imported successfully")
    val ADB_KEY_IMPORT_FAILED = TextPair("密钥导入失败", "Failed to import keys")
    val ADB_KEY_EXPORT_SUCCESS = TextPair("密钥导出成功", "Keys exported successfully")
    val ADB_KEY_EXPORT_FAILED = TextPair("密钥导出失败", "Failed to export keys")
    val ADB_KEY_GENERATE_SUCCESS = TextPair("密钥生成成功", "Keys generated successfully")
    val ADB_KEY_GENERATE_FAILED = TextPair("密钥生成失败", "Failed to generate keys")

    // 密钥操作按钮
    val BUTTON_GENERATE_KEYS = TextPair("生成密钥对", "Generate Key Pair")
    val BUTTON_IMPORT_KEYS = TextPair("导入密钥", "Import Keys")
    val BUTTON_EXPORT_KEYS = TextPair("导出密钥", "Export Keys")
    val BUTTON_SAVE_KEYS = TextPair("保存密钥", "Save Keys")

    // 密钥管理标签
    val LABEL_KEY_INFO = TextPair("密钥信息", "Key Information")
    val LABEL_KEY_OPERATIONS = TextPair("密钥操作", "Key Operations")

    // ADB 连接错误
    val ERROR_ADB_CONNECTION_DISCONNECTED =
        TextPair("ADB 连接已断开 (ECONNREFUSED)", "ADB connection disconnected (ECONNREFUSED)")
    val ERROR_ADB_HANDSHAKE_FAILED =
        TextPair("ADB 握手失败，设备可能未授权或 ADB 服务异常", "ADB handshake failed, device may be unauthorized or ADB service error")
    val ERROR_ADB_CONNECTION_UNAVAILABLE = TextPair("ADB 连接不可用", "ADB connection unavailable")
    val ERROR_ADB_COMMAND_FAILED = TextPair("ADB 命令执行失败", "ADB command execution failed")
    val ERROR_ADB_RECONNECT_FAILED = TextPair("ADB 重连失败", "ADB reconnection failed")
    val ERROR_INVALID_DEVICE_ID = TextPair("无效的设备 ID", "Invalid device ID")
    val ERROR_CANNOT_GET_ADB_CONNECTION = TextPair("无法获取 ADB 连接", "Cannot get ADB connection")
    val ERROR_DEVICE_NOT_CONNECTED = TextPair("未连接设备", "Device not connected")
    val ERROR_DEVICE_CONNECTION_LOST = TextPair("设备连接已断开", "Device connection lost")

    // ADB 连接管理器日志
    val ADB_HEARTBEAT_FAILED = TextPair("心跳检测失败", "Heartbeat check failed")
    val ADB_CONNECTION_DETECTED_DISCONNECTED = TextPair("检测到 ADB 连接断开", "ADB connection disconnected detected")
    val ADB_CLEANUP_INVALID_CONNECTION = TextPair("清理失效连接", "Cleaning up invalid connection")
    val ADB_KEEPALIVE_STARTED = TextPair("连接保活任务已启动", "Connection keep-alive task started")
    val ADB_GENERATE_NEW_KEYPAIR = TextPair("生成新的 ADB 密钥对", "Generating new ADB key pair")
    val ADB_KEYPAIR_LOADED = TextPair("ADB 密钥对加载成功", "ADB key pair loaded successfully")
    val ADB_KEYPAIR_INIT_FAILED = TextPair("初始化密钥对失败", "Failed to initialize key pair")
    val ADB_START_CONNECTING = TextPair("开始连接设备", "Start connecting device")
    val ADB_TARGET_ADDRESS = TextPair("目标地址", "Target address")
    val ADB_KEYPAIR_NOT_INITIALIZED = TextPair("ADB 密钥对未初始化", "ADB key pair not initialized")
    val ADB_FORCE_RECONNECT_CLEANUP = TextPair("强制重连：清理旧连接", "Force reconnect: cleaning up old connection")
    val ADB_VERIFYING_CONNECTION = TextPair("验证连接", "Verifying connection")
    val ADB_CONNECTION_VERIFIED = TextPair("连接验证成功", "Connection verified")
    val ADB_CONNECTION_VERIFY_FAILED = TextPair("连接验证失败", "Connection verification failed")
    val ADB_CREATING_NEW_CONNECTION = TextPair("创建新连接", "Creating new connection")
    val ADB_CONNECTION_REFUSED = TextPair("连接被拒绝", "Connection refused")
    val ADB_CONNECTION_REFUSED_DETAILS =
        TextPair(
            "连接被拒绝，请检查：\n1. 设备是否开启无线调试\n2. IP 地址和端口是否正确\n3. 设备是否在同一网络",
            "Connection refused. Please check:\n1. Wireless debugging is enabled\n2. IP address and port are correct\n3. Device is on the same network",
        )
    val ADB_DADB_CREATED = TextPair("DADB 连接已创建", "DADB connection created")
    val ADB_ADDED_TO_POOL = TextPair("添加到连接池", "Add to connection pool")
    val ADB_GET_DEVICE_INFO_FAILED = TextPair("获取设备信息失败", "Failed to get device info")
    val ADB_CONNECTION_SUCCESS = TextPair("连接成功", "Connection successful")
    val ADB_CONNECTION_FAILED_TITLE = TextPair("连接失败", "Connection failed")
    val ADB_DEVICE_NOT_CONNECTED = TextPair("设备未连接", "Device not connected")
    val ADB_DISCONNECT_FAILED = TextPair("断开连接失败", "Failed to disconnect")
    val ADB_DISCONNECT_ALL = TextPair("断开所有设备连接", "Disconnecting all devices")
    val ADB_CLOSE_CONNECTION_FAILED = TextPair("关闭连接失败", "Failed to close connection")
    val ADB_CONNECTING = TextPair("ADB 正在连接...", "ADB Connecting...")
    val ADB_CONNECTED = TextPair("ADB 连接成功", "ADB Connected")
    val ADB_DISCONNECTED = TextPair("ADB 已断开", "ADB Disconnected")
    val ADB_VERIFYING = TextPair("验证 ADB 连接...", "Verifying ADB connection...")
    val ADB_VERIFY_FAILED = TextPair("ADB 连接验证失败", "ADB connection verification failed")
    val ADB_VERIFY_TIMEOUT =
        TextPair("验证超时，请检查设备是否已授权 USB 调试", "Verification timeout, please check if USB debugging is authorized")
    val ADB_CLOSE_DADB_ERROR = TextPair("关闭 dadb 时出错", "Error closing dadb")
    val ADB_DISCONNECTED_ECONNREFUSED =
        TextPair("ADB 连接已断开 (ECONNREFUSED)", "ADB connection disconnected (ECONNREFUSED)")
    val ADB_RECONNECT_DEVICE = TextPair("ADB 连接已断开，请重新连接设备", "ADB connection disconnected, please reconnect device")
    val ADB_HANDSHAKE_FAILED_OR_INTERRUPTED =
        TextPair("ADB 握手失败或连接中断", "ADB handshake failed or connection interrupted")
    val ADB_COMMUNICATION_FAILED = TextPair("ADB 通信失败，连接不可用", "ADB communication failed, connection unavailable")
    val ADB_GET_DEVICE_INFO_FAILED_DETAIL = TextPair("获取设备信息失败", "Failed to get device info")
    val ADB_CANNOT_GET_DEVICE_INFO = TextPair("无法获取设备信息", "Cannot get device info")
    val ADB_DEVICE_DISCONNECTED = TextPair("设备已断开", "Device disconnected")
    val ADB_GET_PUBLIC_KEY_FAILED = TextPair("获取公钥失败", "Failed to get public key")
    val ADB_CANNOT_EXECUTE_COMMAND = TextPair("无法执行命令", "Cannot execute command")
    val ADB_AUTO_RECONNECT_RETRY =
        TextPair("ADB 连接已关闭，尝试自动重连后重试", "ADB connection closed, retrying after auto-reconnect")
    val ADB_AUTO_RECONNECT_SUCCESS = TextPair("自动重连成功，命令执行成功", "Auto-reconnect successful, command executed")
    val ADB_AUTO_RECONNECT_STILL_FAILED = TextPair("自动重连后仍失败", "Still failed after auto-reconnect")
    val ADB_SOCKET_EXCEPTION_RETRY =
        TextPair("ADB Socket 异常，尝试自动重连后重试", "ADB Socket exception, retrying after auto-reconnect")
    val ADB_SOCKET_EXCEPTION = TextPair("ADB Socket 异常，无法执行命令", "ADB Socket exception, cannot execute command")
    val ADB_EXECUTE_COMMAND_FAILED = TextPair("执行命令失败", "Failed to execute command")
    val ADB_ASYNC_EXECUTE_FAILED = TextPair("异步执行命令失败", "Failed to execute command asynchronously")
    val ADB_OPEN_SHELL_STREAM_FAILED = TextPair("打开 Shell 流失败", "Failed to open shell stream")
    val ADB_PORT_FORWARD_SUCCESS = TextPair("端口转发设置成功", "Port forwarding set up successfully")
    val ADB_PORT_FORWARD_FAILED = TextPair("端口转发失败", "Port forwarding failed")
    val ADB_FORWARD_SETUP_SUCCESS = TextPair("ADB forward 设置成功", "ADB forward set up successfully")
    val ADB_SOCKET_FORWARDER_FAILED = TextPair("SocketForwarder 失败", "SocketForwarder failed")
    val ADB_FORWARD_SETUP_EXCEPTION = TextPair("设置 ADB forward 异常", "Exception setting up ADB forward")
    val ADB_FORWARD_REMOVED = TextPair("ADB forward 已移除", "ADB forward removed")
    val ADB_FORWARD_REMOVE_EXCEPTION = TextPair("移除 ADB forward 异常", "Exception removing ADB forward")
    val ADB_FILE_PUSH_SUCCESS = TextPair("文件推送成功", "File pushed successfully")
    val ADB_FILE_PUSH_FAILED = TextPair("文件推送失败", "Failed to push file")
    val ADB_FILE_PULL_SUCCESS = TextPair("文件拉取成功", "File pulled successfully")
    val ADB_FILE_PULL_FAILED = TextPair("文件拉取失败", "Failed to pull file")
    val ADB_APK_INSTALL_SUCCESS = TextPair("APK 安装成功", "APK installed successfully")
    val ADB_APK_INSTALL_FAILED = TextPair("APK 安装失败", "Failed to install APK")
    val ADB_APP_UNINSTALL_SUCCESS = TextPair("应用卸载成功", "App uninstalled successfully")
    val ADB_APP_UNINSTALL_FAILED = TextPair("应用卸载失败", "Failed to uninstall app")
    val ADB_SCRCPY_SERVER_NOT_IN_ASSETS =
        TextPair("scrcpy-server.jar 不存在于 assets 目录", "scrcpy-server.jar not found in assets directory")
    val ADB_PUSH_SCRCPY_SERVER_FAILED = TextPair("推送 scrcpy-server.jar 失败", "Failed to push scrcpy-server.jar")
    val ADB_DETECTING_VIDEO_ENCODERS = TextPair("开始检测视频编码器...", "Starting video encoder detection...")
    val ADB_PUSH_SERVER_FAILED_CANNOT_DETECT =
        TextPair("推送 scrcpy-server.jar 失败，无法检测编码器", "Failed to push scrcpy-server.jar, cannot detect encoders")
    val ADB_PUSH_FAILED = TextPair("推送失败", "Push failed")
    val ADB_CANNOT_OPEN_SHELL_STREAM = TextPair("无法打开 shell 流", "Cannot open shell stream")
    val ADB_SHELL_STREAM_EXIT = TextPair("Shell 流退出", "Shell stream exited")
    val ADB_READ_OUTPUT_ERROR = TextPair("读取输出时出错", "Error reading output")
    val ADB_DETECTED_VIDEO_ENCODERS = TextPair("检测到视频编码器", "Detected video encoders")
    val ADB_NO_ENCODERS_DETECTED_OUTPUT = TextPair("未检测到编码器，输出内容", "No encoders detected, output content")
    val ADB_DETECT_ENCODERS_FAILED = TextPair("检测编码器失败", "Failed to detect encoders")
    val ADB_DETECTING_AUDIO_ENCODERS = TextPair("开始检测音频编码器...", "Starting audio encoder detection...")
    val ADB_DETECTED_AUDIO_ENCODERS = TextPair("检测到音频编码器", "Detected audio encoders")
    val ADB_NO_AUDIO_ENCODERS_DETECTED_OUTPUT = TextPair("未检测到音频编码器，输出内容", "No audio encoders detected, output content")
    val ADB_DETECT_AUDIO_ENCODERS_FAILED = TextPair("检测音频编码器失败", "Failed to detect audio encoders")
    val ADB_CONNECTION_CLOSED = TextPair("连接已关闭", "Connection closed")
    val ADB_CLOSE_CONNECTION_FAILED_DETAIL = TextPair("关闭连接失败", "Failed to close connection")
    val ADB_DEVICE_ALREADY_CONNECTED = TextPair("设备已连接", "Device already connected")

    // 生成密钥确认对话框
    val ADB_KEY_GENERATE_CONFIRM_TITLE = TextPair("确认生成新密钥对", "Confirm Generate New Key Pair")
    val ADB_KEY_DESTRUCTIVE_OP = TextPair("⚠️ 这是一个破坏性操作", "⚠️ This is a destructive operation")
    val ADB_KEY_CURRENT_KEYS_DELETED = TextPair("当前密钥将被永久删除", "Current keys will be permanently deleted")
    val ADB_KEY_DEVICES_LOSE_AUTH = TextPair("所有已授权设备将失去信任关系", "All authorized devices will lose trust relationship")
    val ADB_KEY_NEED_REAUTH = TextPair("需要在每台设备上重新授权", "Need to re-authorize on each device")
    val ADB_KEY_CANNOT_UNDO = TextPair("此操作无法撤销", "This operation cannot be undone")
    val ADB_KEY_CONFIRM_GENERATE = TextPair("确定要生成新密钥对吗？", "Are you sure you want to generate new key pair?")

    // 导入密钥提示
    val ADB_KEY_IMPORT_HINT = TextPair("📋 导入提示", "📋 Import Tips")
    val ADB_KEY_IMPORT_HINT_MULTISELECT =
        TextPair(
            "在文件选择器中，长按第一个文件，然后点击第二个文件即可多选",
            "In the file picker, long press the first file, then tap the second file to select multiple files",
        )
    val ADB_KEY_IMPORT_HINT_BOTH_FILES =
        TextPair(
            "需要同时选择 adbkey 和 adbkey.pub 两个文件",
            "You need to select both adbkey and adbkey.pub files",
        )

    // 密钥导入错误
    val ERROR_SELECT_EXACTLY_2_FILES =
        TextPair("请选择 2 个文件 (adbkey 和 adbkey.pub)", "Please select exactly 2 files (adbkey and adbkey.pub)")
    val ERROR_IDENTIFY_KEY_FILES = TextPair("无法识别私钥和公钥文件", "Could not identify private key and public key files")

    // ========== 设备配对 ==========
    val PAIRING_TITLE = TextPair("配对 ADB 设备", "Pair ADB Device")
    val PAIRING_METHOD_TITLE = TextPair("配对方式", "Pairing Method")
    val PAIRING_METHOD_LABEL = TextPair("方式", "Method")
    val PAIRING_TAB_QR_CODE = TextPair("扫码配对", "QR Code")
    val PAIRING_TAB_PAIRING_CODE = TextPair("配对码", "Pairing Code")

    // 配对说明
    val PAIRING_INSTRUCTION_TITLE = TextPair("说明", "Instructions")
    val PAIRING_INSTRUCTION_CONTENT =
        TextPair(
            "使用无线 ADB 调试为 Android 设备配对：\n\n1. 打开 Android 设置\n2. 进入 开发者选项\n3. 启用 无线调试\n4. 点击 \"使用配对码配对设备\"\n5. 在下方输入 主机:端口 和 配对码",
            "Use Wireless ADB Debugging to pair Android devices:\n\n1. Open Android Settings\n2. Enter Developer Options\n3. Enable Wireless Debugging\n4. Tap \"Pair device with pairing code\"\n5. Enter Host:Port and Pairing Code below",
        )

    // 配对历史
    val PAIRING_HISTORY_TITLE = TextPair("最近配对记录", "Recent Pairing History")
    val PAIRING_HISTORY_EMPTY = TextPair("暂无配对记录", "No pairing history")
    val PAIRING_HISTORY_CLEAR = TextPair("清除历史", "Clear History")
    val PAIRING_HISTORY_CLEAR_CONFIRM_TITLE = TextPair("清除配对历史", "Clear Pairing History")
    val PAIRING_HISTORY_CLEAR_CONFIRM_MESSAGE =
        TextPair(
            "这将永久删除所有配对历史。此操作不可撤销。",
            "This will permanently delete all pairing history. This operation cannot be undone.",
        )
    val PAIRING_HISTORY_CLEAR_BUTTON = TextPair("清除", "Clear")
    val PAIRING_HISTORY_CLEARED = TextPair("配对历史已清除", "Pairing history cleared")

    // mDNS 服务发现
    val PAIRING_DISCOVERY_TITLE = TextPair("附近无线调试设备", "Nearby Wireless Debugging Devices")
    val PAIRING_DISCOVERY_SCANNING = TextPair("正在扫描无线调试设备...", "Scanning for Wireless Debugging devices...")
    val PAIRING_DISCOVERY_EMPTY = TextPair("暂无发现，仍可手动输入", "No devices found yet. You can still enter details manually.")
    val PAIRING_DISCOVERY_PAIRABLE = TextPair("可配对", "Available to pair")
    val PAIRING_DISCOVERY_RECORDED = TextPair("曾配对", "Previously paired")
    val PAIRING_DISCOVERY_DISCOVERED = TextPair("附近可见", "Nearby")
    val PAIRING_DISCOVERY_CONFIRMING = TextPair("正在确认", "Confirming")

    // 配对信息标签
    val PAIRING_INFO_TITLE = TextPair("配对信息", "Pairing Information")
    val PAIRING_HOST_PORT_LABEL = TextPair("IP地址和端口", "IP address & Port")
    val PAIRING_CODE_LABEL = TextPair("WLAN配对码", "Wi-Fi Pairing Code")

    // 二维码配对
    val QR_CODE_TITLE = TextPair("配对二维码", "Pairing QR Code")
    val QR_CODE_DESCRIPTION =
        TextPair(
            "在被控设备上打开「开发者选项 → 无线调试 → 使用二维码配对设备」，然后扫描下方二维码。Screen Remote 发现该设备后会自动完成配对。",
            "On the target device, open Developer options → Wireless debugging → Pair device with QR code, then scan the code below. Screen Remote pairs automatically after discovering the device.",
        )
    val QR_CODE_CONTENT_DESCRIPTION = TextPair("ADB 无线调试配对二维码", "ADB Wireless Debugging pairing QR code")
    val QR_CODE_WAITING_SCAN =
        TextPair("等待被控设备扫描二维码…", "Waiting for the target device to scan…")
    val QR_CODE_PAIRING = TextPair("已发现设备，正在配对…", "Device found. Pairing…")
    val QR_CODE_PAIRING_SUCCESS = TextPair("扫码配对成功", "QR pairing successful")
    val QR_CODE_PAIRING_RETRY =
        TextPair("配对未完成，请生成新二维码后重试", "Pairing did not complete. Generate a new QR code and try again.")
    val QR_CODE_REGENERATE = TextPair("重新生成", "Regenerate")

    // 配对码配对
    val PAIRING_CODE_TITLE = TextPair("使用配对码配对", "Pair with Pairing Code")
    val PAIRING_CODE_DESCRIPTION =
        TextPair(
            "1. 在被控设备上打开「开发者选项」\n2. 启用「无线调试」\n3. 点击「使用配对码配对设备」\n4. 输入显示的配对信息",
            "1. Open 'Developer options' on the target device\n2. Enable 'Wireless debugging'\n3. Tap 'Pair device with pairing code'\n4. Enter the displayed pairing information",
        )
    val PAIRING_CODE_IP = TextPair("IP 地址", "IP Address")
    val PAIRING_CODE_PORT = TextPair("端口", "Port")
    val PAIRING_CODE_CODE = TextPair("配对码", "Pairing Code")
    val PAIRING_CODE_IP_HINT = TextPair("例如：192.168.1.100", "e.g., 192.168.1.100")
    val PAIRING_CODE_PORT_HINT = TextPair("例如：37829", "e.g., 37829")
    val PAIRING_CODE_CODE_HINT = TextPair("6位数字", "6-digit code")

    // 配对状态
    val PAIRING_STATUS_CONNECTING = TextPair("正在连接...", "Connecting...")
    val PAIRING_STATUS_PAIRING = TextPair("正在配对...", "Pairing...")
    val PAIRING_STATUS_SUCCESS = TextPair("配对成功", "Pairing Successful")
    val PAIRING_STATUS_FAILED = TextPair("配对失败", "Pairing Failed")

    // 配对结果
    val PAIRING_SUCCESS_MESSAGE =
        TextPair(
            "设备配对成功！现在可以在主页面添加会话连接到此设备。",
            "Device paired successfully! You can now add a session on the main page to connect to this device.",
        )
    val PAIRING_FAILED_MESSAGE =
        TextPair(
            "配对失败，请检查：\n1. 设备是否在同一网络\n2. 配对信息是否正确\n3. 无线调试是否已启用",
            "Pairing failed. Please check:\n1. Devices are on the same network\n2. Pairing information is correct\n3. Wireless debugging is enabled",
        )

    // 按钮
    val BUTTON_PAIR = TextPair("配对", "Pair")
    val BUTTON_RETRY = TextPair("重试", "Retry")
    val BUTTON_CLOSE = TextPair("关闭", "Close")

    // 错误提示
    val ERROR_INVALID_IP = TextPair("无效的 IP 地址", "Invalid IP address")
    val ERROR_INVALID_PORT = TextPair("无效的端口号", "Invalid port number")
    val ERROR_INVALID_CODE = TextPair("配对码必须是6位数字", "Pairing code must be 6 digits")
    val ERROR_EMPTY_FIELD = TextPair("请填写所有字段", "Please fill in all fields")
    val ERROR_QR_CODE_INVALID = TextPair("无效的二维码", "Invalid QR code")
    val ERROR_QR_CODE_PARSE_FAILED = TextPair("二维码解析失败", "Failed to parse QR code")
    val ERROR_QR_CODE_GENERATE_FAILED = TextPair("二维码生成失败", "Failed to generate QR code")

    // ========== USB 设备管理 ==========
    val USB_SCANNING_DEVICES = TextPair("正在扫描 USB 设备...", "Scanning USB devices...")
    val USB_FOUND_DEVICES = TextPair("找到设备", "Found devices")
    val USB_DEVICE_FOUND = TextPair("发现设备", "Device found")
    val USB_SCAN_BUTTON = TextPair("扫描 USB 设备", "Scan USB Devices")
    val USB_SCAN_FAILED = TextPair("扫描失败", "Scan failed")

    // USB 权限
    val USB_PERMISSION = TextPair("权限", "Permission")
    val USB_PERMISSION_GRANTED = TextPair("USB 权限已授予", "USB permission granted")
    val USB_PERMISSION_DENIED = TextPair("USB 权限被拒绝", "USB permission denied")
    val USB_PERMISSION_ALREADY_GRANTED = TextPair("USB 权限已授予", "USB permission already granted")
    val USB_REQUESTING_PERMISSION = TextPair("正在请求 USB 权限...", "Requesting USB permission...")
    val USB_PERMISSION_REQUEST_FAILED = TextPair("USB 权限请求失败", "USB permission request failed")
    val USB_PERMISSION_GRANTED_STATUS = TextPair("已授权", "Granted")
    val USB_PERMISSION_NOT_GRANTED_STATUS = TextPair("未授权", "Not Granted")
    val USB_CLICK_TO_REQUEST_PERMISSION = TextPair("点击请求权限", "Click to request permission")
    val USB_DEVICE_QUERY = TextPair("设备查询", "Device query")
    val USB_NO_DEVICES_FOUND = TextPair("未找到 USB 设备", "No USB devices found")
    val USB_CONNECT_BUTTON = TextPair("连接", "Connect")
    val USB_DEVICE_LIST_TITLE = TextPair("USB 设备列表", "USB Device List")
    val ERROR_USB_CONNECTION_LOST = TextPair("USB 连接已断开，请重新连接设备", "USB connection lost, please reconnect the device")
    val CONNECTION_TYPE = TextPair("连接类型", "Connection Type")
    val CONNECTION_TYPE_TCP = TextPair("TCP/IP", "TCP/IP")
    val CONNECTION_TYPE_USB = TextPair("USB", "USB")
    val USB_SELECT_DEVICE = TextPair("选择设备", "Select Device")
    val USB_DEVICE_SELECTED = TextPair("已选择设备", "Device Selected")
    val USB_NO_DEVICE_SELECTED = TextPair("未选择设备", "No Device Selected")

    // USB 连接
    val USB_CONNECTING_DEVICE = TextPair("正在连接 USB 设备", "Connecting USB device")
    val USB_SERIAL_NUMBER = TextPair("序列号", "Serial Number")
    val USB_CONNECT_FAILED = TextPair("USB 连接失败", "USB connection failed")

    // ========== ADB 连接管理 ==========
    val ADB_MANAGER_INIT = TextPair("ADB 连接管理器初始化", "ADB Connection Manager initialized")

    // Scrcpy 连接进度
    val PROGRESS_VERIFYING_ADB = TextPair("验证 ADB 连接", "Verifying ADB connection")
    val PROGRESS_ADB_RECONNECTING = TextPair("ADB 连接已断开，重新连接...", "ADB disconnected, reconnecting...")
    val PROGRESS_ADB_NORMAL = TextPair("ADB 连接正常", "ADB connection normal")
    val PROGRESS_PORT_FORWARD = TextPair("端口转发已建立", "Port forwarding established")
    val PROGRESS_SERVER_PUSHED = TextPair("服务端已推送", "Server pushed")
    val PROGRESS_SERVER_STARTED = TextPair("服务端已启动", "Server started")
    val PROGRESS_SOCKET_CONNECTED = TextPair("Socket 连接成功", "Socket connected")
    val PROGRESS_CONNECTION_ESTABLISHED = TextPair("连接已建立", "Connection established")
    val PROGRESS_PUSHING_SERVER = TextPair("推送 scrcpy-server", "Pushing scrcpy-server")
    val PROGRESS_STARTING_SERVER = TextPair("启动 scrcpy-server", "Starting scrcpy-server")
    val PROGRESS_CONNECTING_STREAM = TextPair("连接视频流", "Connecting video stream")
}
