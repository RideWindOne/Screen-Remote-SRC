package com.mobile.scrcpy.android.core.common.constants

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
    val windowCornerRadius = 8.dp

    // 组件高度

    /** 分组标题高度 */
    val sectionTitleHeight = 35.dp

    /** 列表项高度 */
    val listItemHeight = 38.dp

    /** 主题选项高度 */
    val themeOptionHeight = 43.dp

    // 间距

    /** 卡片间距 */
    val cardSpacing = 10.dp

    /** 标准内边距 */
    val paddingStandard = 10.dp

    /** 标准间距 */
    val spacingStandard = 10.dp

    /** 水平内边距 */
    val paddingHorizontal = 10.dp

    /** 垂直内边距 */
    val paddingVertical = 10.dp

    // 卡片

    /** 卡片圆角 */
    val cardCornerRadius = 8.dp

    // 其他

    /** 标签宽度 */
    val labelWidth = 100.dp

    /** 音量文字宽度 */
    val volumeTextWidth = 50.dp

    /** 音量标签宽度 */
    val volumeLabelWidth = 80.dp
}
