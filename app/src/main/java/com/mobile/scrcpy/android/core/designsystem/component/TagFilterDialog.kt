package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.LogTexts

@Composable
fun TagFilterDialog(
    availableTags: List<String>,
    selectedTags: Set<String>,
    onTagsSelected: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var tempSelectedTags by remember { mutableStateOf(selectedTags) }

    DialogPage(
        title = LogTexts.LOG_FILTER_BY_TAG.get(),
        onDismiss = onDismiss,
        showBackButton = true,
        rightButtonText = CommonTexts.BUTTON_CONFIRM.get(),
        onRightButtonClick = {
            onTagsSelected(tempSelectedTags)
            onDismiss()
        },
        maxHeightRatio = 0.6f,
        enableScroll = true,
        verticalSpacing = 10.dp,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape =
                RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation =
                CardDefaults
                    .cardElevation(defaultElevation = 0.5.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (availableTags.isEmpty()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = LogTexts.LOG_NO_RESULTS.get(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tempSelectedTags =
                                        if (tempSelectedTags.size == availableTags.size) {
                                            emptySet()
                                        } else {
                                            availableTags.toSet()
                                        }
                                }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = tempSelectedTags.size == availableTags.size,
                            onCheckedChange = null,
                        )
                        Text(
                            text = LogTexts.LOG_ALL_TAGS.get(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    AppDivider()

                    availableTags.forEach { tag ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tempSelectedTags =
                                            if (tag in tempSelectedTags) {
                                                tempSelectedTags - tag
                                            } else {
                                                tempSelectedTags + tag
                                            }
                                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = tag in tempSelectedTags,
                                onCheckedChange = null,
                            )
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
