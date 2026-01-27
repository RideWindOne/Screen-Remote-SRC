package com.mobile.scrcpy.android.core.i18n

import com.mobile.scrcpy.android.core.common.manager.LanguageManager

/**
 * 文本对（中文+英文）
 */
data class TextPair(
    val chinese: String,
    val english: String
) {
    /**
     * 根据当前语言获取文本
     */
    fun get(): String {
        return LanguageManager.getText(chinese, english)
    }
}
