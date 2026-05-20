package com.screen.remote.android.core.common.constants

import androidx.compose.ui.unit.dp

/**
 * 应用尺寸常量
 * 包含窗口、组件、间距、卡片等尺寸定义
 */
object AppDimens {
    // 窗口尺寸

    /** Dialog 窗口宽度比例 */
    const val WINDOW_WIDTH_RATIO = 0.95f

    /** Dialog 窗口最大高度比例（相对屏幕高度） */
    const val WINDOW_MAX_HEIGHT_RATIO = 0.8f

    /** 窗口圆角 */
    val windowCornerRadius = IosDesignTokens.dialogCornerRadius

    // 组件高度

    /** 分组标题高度 */
    val sectionTitleHeight = IosDesignTokens.sectionTitleHeight

    /** 列表项高度 */
    val listItemHeight = IosDesignTokens.formRowHeight

    /** 主题选项高度 */
    val themeOptionHeight = IosDesignTokens.themeOptionHeight

    // 间距

    /** 卡片间距 */
    val cardSpacing = IosDesignTokens.compactSpacing

    /** 标准内边距 */
    val paddingStandard = IosDesignTokens.compactHorizontalPadding

    /** 标准间距 */
    val spacingStandard = IosDesignTokens.compactHorizontalPadding

    /** 水平内边距 */
    val paddingHorizontal = IosDesignTokens.compactHorizontalPadding

    /** 垂直内边距 */
    val paddingVertical = IosDesignTokens.compactHorizontalPadding

    // 卡片

    /** 卡片圆角 */
    val cardCornerRadius = IosDesignTokens.cardCornerRadius

    // 其他

    /** 标签宽度 */
    val labelWidth = 100.dp

    /** 音量文字宽度 */
    val volumeTextWidth = 50.dp

    /** 音量标签宽度 */
    val volumeLabelWidth = 80.dp
}
