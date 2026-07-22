package com.screen.remote.android.feature.session.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.core.designsystem.component.StatusBadge

private val ManagementFabInset = 20.dp
private val ManagementFabIconSize = 18.dp
private val ManagementTopBarHeight = 52.dp
private val ManagementTopBarSideWidth = 52.dp
private val ManagementTopBarActionSize = 40.dp
private val ManagementTopBarHorizontalInset = 4.dp
private val ManagementDrawerWidth = 248.dp
private val ManagementDrawerEdgeCornerRadius = 22.dp
private val ManagementDrawerPadding = 12.dp
private val ManagementDrawerHeaderPadding = 18.dp
private val ManagementDrawerSectionSpacing = 12.dp
private val ManagementDrawerItemSpacing = 6.dp
private val ManagementSurfaceElevation = 1.dp

internal val SessionManagementCardShape = RoundedCornerShape(12.dp)
internal val SessionManagementPageBottomShape =
    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
internal val SessionManagementControlShape = RoundedCornerShape(18.dp)
internal val SessionManagementControlHeight = 38.dp
internal const val SessionManagementPageOuterWidthFraction = 0.98f
internal const val SessionManagementContentWidthFraction = 0.95f
internal const val SessionManagementContentWidthWithinPageFraction =
    SessionManagementContentWidthFraction / SessionManagementPageOuterWidthFraction
internal val SessionManagementPageInnerTopPadding = 8.dp
internal val SessionManagementPageInnerBottomPadding = 8.dp

@Composable
internal fun SessionManagementPageFrame(
    modifier: Modifier = Modifier,
    clipBottom: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier.then(
                if (clipBottom) {
                    Modifier.clip(SessionManagementPageBottomShape)
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

@Composable
internal fun SessionManagementSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val textStyle =
        MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
        )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .height(SessionManagementControlHeight),
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { innerTextField ->
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = SessionManagementControlShape,
                color = Color.Transparent,
                border =
                    BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f),
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    IconButton(
                        onClick = onSearch,
                        modifier = Modifier.size(SessionManagementControlHeight),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = contentDescription,
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun SessionManagementLoadingBar(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier =
            modifier
                .fillMaxWidth()
                .height(2.dp)
                .zIndex(2f),
    )
}

@Composable
internal fun SessionManagementInfoPlaceholderRow(
    label: String,
    labelWidth: Dp = 88.dp,
    rowMinHeight: Dp = 18.dp,
) {
    SessionManagementInfoRowLayout(
        label = label,
        labelWidth = labelWidth,
        rowHeight = rowMinHeight,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp),
            )
        }
    }
}

@Composable
internal fun SessionManagementInfoRow(
    label: String,
    value: String,
    labelWidth: Dp = 88.dp,
    rowMinHeight: Dp = 0.dp,
    valueTextStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    valueMaxLines: Int = 2,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    SessionManagementInfoRowLayout(
        label = label,
        labelWidth = labelWidth,
        rowHeight = rowMinHeight,
    ) {
        Text(
            text = value,
            style = valueTextStyle,
            color = valueColor,
            modifier = Modifier.weight(1f),
            maxLines = valueMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SessionManagementInfoRowLayout(
    label: String,
    labelWidth: Dp,
    rowHeight: Dp,
    valueContent: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (rowHeight > 0.dp) {
                        Modifier.height(rowHeight)
                    } else {
                        Modifier
                    },
                ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(labelWidth),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        valueContent()
    }
}

@Composable
internal fun SessionManagementVirtualizedPanelRow(
    index: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    dividerInsetStart: Dp = 0.dp,
    dividerInsetEnd: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(widthFraction),
        shape = managementVirtualizedPanelShape(index = index, totalCount = totalCount),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
            if (index != totalCount - 1) {
                AppDivider(
                    modifier =
                        Modifier.padding(
                            start = dividerInsetStart,
                            end = dividerInsetEnd,
                        ),
                )
            }
        }
    }
}

private fun managementVirtualizedPanelShape(
    index: Int,
    totalCount: Int,
): RoundedCornerShape {
    val radius = AppDimens.cardCornerRadius
    val topRadius = if (index == 0) radius else 0.dp
    val bottomRadius = if (index == totalCount - 1) radius else 0.dp
    return RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )
}

@Composable
internal fun SessionManagementNoteCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = SessionManagementCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SessionManagementAddFab(
    modifier: Modifier = Modifier,
    contentDescription: String,
    onClick: () -> Unit,
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier =
            modifier
                .navigationBarsPadding()
                .padding(end = ManagementFabInset, bottom = ManagementFabInset),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = contentDescription,
            modifier = Modifier.size(ManagementFabIconSize),
        )
    }
}

@Composable
internal fun SessionManagementTopRow(
    modifier: Modifier = Modifier,
    title: String,
    onOpenMenu: () -> Unit,
    onRefresh: (() -> Unit)?,
    actionIcon: ImageVector = Icons.Default.Refresh,
    actionContentDescription: String = ManagementTexts.Scaffold.REFRESH.get(),
) {
    Surface(
        modifier =
            modifier
                .zIndex(1f),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = ManagementSurfaceElevation,
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(ManagementTopBarHeight)
                        .padding(
                            start = ManagementTopBarHorizontalInset,
                            end = ManagementTopBarHorizontalInset,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.width(ManagementTopBarSideWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = onOpenMenu,
                        modifier = Modifier.size(ManagementTopBarActionSize),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = ManagementTexts.Scaffold.OPEN_MENU.get(),
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box(
                    modifier = Modifier.width(ManagementTopBarSideWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    if (onRefresh != null) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(ManagementTopBarActionSize),
                        ) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = actionContentDescription,
                            )
                        }
                    }
                }
            }
            AppDivider()
        }
    }
}

@Composable
internal fun SessionManagementDrawer(
    sessionData: SessionData,
    selectedSection: SessionManagementSection,
    onDismiss: () -> Unit,
    onSectionSelected: (SessionManagementSection) -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        onClick = onDismiss,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ),
        )

        Surface(
            modifier =
                Modifier
                    .width(ManagementDrawerWidth)
                    .fillMaxHeight(),
            shape =
                RoundedCornerShape(
                    topEnd = ManagementDrawerEdgeCornerRadius,
                    bottomEnd = ManagementDrawerEdgeCornerRadius,
                ),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(
                    horizontal = ManagementDrawerPadding,
                    vertical = ManagementDrawerPadding,
                ),
            ) {
                Surface(
                    shape = SessionManagementCardShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 1.dp,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = ManagementDrawerHeaderPadding,
                                    vertical = ManagementDrawerHeaderPadding,
                                ),
                        verticalArrangement = Arrangement.spacedBy(ManagementDrawerSectionSpacing),
                    ) {
                        Text(
                            text = ManagementTexts.Scaffold.CURRENT_DEVICE.get(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = sessionData.name.ifBlank { sessionData.primaryConnectionEndpointForDisplay() },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = sessionData.primaryConnectionEndpointForDisplay(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(top = ManagementDrawerSectionSpacing, start = 2.dp, end = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(ManagementDrawerItemSpacing),
                ) {
                    SessionManagementSection.entries.forEach { section ->
                        SessionManagementDrawerItem(
                            section = section,
                            selected = selectedSection == section,
                            onClick = { onSectionSelected(section) },
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(ManagementDrawerItemSpacing),
                ) {
                    SessionManagementDrawerActionItem(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        title = ManagementTexts.Scaffold.EXIT.get(),
                        onClick = onExit,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SessionManagementUtilityList(
    snapshot: DeviceDashboardSnapshot,
    onAction: (UtilityAction) -> Unit,
) {
    val items =
        utilityItems(
            snapshot = snapshot,
            accent = MaterialTheme.colorScheme.primary,
        )
    Surface(
        shape = SessionManagementCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            items.forEachIndexed { index, item ->
                SessionManagementUtilityCard(
                    item = item,
                    onClick = { onAction(item.action) },
                )
                if (index != items.lastIndex) {
                    AppDivider(modifier = Modifier.padding(start = 64.dp))
                }
            }
        }
    }
}

@Composable
internal fun SessionManagementUtilityBadge(
    text: String,
    accent: Color = Color.Unspecified,
    available: Boolean = true,
) {
    val resolvedAccent =
        if (accent == Color.Unspecified) {
            MaterialTheme.colorScheme.outlineVariant
        } else {
            accent
        }
    StatusBadge(
        text = text,
        contentColor =
            if (available) {
                resolvedAccent
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        containerColor =
            if (available) {
                resolvedAccent.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.background
            },
        tonalElevation = 0.dp,
    )
}

@Composable
private fun SessionManagementUtilityCard(
    item: UtilityCardItem,
    onClick: () -> Unit,
) {
    val iconTint =
        if (item.available) {
            item.accent.copy(alpha = 0.86f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = item.available, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(12.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color =
                    if (item.available) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (item.available) 1f else 0.72f),
            )
        }

        when {
            item.statusChecked != null -> {
                SessionManagementUtilityCheckBadge(
                    checked = item.statusChecked,
                    accent = item.accent,
                )
            }

            item.statusText != null -> {
                SessionManagementUtilityBadge(
                    text = item.statusText,
                    accent = item.accent,
                    available = item.available,
                )
            }
        }
    }
}

@Composable
private fun SessionManagementUtilityCheckBadge(
    checked: Boolean,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.Check else Icons.Default.Close,
            contentDescription = if (checked) ManagementTexts.Scaffold.ON.get() else ManagementTexts.Scaffold.OFF.get(),
            tint = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .size(16.dp),
        )
    }
}

@Composable
private fun SessionManagementDrawerItem(
    section: SessionManagementSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Surface(
        shape = SessionManagementControlShape,
        color = containerColor,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = contentColor,
            )
            Text(
                text = section.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SessionManagementDrawerActionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = SessionManagementControlShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

internal enum class SessionManagementSection(
    private val text: com.screen.remote.android.core.i18n.TextPair,
    val icon: ImageVector,
    val supportsRefresh: Boolean = false,
) {
    DeviceInfo(ManagementTexts.General.DEVICE_INFO_SECTION, Icons.Default.Info, supportsRefresh = true),
    Utility(ManagementTexts.General.UTILITIES_SECTION, Icons.Default.Build, supportsRefresh = true),
    Files(ManagementTexts.General.FILES_SECTION, Icons.Default.Folder, supportsRefresh = true),
    Apps(ManagementTexts.General.APPS_SECTION, Icons.Default.Apps, supportsRefresh = true),
    Process(ManagementTexts.General.PROCESSES_SECTION, Icons.Default.Usb, supportsRefresh = true),
    PortForward(ManagementTexts.General.PORT_FORWARD_SECTION, Icons.Default.SwapHoriz, supportsRefresh = true),
    Command(ManagementTexts.General.COMMANDS_SECTION, Icons.Default.Code),
    ;

    val title: String
        get() = text.get()
}

internal enum class UtilityAction {
    FixedPort,
    Screenshot,
    AdvancedReboot,
    ActivateApp,
    ModifyDpi,
    ModifyResolution,
    AnimationScale,
    SleepStandby,
}

private data class UtilityCardItem(
    val action: UtilityAction,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val available: Boolean = true,
    val statusText: String? = null,
    val statusChecked: Boolean? = null,
)

private fun utilityItems(
    snapshot: DeviceDashboardSnapshot,
    accent: Color,
): List<UtilityCardItem> {
    return listOf(
        UtilityCardItem(
            action = UtilityAction.FixedPort,
            title = ManagementTexts.Scaffold.FIXED_WIRELESS_DEBUGGING_PORT.get(),
            subtitle = ManagementTexts.Scaffold.SET_ADB_TCP_PORT_DEVICE_WILL_RESTART_ADB.get(),
            icon = Icons.Default.Wifi,
            accent = accent,
        ),
        UtilityCardItem(
            action = UtilityAction.Screenshot,
            title = ManagementTexts.Scaffold.SCREENSHOT.get(),
            subtitle = ManagementTexts.Scaffold.SAVE_SCREENSHOT_LOCAL_CACHE_THEN_PREVIEW_SAVE_IT.get(),
            icon = Icons.Default.PhotoCamera,
            accent = accent,
        ),
        UtilityCardItem(
            action = UtilityAction.AdvancedReboot,
            title = ManagementTexts.Scaffold.ADVANCED_REBOOT.get(),
            subtitle = ManagementTexts.Scaffold.RESTART_POWER_OFF_RECOVERY_FASTBOOT.get(),
            icon = Icons.Default.RestartAlt,
            accent = accent,
        ),
        UtilityCardItem(
            action = UtilityAction.ActivateApp,
            title = ManagementTexts.Scaffold.ACTIVATE_APP.get(),
            subtitle = ManagementTexts.Scaffold.LOAD_LIST_SUPPORTED_ACTIVATION_APPS.get(),
            icon = Icons.Default.VerifiedUser,
            accent = accent,
        ),
        UtilityCardItem(
            action = UtilityAction.ModifyDpi,
            title = ManagementTexts.Scaffold.CHANGE_DPI.get(),
            subtitle =
                snapshot.currentDpiLabel?.let {
                    ManagementTexts.Scaffold.CURRENT_TAP_ENTER_NEW_VALUE.format(it)
                } ?: ManagementTexts.Scaffold.ENTER_NEW_SCREEN_DENSITY.get(),
            icon = Icons.Default.CropFree,
            accent = accent,
        ),
        UtilityCardItem(
            action = UtilityAction.ModifyResolution,
            title = ManagementTexts.Scaffold.CHANGE_RESOLUTION.get(),
            subtitle = snapshot.resolution.ifBlank { ManagementTexts.Scaffold.ADJUST_SCREEN_RESOLUTION.get() },
            icon = Icons.Default.CropFree,
            accent = accent,
        ),
        UtilityCardItem(
            action = UtilityAction.AnimationScale,
            title = ManagementTexts.Scaffold.ANIMATION_SCALE.get(),
            subtitle = ManagementTexts.Scaffold.SET_WINDOW_TRANSITION_DURATION_SCALES_TOGETHER.get(),
            icon = Icons.Default.Tune,
            accent = accent,
        ),
        UtilityCardItem(
            action = UtilityAction.SleepStandby,
            title = ManagementTexts.Scaffold.SCREEN_STANDBY.get(),
            subtitle = ManagementTexts.Scaffold.PROVIDE_SLEEP_WAKE_ACTIONS.get(),
            icon = Icons.Default.Lightbulb,
            accent = accent,
        ),
    )
}
