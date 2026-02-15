package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.IosDesignTokens
import com.mobile.scrcpy.android.core.i18n.LogTexts
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun LogViewerContent(
    file: File,
    dateFormat: SimpleDateFormat,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTags: Set<String>,
    onRemoveTag: (String) -> Unit,
    isDarkTheme: Boolean,
    displayContent: String,
) {
    if (isSearchActive) {
        LogSearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
        )
    }

    if (selectedTags.isNotEmpty()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            selectedTags.forEach { tag ->
                FilterChip(
                    selected = true,
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor =
                                if (isDarkTheme) {
                                    AppColors.darkIOSSelectedBackground
                                } else {
                                    AppColors.iOSSelectedBackground
                                },
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    onClick = { onRemoveTag(tag) },
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(IosDesignTokens.trailingIconSize),
                        )
                    },
                )
            }
        }
    }

    LogFileMetadataCard(
        file = file,
        dateFormat = dateFormat,
    )

    LogContentCard(displayContent = displayContent)
}

@Composable
private fun LogSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IosDesignTokens.segmentedControlHeight)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(IosDesignTokens.searchFieldCornerRadius))
                .padding(horizontal = IosDesignTokens.compactSpacing),
        textStyle =
            TextStyle(
                fontSize = 14.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        singleLine = true,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = LogTexts.LOG_SEARCH_PLACEHOLDER.get(),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(IosDesignTokens.trailingIconSize),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun LogFileMetadataCard(
    file: File,
    dateFormat: SimpleDateFormat,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LogMetadataRow(
                label = LogTexts.LOG_FILE_LABEL.get() + "：",
                value = file.name,
            )
            AppDivider()
            LogMetadataRow(
                label = LogTexts.LOG_SIZE_LABEL.get() + "：",
                value = formatFileSize(file.length()),
            )
            AppDivider()
            LogMetadataRow(
                label = LogTexts.LOG_MODIFIED_LABEL.get() + "：",
                value = dateFormat.format(Date(file.lastModified())),
            )
        }
    }
}

@Composable
private fun LogMetadataRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogContentCard(displayContent: String) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(400.dp),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        SelectionContainer {
            Text(
                text = displayContent,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
