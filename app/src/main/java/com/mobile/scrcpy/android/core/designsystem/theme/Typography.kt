package com.mobile.scrcpy.android.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * App typography - 统一字体大小规范
 */
val AppTypography =
    Typography(
        // title - 17sp (标题、按钮)
        titleLarge = TextStyle(fontSize = 17.sp),
        titleMedium = TextStyle(fontSize = 16.sp),
        titleSmall = TextStyle(fontSize = 15.sp),
        // body - 15sp (正文、描述)
        bodyLarge = TextStyle(fontSize = 15.sp),
        bodyMedium = TextStyle(fontSize = 14.sp),
        bodySmall = TextStyle(fontSize = 13.sp), // caption - 13sp
        // label - 17sp (按钮标签)
        labelLarge = TextStyle(fontSize = 17.sp),
        labelMedium = TextStyle(fontSize = 15.sp),
        labelSmall = TextStyle(fontSize = 13.sp),
    )
