package com.screen.remote.android.core.i18n

import com.screen.remote.android.core.common.manager.LanguageManager
import java.util.Locale

/**
 * 文本对（中文+英文）
 */
data class TextPair(
    val chinese: String,
    val english: String,
) {
    /**
     * 根据当前语言获取文本
     */
    fun get(): String = LanguageManager.getText(chinese, english)

    fun format(vararg args: Any?): String = String.format(Locale.getDefault(), get(), *args)
}
