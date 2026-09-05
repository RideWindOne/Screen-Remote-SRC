package com.screen.remote.android.core.domain.model

import com.screen.remote.android.core.update.UpdateChannel
import kotlinx.serialization.Serializable

/**
 * 主题模式
 */
@Serializable
enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
}

/**
 * 应用语言
 */
@Serializable
enum class AppLanguage {
    AUTO, // 跟随系统
    CHINESE, // 中文
    ENGLISH, // English
}

@Serializable
data class CustomShellCommand(
    val id: String,
    val name: String,
    val command: String,
)

/**
 * 应用设置
 */
@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.AUTO,
    val enableActivityLog: Boolean = true,
    val enableAudioStreamLog: Boolean = false,
    val enableVideoStreamLog: Boolean = false,
    val enableControlStreamLog: Boolean = false,
    val enableEventStreamLog: Boolean = false,
    val enableShellStreamLog: Boolean = false,
    val enableManagementLog: Boolean = false,
    val enableDebugMode: Boolean = false,
    val enableFloatingHapticFeedback: Boolean = true,
    val allowScreenCapture: Boolean = true,
    val showPerformanceStats: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val customShellCommands: List<CustomShellCommand> = emptyList(),
    val replaceDefaultShellCommands: Boolean = false,
    val notifyAllNotificationsOnStart: Boolean = false,
)
