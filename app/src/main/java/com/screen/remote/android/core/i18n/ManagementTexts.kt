package com.screen.remote.android.core.i18n

object ManagementTexts {
    fun text(
        chinese: String,
        english: String,
    ): String = TextPair(chinese, english).get()

    fun countLabel(count: Int): String = TextPair("共 %d 项", "%d items").format(count)
}
