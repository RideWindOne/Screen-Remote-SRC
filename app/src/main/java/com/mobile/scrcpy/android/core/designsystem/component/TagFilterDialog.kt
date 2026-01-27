package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.i18n.LogTexts

import com.mobile.scrcpy.android.core.i18n.CommonTexts
@Composable
fun TagFilterDialog(
    availableTags: List<String>,
    selectedTags: Set<String>,
    onTagsSelected: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var tempSelectedTags by remember { mutableStateOf(selectedTags) }
    
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxDialogHeight = screenHeight * 0.6f
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(LogTexts.LOG_FILTER_BY_TAG.get()) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (availableTags.isEmpty()) {
                    Text(
                        text = LogTexts.LOG_NO_RESULTS.get(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                tempSelectedTags = if (tempSelectedTags.size == availableTags.size) {
                                    emptySet()
                                } else {
                                    availableTags.toSet()
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = tempSelectedTags.size == availableTags.size,
                            onCheckedChange = null
                        )
                        Text(
                            text = LogTexts.LOG_ALL_TAGS.get(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    AppDivider()
                    
                    availableTags.forEach { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tempSelectedTags = if (tag in tempSelectedTags) {
                                        tempSelectedTags - tag
                                    } else {
                                        tempSelectedTags + tag
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = tag in tempSelectedTags,
                                onCheckedChange = null
                            )
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onTagsSelected(tempSelectedTags)
                    onDismiss()
                }
            ) {
                Text(CommonTexts.BUTTON_CONFIRM.get())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(CommonTexts.BUTTON_CANCEL.get())
            }
        }
    )
}
