package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.i18n.SessionTexts

@Composable
fun GroupSelectorButton(
    selectedGroupIds: List<String>,
    availableGroups: List<DeviceGroup>,
    onGroupsSelected: (List<String>) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val displayText =
        if (selectedGroupIds.isNotEmpty()) {
            availableGroups
                .filter { it.id in selectedGroupIds }
                .joinToString(" / ") { it.name }
        } else {
            SessionTexts.GROUP_PLACEHOLDER_DESCRIPTION.get()
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = SessionTexts.GROUP_SELECT.get(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(100.dp),
        )

        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (selectedGroupIds.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.weight(1f),
        )
    }

    if (showDialog) {
        GroupSelectorDialog(
            selectedGroupIds = selectedGroupIds,
            availableGroups = availableGroups,
            onGroupsSelected = onGroupsSelected,
            onDismiss = { showDialog = false },
        )
    }
}
