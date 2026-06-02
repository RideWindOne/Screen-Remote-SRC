package com.screen.remote.android.feature.session.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.screen.remote.android.core.designsystem.theme.DarkColorScheme
import com.screen.remote.android.core.designsystem.theme.LightColorScheme

internal data class SessionManagementTerminalPalette(
    val background: Color,
    val text: Color,
    val hint: Color,
    val accent: Color,
    val error: Color,
    val separator: Color,
)

@Composable
internal fun sessionManagementTerminalPalette(): SessionManagementTerminalPalette {
    val currentThemeIsDark = MaterialTheme.colorScheme.surface == DarkColorScheme.surface
    val oppositeScheme = if (currentThemeIsDark) LightColorScheme else DarkColorScheme
    return SessionManagementTerminalPalette(
        background = if (currentThemeIsDark) LightColorScheme.background else DarkColorScheme.surface,
        text = oppositeScheme.onSurface,
        hint = oppositeScheme.onSurfaceVariant,
        accent = oppositeScheme.primary,
        error = oppositeScheme.error,
        separator = oppositeScheme.outline,
    )
}

internal object SessionManagementTerminalTextTokens {
    val monospace = FontFamily.Monospace
    val outputFontSize = 11.sp
    val outputLineHeight = 17.sp
    val inputFontSize = 14.sp
    val inputLineHeight = 20.sp
}
