package com.mobile.scrcpy.android.core.designsystem.component

import com.mobile.scrcpy.android.core.domain.model.DefaultGroups
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.i18n.SessionTexts

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
