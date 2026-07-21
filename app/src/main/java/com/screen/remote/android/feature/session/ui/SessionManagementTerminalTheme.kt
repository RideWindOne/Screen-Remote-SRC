package com.screen.remote.android.feature.session.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

internal data class SessionManagementTerminalPalette(
    val background: Color,
    val text: Color,
    val hint: Color,
    val accent: Color,
    val match: Color,
    val number: Color,
    val string: Color,
    val path: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val separator: Color,
)

internal fun sessionManagementTerminalPalette(): SessionManagementTerminalPalette {
    return SessionManagementTerminalPalette(
        background = Color(0xFF18181A),
        text = Color(0xFFF2F2F7),
        hint = Color(0xFF9A9AA0),
        accent = Color(0xFF64A8FF),
        match = Color(0xFF7DD3FC),
        number = Color(0xFFFFB86C),
        string = Color(0xFFA8E6A3),
        path = Color(0xFFC4A7E7),
        success = Color(0xFF5BD66F),
        warning = Color(0xFFFFD166),
        error = Color(0xFFFF6961),
        separator = Color(0xFF38383A),
    )
}

internal object SessionManagementTerminalTextTokens {
    val monospace = FontFamily.Monospace
    val outputFontSize = 11.sp
    val outputLineHeight = 17.sp
    val inputFontSize = 14.sp
    val inputLineHeight = 20.sp
}
