package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupType

@Composable
internal fun rememberGroupManagementDialogState(): GroupManagementDialogState =
    remember { GroupManagementDialogState() }

@Stable
internal class GroupManagementDialogState {
    var showAddDialog by mutableStateOf(false)
    var editingGroup by mutableStateOf<DeviceGroup?>(null)
    var groupToDelete by mutableStateOf<DeviceGroup?>(null)
    var expandedPaths by mutableStateOf(setOf<String>())
    var selectedType by mutableStateOf(GroupType.SESSION)

    fun startAdding() {
        editingGroup = null
        showAddDialog = true
    }

    fun startEditing(group: DeviceGroup) {
        editingGroup = group
        showAddDialog = true
    }

    fun requestDelete(group: DeviceGroup) {
        groupToDelete = group
    }

    fun dismissAddDialog() {
        showAddDialog = false
        editingGroup = null
    }

    fun dismissDeleteDialog() {
        groupToDelete = null
    }

    fun toggleExpanded(path: String) {
        expandedPaths =
            if (path in expandedPaths) {
                expandedPaths - path
            } else {
                expandedPaths + path
            }
    }
}
