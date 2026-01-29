package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS 风格的 Switch 开关组件
 *
 * 特点：
 * - 更大的圆形滑块（thumb）
 * - 更圆润的轨道（track）
 * - 流畅的动画效果
 * - iOS 标准配色
 *
 * @param checked 开关状态
 * @param onCheckedChange 状态变化回调
 * @param modifier 修饰符
 * @param enabled 是否启用
 * @param width 开关宽度
 * @param height 开关高度
 * @param thumbPadding 滑块内边距
 */
@Composable
fun IOSSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 51.dp,
    height: Dp = 31.dp,
    thumbPadding: Dp = 2.dp,
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // 动画：滑块位置（0.0 = 左侧，1.0 = 右侧）
    val thumbPosition by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "thumbPosition",
    )

    // 动画：轨道颜色
    val trackColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> if (isDarkTheme) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)

                checked -> Color(0xFF34C759)

                // iOS 绿色（深浅色通用）
                else -> if (isDarkTheme) Color(0xFF39393D) else Color(0xFFE9E9EB) // 关闭状态
            },
        animationSpec = tween(durationMillis = 250),
        label = "trackColor",
    )

    // 滑块颜色（始终为白色）
    val thumbColor = if (enabled) Color.White else Color(0xFFBDBDBD)

    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }
    val heightPx = with(density) { height.toPx() }
    val thumbPaddingPx = with(density) { thumbPadding.toPx() }

    Canvas(
        modifier =
            modifier
                .size(width, height)
                .pointerInput(enabled, checked) {
                    if (enabled) {
                        detectTapGestures {
                            onCheckedChange(!checked)
                        }
                    }
                },
    ) {
        // 绘制轨道（圆角矩形）
        drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(heightPx / 2, heightPx / 2),
        )

        // 计算滑块位置和大小
        val thumbRadius = (heightPx - thumbPaddingPx * 2) / 2
        val thumbDiameter = thumbRadius * 2
        val thumbTravel = widthPx - thumbDiameter - thumbPaddingPx * 2
        val thumbX = thumbPaddingPx + thumbRadius + thumbTravel * thumbPosition
        val thumbY = heightPx / 2

        // 绘制滑块（圆形）
        drawCircle(
            color = thumbColor,
            radius = thumbRadius,
            center = Offset(thumbX, thumbY),
        )

        // 绘制滑块阴影效果（iOS 风格）
        if (enabled) {
            drawCircle(
                color = Color.Black.copy(alpha = 0.04f),
                radius = thumbRadius + 1.dp.toPx(),
                center = Offset(thumbX, thumbY + 0.5.dp.toPx()),
            )
        }
    }
}

/**
 * 获取颜色的亮度
 */
private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
