package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup

/**
 * 紧凑分组选择器（显示在 Tab 栏右侧）
 * 只显示上一级和当前级别
 */
@Composable
fun CompactGroupSelector(
    groups: List<DeviceGroup>,
    selectedGroupPath: String,
    onGroupSelected: (String) -> Unit,
) {
    val state = rememberCompactGroupSelectorState()
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val pathInfo =
        remember(selectedGroupPath, groups) {
            buildCompactGroupSelectorPathInfo(
                groups = groups,
                selectedGroupPath = selectedGroupPath,
            )
        }

    CompactGroupSelectorContent(
        groups = groups,
        state = state,
        pathInfo = pathInfo,
        isDarkTheme = isDarkTheme,
        onGroupSelected = onGroupSelected,
    )
}
