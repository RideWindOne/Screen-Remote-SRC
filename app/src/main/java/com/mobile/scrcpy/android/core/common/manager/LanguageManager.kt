package com.mobile.scrcpy.android.core.common.manager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.mobile.scrcpy.android.core.data.datastore.PreferencesManager
import com.mobile.scrcpy.android.core.domain.model.AppLanguage
import java.util.Locale

/**
 * 语言管理器
 * 根据用户设置的语言返回对应的文本
 */
object LanguageManager {
    private var currentLanguage: AppLanguage = AppLanguage.AUTO
    
    /**
     * 设置当前语言
     */
    fun setLanguage(language: AppLanguage) {
        currentLanguage = language
    }
    
    /**
     * 获取当前语言
     */
    fun getCurrentLanguage(): AppLanguage {
        return currentLanguage
    }
    
    /**
     * 获取当前实际使用的语言（考虑 AUTO 模式）
     */
    fun getEffectiveLanguage(): AppLanguage {
        return when (currentLanguage) {
            AppLanguage.AUTO -> {
                // 根据系统语言判断
                val systemLang = Locale.getDefault().language
                if (systemLang == "zh") AppLanguage.CHINESE else AppLanguage.ENGLISH
            }
            else -> currentLanguage
        }
    }
    
    /**
     * 判断当前是否使用中文
     */
    fun isChinese(): Boolean {
        return getEffectiveLanguage() == AppLanguage.CHINESE
    }
    
    /**
     * 判断当前是否使用英文
     */
    fun isEnglish(): Boolean {
        return getEffectiveLanguage() == AppLanguage.ENGLISH
    }
    
    /**
     * 获取文本（中文/英文）
     */
    fun getText(chinese: String, english: String): String {
        return when (getEffectiveLanguage()) {
            AppLanguage.CHINESE -> chinese
            AppLanguage.ENGLISH -> english
            AppLanguage.AUTO -> {
                val systemLang = Locale.getDefault().language
                if (systemLang == "zh") chinese else english
            }
        }
    }
}

/**
 * Composable 函数：获取双语文本
 * 会自动响应语言变化
 */
@Composable
fun rememberText(chinese: String, english: String): String {
    val context = LocalContext.current
    val preferencesManager = PreferencesManager(context)
    val settings by preferencesManager.settingsFlow.collectAsState(initial = com.mobile.scrcpy.android.core.domain.model.AppSettings())
    
    // 更新语言管理器
    LanguageManager.setLanguage(settings.language)
    
    return LanguageManager.getText(chinese, english)
}

/**
 * Composable 函数：获取双语文本（TextPair 重载版本）
 * 会自动响应语言变化
 */
@Composable
fun rememberText(textPair: com.mobile.scrcpy.android.core.i18n.TextPair): String {
    return rememberText(textPair.chinese, textPair.english)
}
