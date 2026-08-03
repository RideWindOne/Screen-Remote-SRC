package com.screen.remote.android.core.domain.model

data class CompatibilityCaptureSettings(
    val maxSize: Int,
    val jpegQuality: Int,
)

private const val MAX_SCREENSHOT_MAX_SIZE = 16384

fun ScrcpyConfig.compatibilityCaptureSettings(): CompatibilityCaptureSettings =
    CompatibilityCaptureSettings(
        maxSize = maxSize.coerceIn(0, MAX_SCREENSHOT_MAX_SIZE),
        jpegQuality = resolveCompatibilityCaptureQualityForMaxSize(
            maxSize.coerceIn(0, MAX_SCREENSHOT_MAX_SIZE),
        ),
    )

private fun resolveCompatibilityCaptureQualityForMaxSize(maxSize: Int): Int =
    when {
        maxSize <= 0 -> 100
        else -> maxSize.coerceIn(1, 100)
    }
