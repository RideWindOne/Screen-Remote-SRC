package com.mobile.scrcpy.android.core.designsystem.component

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.AppDimens

/**
 * 统一的分组标题组件
 * 用于显示"通用"、"ADB 管理"、"远程设备"等分组标题
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.sectionTitleHeight)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 统一的分隔线组件
 * 用于卡片内部的分隔线
 */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )
}

/**
 * Dialog 标题栏下方的统一间距
 * 用于 DialogHeader 和内容区域之间的间距
 */
@Composable
fun DialogHeaderSpacer() {
    Spacer(modifier = Modifier.height(8.dp))
}

/**
 * Dialog 底部的统一间距
 * 用于内容区域和 Dialog 底部边缘之间的间距
 */
@Composable
fun DialogBottomSpacer() {
    Spacer(modifier = Modifier.height(16.dp))
}

/**
 * 统一的 Dialog 标题栏组件
 * 支持三种布局模式：
 * 1. 左侧返回 + 中间标题 + 右侧占位
 * 2. 左侧取消 + 中间标题 + 右侧完成/保存
 * 3. 左侧返回 + 中间标题 + 右侧自定义按钮
 */
@Composable
fun DialogHeader(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    leftButtonText: String? = null,
    rightButtonText: String? = null,
    onRightButtonClick: (() -> Unit)? = null,
    rightButtonEnabled: Boolean = true,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧按钮
            if (leftButtonText != null) {
                TextButton(onClick = onDismiss) {
                    Text(
                        leftButtonText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.iOSBlue,
                    )
                }
            } else if (showBackButton) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = AppColors.iOSBlue,
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }

            // 中间标题
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )

            // 右侧按钮或内容
            if (trailingContent != null) {
                trailingContent()
            } else if (rightButtonText != null && onRightButtonClick != null) {
                TextButton(
                    onClick = onRightButtonClick,
                    enabled = rightButtonEnabled,
                ) {
                    Text(
                        rightButtonText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (rightButtonEnabled) AppColors.iOSBlue else AppColors.iOSBlue.copy(alpha = 0.3f),
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
        }

        AppDivider()
    }
}

/**
 * 统一样式的 DropdownMenu
 * 自动应用优化的背景色和阴影效果
 * 浅色模式：纯白背景 + 柔和阴影
 * 深色模式：#2C2C2E 背景 + 深色阴影
 *
 * @param expanded 是否展开
 * @param onDismissRequest 关闭回调
 * @param modifier 修饰符
 * @param offset 偏移量: offset = DpOffset(0.dp, 68.dp),
 * @param alignment 对齐方式：Alignment.TopStart(左对齐), Alignment.TopEnd(右对齐)
 * @param content 菜单项内容
 */
@Composable
fun IOSStyledDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier.widthIn(min = 30.dp, max = 150.dp).wrapContentWidth(),
    offset: DpOffset = DpOffset(0.dp, 80.dp),
    alignment: Alignment = Alignment.TopCenter,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.surface

    if (expanded) {
        Popup(
            alignment = alignment,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
            offset =
                androidx.compose.ui.unit
                    .IntOffset(offset.x.value.toInt(), offset.y.value.toInt()),
        ) {
            Surface(
                modifier =
                    modifier
                        .widthIn(min = 30.dp, max = 160.dp)
                        .wrapContentSize(),
                shape = RoundedCornerShape(12.dp),
                color = backgroundColor,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .padding(vertical = 4.dp)
                            .wrapContentSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * iOS 风格的 DropdownMenuItem
 * 文字居中对齐
 *
 * @param text 菜单项文字
 * @param onClick 点击回调
 * @param modifier 修饰符
 * @param textColor 文字颜色
 * @param fontSize 字体大小（可选，默认使用主题字体）
 */
@Composable
fun IOSStyledDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
) {
    Box(
        modifier =
            modifier
                .wrapContentSize()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val style =
            if (fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) {
                MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize)
            } else {
                MaterialTheme.typography.bodyMedium
            }
        Text(
            text = text,
            color = textColor,
            textAlign = TextAlign.Center,
            style = style,
        )
    }
}

/**
 * iOS 风格的 DropdownMenuItem（自定义内容版本）
 * 支持完全自定义的 Composable 内容，文字居中对齐
 *
 * @param text Composable 内容
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun IOSStyledDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .wrapContentSize()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        text()
    }
}

/**
 * 统一的 Dialog 容器组件
 * 自动应用全局通用的尺寸和样式配置：
 * - 宽度：屏幕宽度的 95% (AppDimens.WINDOW_WIDTH_RATIO)
 * - 高度：自适应内容，最大不超过屏幕高度的 80% (AppDimens.WINDOW_MAX_HEIGHT_RATIO)
 * - 圆角：AppDimens.windowCornerRadius
 * - 背景色：MaterialTheme.colorScheme.background
 *
 * 使用示例：
 * ```
 * Dialog(onDismissRequest = { ... }) {
 *     DialogContainer {
 *         // Dialog 内容
 *     }
 * }
 * ```
 *
 * @param modifier 额外的修饰符
 * @param widthRatio 宽度比例（相对屏幕宽度），默认 AppDimens.WINDOW_WIDTH_RATIO
 * @param maxHeightRatio 最大高度比例（相对屏幕高度），默认 AppDimens.WINDOW_MAX_HEIGHT_RATIO
 * @param cornerRadius 圆角大小，默认 AppDimens.windowCornerRadius
 * @param backgroundColor 背景色，默认 MaterialTheme.colorScheme.background
 * @param content Dialog 内容
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun DialogContainer(
    modifier: Modifier = Modifier,
    widthRatio: Float = AppDimens.WINDOW_WIDTH_RATIO,
    maxHeightRatio: Float = AppDimens.WINDOW_MAX_HEIGHT_RATIO,
    cornerRadius: Dp = AppDimens.windowCornerRadius,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable ColumnScope.() -> Unit,
) {
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * maxHeightRatio).dp

    Surface(
        modifier =
            modifier
                .fillMaxWidth(widthRatio)
                .wrapContentHeight()
                .wrapContentWidth()
                .heightIn(max = maxHeight),
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

/**
 * 统一的 Dialog 页面容器组件（带标题栏和间距）
 * 自动包含：
 * - DialogHeader（标题栏）
 * - DialogHeader 下方 16.dp 间距
 * - 内容区域（自动设置宽度、padding、滚动）
 * - 底部 16.dp 间距
 *
 * 使用示例：
 * ```
 * Dialog(onDismissRequest = { ... }) {
 *     DialogPage(
 *         title = "设置",
 *         onDismiss = { ... }
 *     ) {
 *         // 页面内容
 *         Text("内容")
 *     }
 * }
 * ```
 *
 * @param title 标题文本
 * @param onDismiss 关闭回调
 * @param modifier 额外的修饰符
 * @param widthRatio 宽度比例，默认 AppDimens.WINDOW_WIDTH_RATIO
 * @param maxHeightRatio 最大高度比例，默认 AppDimens.WINDOW_MAX_HEIGHT_RATIO
 * @param showBackButton 是否显示返回按钮，默认 true
 * @param leftButtonText 左侧按钮文本（如"取消"），与 showBackButton 互斥
 * @param rightButtonText 右侧按钮文本（如"完成"、"保存"）
 * @param onRightButtonClick 右侧按钮点击回调
 * @param rightButtonEnabled 右侧按钮是否可用，默认 true
 * @param trailingContent 右侧自定义内容（与 rightButtonText 互斥）
 * @param enableScroll 是否启用垂直滚动，默认 false
 * @param horizontalPadding 内容区域水平 padding，默认 AppDimens.paddingStandard
 * @param verticalSpacing 内容区域垂直间距（用于 Arrangement.spacedBy），默认 null（不设置）
 * @param content 页面内容
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun DialogPage(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    widthRatio: Float = AppDimens.WINDOW_WIDTH_RATIO,
    maxHeightRatio: Float = AppDimens.WINDOW_MAX_HEIGHT_RATIO,
    showBackButton: Boolean = true,
    leftButtonText: String? = null,
    rightButtonText: String? = null,
    onRightButtonClick: (() -> Unit)? = null,
    rightButtonEnabled: Boolean = true,
    trailingContent: @Composable (() -> Unit)? = null,
    enableScroll: Boolean = false,
    horizontalPadding: Dp = AppDimens.paddingStandard,
    verticalSpacing: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
    ) {
        DialogContainer(
            modifier = modifier,
            widthRatio = widthRatio,
            maxHeightRatio = maxHeightRatio,
        ) {
            // 标题栏
            DialogHeader(
                title = title,
                onDismiss = onDismiss,
                showBackButton = showBackButton,
                leftButtonText = leftButtonText,
                rightButtonText = rightButtonText,
                onRightButtonClick = onRightButtonClick,
                rightButtonEnabled = rightButtonEnabled,
                trailingContent = trailingContent,
            )

            // 标题栏下方间距
            DialogHeaderSpacer()

            // 内容区域
            val contentModifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = horizontalPadding)
                    .then(if (enableScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)

            Column(
                modifier = contentModifier,
                verticalArrangement =
                    if (verticalSpacing != null) {
                        Arrangement.spacedBy(verticalSpacing)
                    } else {
                        Arrangement.Top
                    },
            ) {
                content()
                if (enableScroll) {
                    DialogBottomSpacer()
                }
            }

            // 底部间距（非滚动模式）
            if (!enableScroll) {
                DialogBottomSpacer()
            }
        }
    }
}
