package com.mobile.scrcpy.android.core.domain.model

/**
 * 连接步骤
 */
enum class ConnectionStep {
    ADB_CONNECT, // ADB 连接
    ADB_FORWARD, // ADB 端口转发
    PUSH_SERVER, // 推送 scrcpy-server
    START_SERVER, // 启动 scrcpy-server
    CONNECT_SOCKET, // 连接 Socket
    COMPLETED, // 完成
}

/**
 * 步骤状态
 */
enum class StepStatus {
    PENDING, // 等待中
    RUNNING, // 执行中
    SUCCESS, // 成功
    FAILED, // 失败
}

/**
 * 连接进度信息
 */
data class ConnectionProgress(
    val step: ConnectionStep,
    val status: StepStatus,
    val message: String = "",
    val error: String? = null,
)

/**
 * 获取步骤的显示文本
 */
fun ConnectionStep.getDisplayText(): String =
    when (this) {
        ConnectionStep.ADB_CONNECT -> "ADB Connect"
        ConnectionStep.ADB_FORWARD -> "ADB Forward"
        ConnectionStep.PUSH_SERVER -> "Push Server"
        ConnectionStep.START_SERVER -> "Start Server"
        ConnectionStep.CONNECT_SOCKET -> "Connect Socket"
        ConnectionStep.COMPLETED -> "Completed"
    }

/**
 * 获取步骤的图标
 */
fun StepStatus.getIcon(): String =
    when (this) {
        StepStatus.PENDING -> "⏳"
        StepStatus.RUNNING -> "🔄"
        StepStatus.SUCCESS -> "✅"
        StepStatus.FAILED -> "❌"
    }
