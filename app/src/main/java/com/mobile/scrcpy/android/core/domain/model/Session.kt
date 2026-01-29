package com.mobile.scrcpy.android.core.domain.model

/**
 * Scrcpy 会话
 */
data class ScrcpySession(
    val id: String,
    val name: String,
    val color: SessionColor,
    val deviceId: String? = null,
    val isConnected: Boolean = false,
    val hasWifi: Boolean = false,
    val hasWarning: Boolean = false,
)

/**
 * 会话颜色
 */
enum class SessionColor {
    BLUE,
    RED,
    GREEN,
    ORANGE,
    PURPLE,
}
