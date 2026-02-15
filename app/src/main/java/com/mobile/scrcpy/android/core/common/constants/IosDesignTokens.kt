package com.mobile.scrcpy.android.core.common.constants

import androidx.compose.ui.unit.dp

/**
 * iOS 风格设计 token。
 *
 * 这层用于收口共享组件中的高频硬编码，避免 8/10/12/16/38/40/50 在各处散落。
 */
object IosDesignTokens {
    val formRowHeight = 40.dp
    val sectionTitleHeight = 35.dp
    val segmentedControlHeight = 38.dp
    val themeOptionHeight = 43.dp

    val dialogHeaderHeight = 50.dp
    val dialogHeaderHorizontalPadding = 8.dp
    val dialogActionSlotWidth = 48.dp
    val dialogHeaderSpacerHeight = 8.dp
    val dialogBottomSpacerHeight = 16.dp

    val dialogCornerRadius = 8.dp
    val cardCornerRadius = 12.dp
    val compactCornerRadius = 8.dp
    val segmentedControlContainerCornerRadius = 15.dp
    val segmentedControlChipCornerRadius = 13.dp
    val searchFieldCornerRadius = 8.dp

    val compactHorizontalPadding = 10.dp
    val standardHorizontalPadding = 16.dp
    val compactSpacing = 12.dp
    val standardSpacing = 16.dp
    val compactInlineSpacing = 6.dp

    val fieldContentStartPadding = 8.dp
    val fieldContentEndPadding = 12.dp

    val helpIconSize = 16.dp
    val trailingIconSize = 16.dp
    val externalIconSize = 18.dp

    val dialogTrailingActionWidth = 160.dp
    val dialogLabelMaxWidth = 132.dp
    val dialogLabelTextMaxWidth = 108.dp

    const val dividerAlpha = 0.3f
    const val dialogHeaderBackgroundAlpha = 0.5f
    const val disabledActionAlpha = 0.3f
}
