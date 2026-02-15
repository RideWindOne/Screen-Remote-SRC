package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.manager.LanguageManager.isChinese
import com.mobile.scrcpy.android.core.domain.model.DefaultGroups
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.i18n.SessionTexts

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
            text = if (isChinese()) "返回" else "Back",
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
                text = if (isChinese()) "返回" else "Back",
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
