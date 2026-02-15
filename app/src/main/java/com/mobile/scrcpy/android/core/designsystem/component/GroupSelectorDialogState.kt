package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup

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

    val isEditMode: Boolean
        get() = editingGroupId != null

    val hasPendingSelection: Boolean
        get() =
            if (isEditMode) {
                currentSelectedGroupId != null
            } else {
                currentSelectedGroupId != null && currentSelectedGroupId !in tempSelectedIds
            }

    val alreadyAddedIds: List<String>
        get() =
            if (editingGroupId != null) {
                tempSelectedIds.filter { it != editingGroupId }
            } else {
                tempSelectedIds
            }

    fun applySelection() {
        val selectedGroupId = currentSelectedGroupId ?: return
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

    fun toggleCurrentSelection(groupId: String) {
        currentSelectedGroupId =
            if (currentSelectedGroupId == groupId) {
                null
            } else {
                groupId
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
