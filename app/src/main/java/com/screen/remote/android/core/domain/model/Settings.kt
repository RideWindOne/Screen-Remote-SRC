package com.screen.remote.android.core.domain.model

import kotlinx.serialization.Serializable
import com.screen.remote.android.core.update.UpdateChannel

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
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
)
