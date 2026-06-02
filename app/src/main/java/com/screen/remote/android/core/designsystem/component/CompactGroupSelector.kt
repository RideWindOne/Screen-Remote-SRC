package com.screen.remote.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.constants.AppColors
import com.screen.remote.android.core.common.constants.IosDesignTokens
import com.screen.remote.android.core.domain.model.DefaultGroups
import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SessionTexts

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

    Row(
        modifier =
            Modifier
                .height(IosDesignTokens.segmentedControlHeight)
                .clip(RoundedCornerShape(IosDesignTokens.segmentedControlContainerCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactGroupChip(
            text =
                if (pathInfo.pathParts.isEmpty()) {
                    SessionTexts.GROUP_ALL.get()
                } else {
                    pathInfo.parentName ?: SessionTexts.GROUP_ALL.get()
                },
            selected = pathInfo.pathParts.isEmpty(),
            isDarkTheme = isDarkTheme,
            textColor = MaterialTheme.colorScheme.onSurface,
            clickable = pathInfo.parentClickable,
            onClick = { state.open(CompactGroupSelectorLevel.Parent) },
        ) {
            if (pathInfo.parentClickable) {
                ParentLevelMenu(
                    groups = groups,
                    state = state,
                    pathInfo = pathInfo,
                    onGroupSelected = onGroupSelected,
                )
            }
        }

        if (pathInfo.pathParts.isNotEmpty()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IosDesignTokens.trailingIconSize),
            )

            CompactGroupChip(
                text = pathInfo.currentLevelName,
                selected = true,
                isDarkTheme = isDarkTheme,
                textColor = MaterialTheme.colorScheme.onSurface,
                clickable = pathInfo.currentClickable,
                onClick = { state.open(CompactGroupSelectorLevel.Current) },
            ) {
                if (pathInfo.currentClickable) {
                    CurrentLevelMenu(
                        state = state,
                        pathInfo = pathInfo,
                        onGroupSelected = onGroupSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactGroupChip(
    text: String,
    selected: Boolean,
    isDarkTheme: Boolean,
    textColor: Color,
    clickable: Boolean,
    onClick: () -> Unit,
    dropdownContent: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(IosDesignTokens.segmentedControlChipCornerRadius))
                .background(
                    if (selected) {
                        if (isDarkTheme) {
                            AppColors.darkIOSSelectedBackground
                        } else {
                            AppColors.iOSSelectedBackground
                        }
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = IosDesignTokens.compactSpacing),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        }

        dropdownContent()
    }
}

@Composable
internal fun ParentLevelMenu(
    groups: List<DeviceGroup>,
    state: CompactGroupSelectorState,
    pathInfo: CompactGroupSelectorPathInfo,
    onGroupSelected: (String) -> Unit,
) {
    IOSStyledDropdownMenu(
        expanded = state.isExpanded(CompactGroupSelectorLevel.Parent),
        offset = DpOffset(0.dp, 98.dp),
        onDismissRequest = state::close,
    ) {
        if (pathInfo.pathParts.isEmpty()) {
            groups
                .filter { it.parentPath == "/" }
                .forEach { group ->
                    CompactGroupMenuItem(
                        text = compactGroupDisplayName(group.name),
                        onClick = {
                            onGroupSelected(group.path)
                            state.close()
                        },
                    )
                }
            return@IOSStyledDropdownMenu
        }

        if (pathInfo.parentPath == DefaultGroups.ALL_DEVICES) {
            CompactGroupMenuItem(
                text = SessionTexts.GROUP_ALL.get(),
                onClick = {
                    onGroupSelected(DefaultGroups.ALL_DEVICES)
                    state.close()
                },
            )

            groups
                .filter { it.parentPath == "/" }
                .forEach { group ->
                    CompactGroupMenuItem(
                        text = compactGroupDisplayName(group.name),
                        onClick = {
                            onGroupSelected(group.path)
                            state.close()
                        },
                    )
                }
            return@IOSStyledDropdownMenu
        }

        CompactGroupMenuItem(
            text = CommonTexts.BUTTON_BACK.get(),
            onClick = {
                onGroupSelected(pathInfo.parentPath ?: DefaultGroups.ALL_DEVICES)
                state.close()
            },
        )

        groups
            .filter {
                it.parentPath == pathInfo.grandparentPathForSiblings &&
                    it.path != pathInfo.parentPath
            }.forEach { group ->
                CompactGroupMenuItem(
                    text = compactGroupDisplayName(group.name),
                    onClick = {
                        onGroupSelected(group.path)
                        state.close()
                    },
                )
            }
    }
}

@Composable
internal fun CurrentLevelMenu(
    state: CompactGroupSelectorState,
    pathInfo: CompactGroupSelectorPathInfo,
    onGroupSelected: (String) -> Unit,
) {
    IOSStyledDropdownMenu(
        offset = DpOffset(0.dp, 98.dp),
        expanded = state.isExpanded(CompactGroupSelectorLevel.Current),
        onDismissRequest = state::close,
    ) {
        if (pathInfo.pathParts.size > 1) {
            CompactGroupMenuItem(
                text = CommonTexts.BUTTON_BACK.get(),
                onClick = {
                    onGroupSelected(pathInfo.parentPath ?: DefaultGroups.ALL_DEVICES)
                    state.close()
                },
            )
        }

        pathInfo.currentChildGroups.forEach { group ->
            CompactGroupMenuItem(
                text = compactGroupDisplayName(group.name),
                onClick = {
                    onGroupSelected(group.path)
                    state.close()
                },
            )
        }
    }
}

@Composable
private fun CompactGroupMenuItem(
    text: String,
    onClick: () -> Unit,
) {
    IOSStyledDropdownMenuItem(
        text = text,
        onClick = onClick,
    )
}

internal data class CompactGroupSelectorPathInfo(
    val pathParts: List<String>,
    val parentPath: String?,
    val parentName: String?,
    val currentLevelName: String,
    val currentChildGroups: List<DeviceGroup>,
    val hasFirstLevelGroups: Boolean,
    val grandparentPathForSiblings: String,
) {
    val parentClickable: Boolean
        get() = if (pathParts.isEmpty()) hasFirstLevelGroups else true

    val currentClickable: Boolean
        get() = currentChildGroups.isNotEmpty()
}

internal fun buildCompactGroupSelectorPathInfo(
    groups: List<DeviceGroup>,
    selectedGroupPath: String,
): CompactGroupSelectorPathInfo {
    val pathParts =
        if (selectedGroupPath == DefaultGroups.ALL_DEVICES || selectedGroupPath == DefaultGroups.UNGROUPED) {
            emptyList()
        } else {
            selectedGroupPath.split("/").filter { it.isNotEmpty() }
        }

    val parentPath =
        when {
            pathParts.isEmpty() -> null
            pathParts.size == 1 -> DefaultGroups.ALL_DEVICES
            else -> "/" + pathParts.dropLast(1).joinToString("/")
        }
    val parentName =
        when {
            pathParts.isEmpty() -> null
            pathParts.size == 1 -> SessionTexts.GROUP_ALL.get()
            else -> compactGroupDisplayName(pathParts[pathParts.size - 2])
        }
    val currentLevelName =
        if (pathParts.isEmpty()) {
            SessionTexts.GROUP_ALL.get()
        } else {
            compactGroupDisplayName(pathParts.last())
        }
    val grandparentPathForSiblings =
        if (pathParts.size <= 2) {
            "/"
        } else {
            "/" + pathParts.dropLast(2).joinToString("/")
        }

    return CompactGroupSelectorPathInfo(
        pathParts = pathParts,
        parentPath = parentPath,
        parentName = parentName,
        currentLevelName = currentLevelName,
        currentChildGroups = groups.filter { it.parentPath == selectedGroupPath },
        hasFirstLevelGroups = groups.any { it.parentPath == "/" },
        grandparentPathForSiblings = grandparentPathForSiblings,
    )
}

internal fun compactGroupDisplayName(name: String): String =
    if (name.length > 10) {
        name.take(10) + "..."
    } else {
        name
    }

@Composable
internal fun rememberCompactGroupSelectorState(): CompactGroupSelectorState =
    remember { CompactGroupSelectorState() }

@Stable
internal class CompactGroupSelectorState {
    var showDropdown by mutableStateOf(false)
    var clickedLevel by mutableStateOf<CompactGroupSelectorLevel?>(null)

    fun open(level: CompactGroupSelectorLevel) {
        clickedLevel = level
        showDropdown = true
    }

    fun close() {
        showDropdown = false
        clickedLevel = null
    }

    fun isExpanded(level: CompactGroupSelectorLevel): Boolean = showDropdown && clickedLevel == level
}

internal enum class CompactGroupSelectorLevel {
    Parent,
    Current,
}
