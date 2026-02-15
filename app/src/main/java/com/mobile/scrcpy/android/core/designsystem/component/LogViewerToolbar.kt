package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.LogTexts

@Composable
internal fun LogViewerToolbar(
    isSearchActive: Boolean,
    selectedTags: Set<String>,
    onToggleSearch: () -> Unit,
    onOpenFilter: () -> Unit,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                contentDescription = LogTexts.LOG_SEARCH_PLACEHOLDER.get() as String?,
                tint = Color(0xFF007AFF),
            )
        }

        BadgedBox(
            badge = {
                if (selectedTags.isNotEmpty()) {
                    Badge {
                        Text(selectedTags.size.toString())
                    }
                }
            },
        ) {
            IconButton(onClick = onOpenFilter) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = LogTexts.LOG_FILTER_BY_TAG.get(),
                    tint =
                        if (selectedTags.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            AppColors.iOSBlue
                        },
                )
            }
        }

        IconButton(onClick = onShare) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = LogTexts.LOG_SHARE_BUTTON.get(),
                tint = Color(0xFF007AFF),
            )
        }

        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = CommonTexts.BUTTON_DONE.get(),
                tint = Color(0xFF007AFF),
            )
        }
    }
}
