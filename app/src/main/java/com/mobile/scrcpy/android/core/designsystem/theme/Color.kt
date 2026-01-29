package com.mobile.scrcpy.android.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.mobile.scrcpy.android.core.common.AppColors

/**
 * Light color scheme for the app
 */
val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF4A90E2),
        secondary = Color(0xFFE85D75),
        background = Color(0xFFF5F5F5),
        surface = Color.White,
        surfaceVariant = AppColors.lightDropdownBackground, // DropdownMenu 使用纯白
        onSurface = Color.Black,
        onSurfaceVariant = Color(0xFF6E6E73),
        outline = Color(0xFFE0E0E0),
    )

/**
 * Dark color scheme for the app
 */
val DarkColorScheme =
    darkColorScheme(
        primary = AppColors.darkButtonPrimary,
        secondary = Color(0xFFE85D75),
        background = AppColors.darkBackground,
        surface = AppColors.darkCard,
        surfaceVariant = AppColors.darkDropdownBackground, // DropdownMenu 使用
        onSurface = AppColors.darkTextPrimary,
        onSurfaceVariant = AppColors.darkTextSecondary,
        outline = AppColors.darkDivider,
    )
