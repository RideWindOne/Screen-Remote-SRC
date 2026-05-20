package com.screen.remote.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.constants.AppColors
import com.screen.remote.android.core.common.constants.IosDesignTokens
import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.i18n.SessionTexts

@Composable
internal fun CompactGroupSelectorContent(
    groups: List<DeviceGroup>,
    state: CompactGroupSelectorState,
    pathInfo: CompactGroupSelectorPathInfo,
    isDarkTheme: Boolean,
    onGroupSelected: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .height(IosDesignTokens.segmentedControlHeight)
                .clip(RoundedCornerShape(IosDesignTokens.segmentedControlContainerCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactGroupChip(
            text =
                if (pathInfo.pathParts.isEmpty()) {
                    SessionTexts.GROUP_ALL.get()
                } else {
                    pathInfo.parentName ?: SessionTexts.GROUP_ALL.get()
                },
            selected = pathInfo.pathParts.isEmpty(),
            isDarkTheme = isDarkTheme,
            textColor =
                if (pathInfo.pathParts.isEmpty()) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            clickable = pathInfo.parentClickable,
            onClick = { state.open(CompactGroupSelectorLevel.Parent) },
        ) {
            if (pathInfo.parentClickable) {
                ParentLevelMenu(
                    groups = groups,
                    state = state,
                    pathInfo = pathInfo,
                    onGroupSelected = onGroupSelected,
                )
            }
        }

        if (pathInfo.pathParts.isNotEmpty()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IosDesignTokens.trailingIconSize),
            )

            CompactGroupChip(
                text = pathInfo.currentLevelName,
                selected = true,
                isDarkTheme = isDarkTheme,
                textColor = MaterialTheme.colorScheme.onSurface,
                clickable = pathInfo.currentClickable,
                onClick = { state.open(CompactGroupSelectorLevel.Current) },
            ) {
                if (pathInfo.currentClickable) {
                    CurrentLevelMenu(
                        state = state,
                        pathInfo = pathInfo,
                        onGroupSelected = onGroupSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactGroupChip(
    text: String,
    selected: Boolean,
    isDarkTheme: Boolean,
    textColor: Color,
    clickable: Boolean,
    onClick: () -> Unit,
    dropdownContent: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(IosDesignTokens.segmentedControlChipCornerRadius))
                .background(
                    if (selected) {
                        if (isDarkTheme) {
                            AppColors.darkIOSSelectedBackground
                        } else {
                            AppColors.iOSSelectedBackground
                        }
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .then(
                        if (clickable) {
                            Modifier.clickable(onClick = onClick)
                        } else {
                            Modifier
                        },
                    ).padding(horizontal = IosDesignTokens.compactSpacing),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        }

        dropdownContent()
    }
}
