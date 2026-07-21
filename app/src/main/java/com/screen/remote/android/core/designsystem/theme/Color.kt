package com.screen.remote.android.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.screen.remote.android.core.common.AppColors

/**
 * Light color scheme for the app
 */
val LightColorScheme =
    lightColorScheme(
        primary = AppColors.iOSBlue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD9ECFF),
        onPrimaryContainer = Color(0xFF003A70),
        secondary = AppColors.success,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDF7E3),
        onSecondaryContainer = Color(0xFF0B4B20),
        tertiary = Color(0xFF5856D6),
        onTertiary = Color.White,
        background = AppColors.lightBackground,
        onBackground = AppColors.lightTextPrimary,
        surface = AppColors.lightSurface,
        onSurface = AppColors.lightTextPrimary,
        surfaceVariant = AppColors.lightSurfaceMuted,
        onSurfaceVariant = AppColors.lightTextSecondary,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = AppColors.lightSurface,
        surfaceContainerLow = Color(0xFFF8F8FA),
        surfaceContainer = AppColors.lightBackground,
        surfaceContainerHigh = AppColors.lightSurface,
        surfaceContainerHighest = AppColors.lightSurfaceMuted,
        outline = Color(0xFF8E8E93),
        outlineVariant = AppColors.lightDivider,
        error = AppColors.error,
        onError = Color.White,
        errorContainer = Color(0xFFFFE5E3),
        onErrorContainer = Color(0xFF7A1210),
        scrim = Color.Black,
    )

/**
 * Dark color scheme for the app
 */
val DarkColorScheme =
    darkColorScheme(
        primary = AppColors.darkButtonPrimary,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF003F73),
        onPrimaryContainer = Color(0xFFD6EAFF),
        secondary = Color(0xFF30D158),
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF123D20),
        onSecondaryContainer = Color(0xFFDDF7E3),
        tertiary = Color(0xFFBF5AF2),
        onTertiary = Color.Black,
        background = AppColors.darkBackground,
        onBackground = AppColors.darkTextPrimary,
        surface = AppColors.darkCard,
        surfaceVariant = AppColors.darkIOSSelectedBackground,
        onSurface = AppColors.darkTextPrimary,
        onSurfaceVariant = AppColors.darkTextSecondary,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = AppColors.darkBackground,
        surfaceContainerLow = AppColors.darkCard,
        surfaceContainer = Color(0xFF242426),
        surfaceContainerHigh = AppColors.darkDialogBackground,
        surfaceContainerHighest = AppColors.darkDialogHeader,
        outline = AppColors.darkIcon,
        outlineVariant = AppColors.darkDivider,
        error = Color(0xFFFF453A),
        onError = Color.Black,
        errorContainer = Color(0xFF5C1A18),
        onErrorContainer = Color(0xFFFFDAD7),
        scrim = Color.Black,
    )
