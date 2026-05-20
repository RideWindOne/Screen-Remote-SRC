package com.screen.remote.android.feature.session.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.common.AppColors
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.core.designsystem.component.IOSAlertDialog as AlertDialog

private val AppsPanelCornerRadius = 16.dp
private val AppsRowSpacing = 10.dp
private val AppsRowVerticalPadding = 7.dp
private val AppsRowMetaSpacing = 2.dp
private val AppsBadgeSpacing = 6.dp
private val AppsAvatarSize = 40.dp
private val AppsAvatarImageSize = 26.dp
private val AppsAvatarFallbackIconSize = 20.dp
private val AppsDisabledBadgeAccent = Color(0xFFFF9F0A)
private val AppsSystemBadgeAccent = AppColors.iOSBlue
private val AppsSystemAvatarAccent = AppColors.iOSBlue
private val AppsUserAvatarAccent = Color(0xFF34C759)
private val AppsInfoCardHorizontalPadding = 14.dp
private val AppsInfoCardVerticalPadding = 16.dp
private val AppsOptionsMenuWidth = 264.dp
private val AppsOptionsMenuOffset = DpOffset(x = (-6).dp, y = (-56).dp)
private val AppsActionRowCornerRadius = 12.dp
private val AppsActionRowHorizontalPadding = 8.dp
private val AppsActionRowVerticalPadding = 12.dp
private val AppsSectionTitleHorizontalPadding = 16.dp
private val AppsSectionTitleVerticalPadding = 8.dp
private val AppsDetailLabelWidth = 92.dp
private val AppsDetailRowMinHeight = 38.dp
private val AppsDetailDividerInset = 110.dp

@Composable
internal fun SessionManagementAppRow(
    entry: AppInventoryEntry,
    packageNameOnlyMode: Boolean,
    presentationVersion: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val presentation by produceState(
        initialValue =
            RemoteAppPresentation(
                title = entry.appTitle,
                icon = SessionManagementAppCache.cachedIcon(entry.packageName),
            ),
        entry.packageName,
        entry.apkPath,
        presentationVersion,
        packageNameOnlyMode,
    ) {
        value = loadCachedAppPresentation(context, entry, packageNameOnlyMode)
    }
    val appTitle = presentation.title
    val iconBitmap = presentation.icon

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = AppsRowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(AppsRowSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionManagementAppAvatar(
            packageName = entry.packageName,
            appTitle = appTitle,
            isSystemApp = entry.isSystemApp,
            iconBitmap = iconBitmap,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppsRowMetaSpacing),
        ) {
            Text(
                text = appTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(AppsBadgeSpacing),
        ) {
            if (!entry.isEnabled) {
                SessionManagementUtilityBadge(
                    text = ManagementTexts.text("已禁用", "Disabled"),
                    accent = AppsDisabledBadgeAccent,
                )
            }
            if (entry.isSystemApp) {
                SessionManagementUtilityBadge(
                    text = ManagementTexts.text("系统", "System"),
                    accent = AppsSystemBadgeAccent,
                )
            }
        }
    }
}

@Composable
internal fun SessionManagementAppAvatar(
    packageName: String,
    appTitle: String,
    isSystemApp: Boolean,
    iconBitmap: Bitmap?,
) {
    val accent = if (isSystemApp) AppsSystemAvatarAccent else AppsUserAvatarAccent
    val initial = appTitle.firstOrNull()?.uppercaseChar()

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.16f),
    ) {
        Box(
            modifier = Modifier.size(AppsAvatarSize),
            contentAlignment = Alignment.Center,
        ) {
            when {
                iconBitmap != null -> {
                    Image(
                        bitmap = iconBitmap.asImageBitmap(),
                        contentDescription = packageName,
                        modifier = Modifier.size(AppsAvatarImageSize),
                    )
                }

                initial != null && initial.code > 32 -> {
                    Text(
                        text = initial.toString(),
                        color = accent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(AppsAvatarFallbackIconSize),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SessionManagementAppActionDialog(
    entry: AppInventoryEntry,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onLaunch: () -> Unit,
    onToggleEnabled: () -> Unit,
    onUninstall: () -> Unit,
    onClearData: () -> Unit,
    onDownloadApk: () -> Unit,
) {
    val actionLabel = if (entry.isEnabled) ManagementTexts.text("停用", "Disable") else ManagementTexts.text("启用", "Enable")
    val iconBitmap = SessionManagementAppCache.cachedIcon(entry.packageName)
    val appTitle = SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle)

    AlertDialog(
        onDismissRequest = onDismiss,
        widthRatio = 0.9f,
        title = {
            SessionManagementAppDialogHeader(
                appTitle = appTitle,
                packageName = entry.packageName,
                isSystemApp = entry.isSystemApp,
                isEnabled = entry.isEnabled,
                iconBitmap = iconBitmap,
            )
        },
        text = {
            SessionManagementDialogCard {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SessionManagementActionRow(icon = Icons.Default.Info, label = ManagementTexts.text("应用详情", "App details"), onClick = onDetails)
                    SessionManagementActionRow(icon = Icons.Default.PlayArrow, label = ManagementTexts.text("在设备上启动", "Launch on device"), onClick = onLaunch)
                    SessionManagementActionRow(icon = Icons.Default.VerifiedUser, label = actionLabel, onClick = onToggleEnabled)
                    SessionManagementActionRow(icon = Icons.Default.DeleteOutline, label = ManagementTexts.text("卸载", "Uninstall"), onClick = onUninstall)
                    SessionManagementActionRow(icon = Icons.Default.Build, label = ManagementTexts.text("清除数据", "Clear data"), onClick = onClearData)
                    SessionManagementActionRow(icon = Icons.Default.Download, label = ManagementTexts.text("下载安装包", "Export APK"), onClick = onDownloadApk)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(ManagementTexts.text("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
internal fun SessionManagementAppOptionsMenu(
    expanded: Boolean,
    selectedFilters: Set<AppListFilter>,
    selectedSort: AppListSort,
    packageNameOnlyMode: Boolean,
    onDismiss: () -> Unit,
    onRefreshList: () -> Unit,
    onSortSelected: (AppListSort) -> Unit,
    onPackageNameOnlyModeChanged: (Boolean) -> Unit,
    onToggleFilter: (AppListFilter) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(AppsOptionsMenuWidth),
        offset = AppsOptionsMenuOffset,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DropdownMenuItem(
            text = { Text(ManagementTexts.text("刷新应用列表", "Refresh apps")) },
            leadingIcon = { Icon(imageVector = Icons.Default.Refresh, contentDescription = null) },
            onClick = onRefreshList,
        )
        HorizontalDivider()
        SessionManagementAppOptionsSectionTitle(ManagementTexts.text("排序方式", "Sort"))
        AppListSort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                leadingIcon = { Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                trailingIcon = {
                    if (selectedSort == option) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    }
                },
                onClick = { onSortSelected(option) },
            )
        }
        HorizontalDivider()
        SessionManagementAppOptionsSectionTitle(ManagementTexts.text("加载方式", "Loading"))
        DropdownMenuItem(
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(ManagementTexts.text("只加载包名", "Package names only"))
                    Text(
                        text = ManagementTexts.text("停止远程解析应用名和图标，仅使用已有缓存。", "Skip remote names and icons, use cache only."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingIcon = { Icon(imageVector = Icons.Default.Code, contentDescription = null) },
            trailingIcon = { Checkbox(checked = packageNameOnlyMode, onCheckedChange = null) },
            onClick = { onPackageNameOnlyModeChanged(!packageNameOnlyMode) },
        )
        HorizontalDivider()
        SessionManagementAppOptionsSectionTitle(ManagementTexts.text("显示筛选", "Filters"))
        AppListFilter.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                leadingIcon = { Icon(imageVector = Icons.Default.FilterList, contentDescription = null) },
                trailingIcon = { Checkbox(checked = option in selectedFilters, onCheckedChange = null) },
                onClick = { onToggleFilter(option) },
            )
        }
    }
}

@Composable
internal fun SessionManagementAppDetailDialog(
    entry: AppInventoryEntry,
    onDismiss: () -> Unit,
) {
    val detail by produceState(
        initialValue = SessionManagementAppCache.cachedAppDetail(entry.packageName) ?: AppDetailSnapshot.loading(entry),
        key1 = entry.packageName,
    ) {
        value = loadAppDetailSnapshot(entry)
    }
    val iconBitmap = SessionManagementAppCache.cachedIcon(entry.packageName)

    AlertDialog(
        onDismissRequest = onDismiss,
        widthRatio = 0.92f,
        title = {
            SessionManagementAppDialogHeader(
                appTitle = detail.appTitle,
                packageName = detail.packageName,
                isSystemApp = detail.isSystemApp,
                isEnabled = entry.isEnabled,
                iconBitmap = iconBitmap,
                packageNameMaxLines = 1,
            )
        },
        text = { SessionManagementAppDetailContent(detail) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(ManagementTexts.text("确定", "OK"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
internal fun SessionManagementAppUninstallDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    val detail by produceState(
        initialValue = SessionManagementAppCache.cachedAppDetail(packageName) ?: AppDetailSnapshot.loading(packageName),
        key1 = packageName,
    ) {
        val appEntry =
            SessionManagementAppCache.snapshot()?.apps?.firstOrNull { it.packageName == packageName }
                ?: AppInventoryEntry(
                    packageName = packageName,
                    appTitle = SessionManagementAppCache.appTitle(packageName, guessAppTitle(packageName)),
                    isSystemApp = false,
                    apkPath = "",
                    isEnabled = true,
                )
        value = loadAppDetailSnapshot(appEntry)
    }
    var keepData by remember(packageName) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (detail.isSystemApp) ManagementTexts.text("该应用为系统应用，请谨慎卸载", "This is a system app. Uninstall carefully.") else ManagementTexts.text("确认卸载 $packageName", "Uninstall $packageName")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { keepData = !keepData }
                            .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SessionManagementUtilityBadge(
                        text = if (keepData) ManagementTexts.text("保留", "Keep data") else ManagementTexts.text("不保留", "Remove data"),
                        accent = Color(0xFF7BA7FF),
                        available = keepData,
                    )
                    Text(ManagementTexts.text("尝试保留应用数据", "Try to keep app data"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(keepData) }) {
                Text(ManagementTexts.text("卸载", "Uninstall"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(ManagementTexts.text("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
internal fun SessionManagementActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(
                    horizontal = AppsActionRowHorizontalPadding,
                    vertical = AppsActionRowVerticalPadding,
                ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun SessionManagementAppDialogHeader(
    appTitle: String,
    packageName: String,
    isSystemApp: Boolean,
    isEnabled: Boolean,
    iconBitmap: Bitmap?,
    packageNameMaxLines: Int = 2,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionManagementAppAvatar(
            packageName = packageName,
            appTitle = appTitle,
            isSystemApp = isSystemApp,
            iconBitmap = iconBitmap,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = appTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = packageNameMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppsBadgeSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isEnabled) {
                    SessionManagementUtilityBadge(
                        text = ManagementTexts.text("已禁用", "Disabled"),
                        accent = AppsDisabledBadgeAccent,
                    )
                }
                if (isSystemApp) {
                    SessionManagementUtilityBadge(
                        text = ManagementTexts.text("系统", "System"),
                        accent = AppsSystemBadgeAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionManagementInfoLoadingCard(labels: List<String>) {
    SessionManagementDialogCard {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppsInfoCardHorizontalPadding,
                        vertical = AppsInfoCardVerticalPadding,
                    ),
            verticalArrangement = Arrangement.Top,
        ) {
            labels.forEachIndexed { index, label ->
                SessionManagementInfoPlaceholderRow(
                    label = label,
                    labelWidth = AppsDetailLabelWidth,
                    rowMinHeight = AppsDetailRowMinHeight,
                )
                if (index != labels.lastIndex) {
                    AppDivider(modifier = Modifier.padding(start = AppsDetailDividerInset))
                }
            }
        }
    }
}

@Composable
private fun SessionManagementAppDetailContent(detail: AppDetailSnapshot) {
    when {
        detail.isLoading -> {
            SessionManagementInfoLoadingCard(
                labels =
                    listOf(
                        ManagementTexts.text("包名", "Package"),
                        ManagementTexts.text("安装包大小", "APK size"),
                        ManagementTexts.text("版本名", "Version"),
                        ManagementTexts.text("系统应用", "System app"),
                        ManagementTexts.text("兼容SDK版本", "Min SDK"),
                        ManagementTexts.text("目标SDK版本", "Target SDK"),
                        ManagementTexts.text("首次安装时间", "First installed"),
                        ManagementTexts.text("上次更新时间", "Last updated"),
                    ),
            )
        }

        detail.errorMessage != null -> {
            SessionManagementNoteCard(
                title = ManagementTexts.text("应用详情读取失败", "Couldn't load app details"),
                text = detail.errorMessage,
            )
        }

        else -> {
            val detailItems =
                listOf(
                    AppDetailItem(ManagementTexts.text("包名", "Package"), detail.packageName, useSmallText = true),
                    AppDetailItem(ManagementTexts.text("安装包大小", "APK size"), detail.apkSize),
                    AppDetailItem(ManagementTexts.text("版本名", "Version"), detail.versionName),
                    AppDetailItem(ManagementTexts.text("系统应用", "System app"), if (detail.isSystemApp) ManagementTexts.text("是", "Yes") else ManagementTexts.text("否", "No")),
                    AppDetailItem(ManagementTexts.text("兼容SDK版本", "Min SDK"), detail.minSdk),
                    AppDetailItem(ManagementTexts.text("目标SDK版本", "Target SDK"), detail.targetSdk),
                    AppDetailItem(ManagementTexts.text("首次安装时间", "First installed"), detail.firstInstallTime, useSmallText = true),
                    AppDetailItem(ManagementTexts.text("上次更新时间", "Last updated"), detail.lastUpdateTime, useSmallText = true),
                )
            SessionManagementDialogCard {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = AppsInfoCardHorizontalPadding,
                                vertical = AppsInfoCardVerticalPadding,
                            ),
                    verticalArrangement = Arrangement.Top,
                ) {
                    detailItems.forEachIndexed { index, item ->
                        SessionManagementInfoRow(
                            label = item.label,
                            value = item.value,
                            labelWidth = AppsDetailLabelWidth,
                            rowMinHeight = AppsDetailRowMinHeight,
                            valueTextStyle =
                                if (item.useSmallText) {
                                    MaterialTheme.typography.bodySmall
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                            valueMaxLines = 1,
                        )
                        if (index != detailItems.lastIndex) {
                            AppDivider(modifier = Modifier.padding(start = AppsDetailDividerInset))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionManagementAppOptionsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier.padding(
                horizontal = AppsSectionTitleHorizontalPadding,
                vertical = AppsSectionTitleVerticalPadding,
            ),
    )
}

private data class AppDetailItem(
    val label: String,
    val value: String,
    val useSmallText: Boolean = false,
)
