package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.domain.model.ActionType
import com.mobile.scrcpy.android.core.domain.model.ScrcpyAction
import java.util.UUID

@Composable
fun AddActionDialog(
    onDismiss: () -> Unit,
    onConfirm: (ScrcpyAction) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ActionType.AUTOMATION) }

    DialogPage(
        title = "添加新自动化",
        onDismiss = onDismiss,
        showBackButton = false,
        leftButtonText = "取消",
        rightButtonText = "添加",
        rightButtonEnabled = name.isNotBlank(),
        onRightButtonClick = {
            if (name.isNotBlank()) {
                onConfirm(
                    ScrcpyAction(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        type = selectedType,
                        commands = emptyList(),
                    ),
                )
            }
        },
        enableScroll = true,
        verticalSpacing = 10.dp,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("自动化名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Text("选择类型", style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = {
                        Text(
                            when (type) {
                                ActionType.CONVERSATION -> "对话"
                                ActionType.AUTOMATION -> "自动化"
                            },
                        )
                    },
                )
            }
        }
    }
}
