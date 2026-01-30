package com.mobile.scrcpy.android.core.domain.model

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

/**
 * 应用设置
 */
@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.AUTO,
    val keepAliveMinutes: Int = 5,
    val showOnLockScreen: Boolean = false,
    val enableActivityLog: Boolean = true,
    val fileTransferPath: String = "",
    val enableFloatingHapticFeedback: Boolean = true,
)
