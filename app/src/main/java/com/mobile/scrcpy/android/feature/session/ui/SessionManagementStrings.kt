package com.mobile.scrcpy.android.feature.session.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mobile.scrcpy.android.core.common.manager.LanguageManager

internal fun managementText(
    zh: String,
    en: String,
): String = if (LanguageManager.isChinese()) zh else en

internal fun managementCountLabel(count: Int): String =
    if (LanguageManager.isChinese()) {
        "共 $count 项"
    } else {
        "$count items"
    }

@Composable
internal fun managementPanelColor(): Color =
    if (MaterialTheme.colorScheme.surface.let { 0.299f * it.red + 0.587f * it.green + 0.114f * it.blue < 0.5f }) {
        MaterialTheme.colorScheme.surface
    } else {
        Color(0xFFF7F7FA)
    }

@Composable
internal fun managementSubtleFillColor(): Color =
    if (MaterialTheme.colorScheme.surface.let { 0.299f * it.red + 0.587f * it.green + 0.114f * it.blue < 0.5f }) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    } else {
        Color(0xFFF1F2F6)
    }
