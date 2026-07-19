package com.screen.remote.android.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.screen.remote.android.core.domain.model.DeviceGroup

internal fun sanitizeSelectedGroupIds(
    selectedGroupIds: List<String>,
    availableGroupIds: Set<String>,
): List<String> = selectedGroupIds.filter { it in availableGroupIds }.distinct()

@Composable
internal fun rememberGroupSelectorDialogState(selectedGroupIds: List<String>): GroupSelectorDialogState =
    remember(selectedGroupIds) {
        GroupSelectorDialogState(selectedGroupIds)
    }

@Stable
internal class GroupSelectorDialogState(
    initialSelectedGroupIds: List<String>,
) {
    var tempSelectedIds by mutableStateOf(initialSelectedGroupIds)
    var currentSelectedGroupId by mutableStateOf<String?>(null)
    var editingGroupId by mutableStateOf<String?>(null)
    var expandedPaths by mutableStateOf(setOf<String>())

    val alreadyAddedIds: List<String>
        get() =
            if (editingGroupId != null) {
                tempSelectedIds.filter { it != editingGroupId }
            } else {
                tempSelectedIds
            }

    fun selectGroup(groupId: String) {
        if (groupId in alreadyAddedIds) return

        currentSelectedGroupId = groupId
        applySelection(groupId)
    }

    private fun applySelection(selectedGroupId: String) {
        val groupIdBeingEdited = editingGroupId

        tempSelectedIds =
            if (groupIdBeingEdited != null) {
                tempSelectedIds.map { groupId ->
                    if (groupId == groupIdBeingEdited) selectedGroupId else groupId
                }
            } else if (selectedGroupId !in tempSelectedIds) {
                tempSelectedIds + selectedGroupId
            } else {
                tempSelectedIds
            }

        editingGroupId = null
        currentSelectedGroupId = null
    }

    fun toggleExpanded(path: String) {
        expandedPaths =
            if (path in expandedPaths) {
                expandedPaths - path
            } else {
                expandedPaths + path
            }
    }

    fun startEditing(group: DeviceGroup) {
        editingGroupId = group.id
        currentSelectedGroupId = group.id
        expandedPaths = expandedPaths + buildExpandedPaths(group.parentPath)
    }

    fun removeSelectedGroup(groupId: String) {
        tempSelectedIds = tempSelectedIds.filter { it != groupId }
        if (editingGroupId == groupId) {
            editingGroupId = null
            currentSelectedGroupId = null
        }
    }

    private fun buildExpandedPaths(parentPath: String): Set<String> {
        val pathsToExpand = mutableSetOf("/")
        var currentPath = parentPath

        while (currentPath != "/" && currentPath.isNotEmpty()) {
            pathsToExpand.add(currentPath)
            val lastSlash = currentPath.lastIndexOf('/')
            currentPath =
                if (lastSlash > 0) {
                    currentPath.take(lastSlash)
                } else {
                    "/"
                }
        }

        return pathsToExpand
    }
}
