package com.screen.remote.android.feature.session.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.core.i18n.ManagementTexts

private val AppsRowSpacing = 10.dp
private val AppsRowVerticalPadding = 4.dp
private val AppsRowMetaSpacing = 2.dp
private val AppsBadgeSpacing = 6.dp
private val AppsAvatarSize = 40.dp
private val AppsAvatarFallbackIconSize = 20.dp
private val AppsInfoCardHorizontalPadding = 14.dp
private val AppsInfoCardVerticalPadding = 16.dp
private val AppsOptionsMenuOffset = DpOffset(x = (-6).dp, y = (-56).dp)
private val AppsOptionsMenuRowHeight = 40.dp
private val AppsOptionsMenuIconSize = 20.dp
private val AppsActionRowHorizontalPadding = 8.dp
private val AppsActionRowVerticalPadding = 12.dp
private val AppsDetailLabelWidth = 92.dp
private val AppsDetailRowMinHeight = 38.dp
private val AppsDetailDividerInset = 110.dp
private val AppsDetailContentHeight = 344.dp
private val AppsSystemBadgeAccent
    @Composable
    get() = MaterialTheme.colorScheme.primary
private val AppsSystemAvatarAccent
    @Composable
    get() = MaterialTheme.colorScheme.primary
private val AppsDisabledBadgeAccent
    @Composable
    get() = MaterialTheme.colorScheme.error
private val AppsUserAvatarAccent
    @Composable
    get() = MaterialTheme.colorScheme.primary

@Composable
internal fun SessionManagementAppPlaceholderRow() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = AppsRowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(AppsRowSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(AppsAvatarSize)
                    .background(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(999.dp),
                    ),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.56f)
                        .height(18.dp)
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(999.dp),
                        ),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp)
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(999.dp),
                        ),
            )
        }
        Box(
            modifier =
                Modifier
                    .width(46.dp)
                    .height(18.dp)
                    .background(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(999.dp),
                    ),
        )
    }
}

@Composable
internal fun SessionManagementAppRow(
    entry: AppInventoryEntry,
    packageNameOnlyMode: Boolean,
    presentationVersion: Int,
    isRunning: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val appTitle =
        remember(entry.packageName, entry.appTitle, packageNameOnlyMode, presentationVersion) {
            resolveAppListTitle(entry, packageNameOnlyMode)
        }
    val iconBitmap =
        remember(entry.packageName, presentationVersion) {
            SessionManagementAppCache.cachedIcon(entry.packageName)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
            if (selectionMode) {
                SessionManagementUtilityBadge(
                    text = if (selected) "✓" else "○",
                    accent = MaterialTheme.colorScheme.primary,
                    available = selected,
                )
            }
            if (isRunning) {
                SessionManagementUtilityBadge(
                    text = ManagementTexts.Apps.RUNNING.get(),
                    accent = MaterialTheme.colorScheme.primary,
                )
            }
            if (!entry.isEnabled) {
                SessionManagementUtilityBadge(
                    text = ManagementTexts.Apps.DISABLED.get(),
                    accent = AppsDisabledBadgeAccent,
                )
            }
            if (entry.isSystemApp) {
                SessionManagementUtilityBadge(
                    text = ManagementTexts.Apps.SYSTEM.get(),
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
                        modifier = Modifier.size(AppsAvatarSize),
                        contentScale = ContentScale.Fit,
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
    isRunning: Boolean,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onLaunch: () -> Unit,
    onToggleEnabled: () -> Unit,
    onUninstall: () -> Unit,
    onClearData: () -> Unit,
    onForceStop: () -> Unit,
    onDownloadApk: () -> Unit,
) {
    val actionLabel = if (entry.isEnabled) ManagementTexts.Apps.DISABLE.get() else ManagementTexts.Apps.ENABLE.get()
    val iconBitmap = SessionManagementAppCache.cachedIcon(entry.packageName)
    val appTitle = SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle)

    SessionManagementCenteredDialog(
        title = appTitle,
        onDismiss = onDismiss,
        widthRatio = SessionManagementContentWidthFraction,
        leftButtonText = ManagementTexts.Apps.CANCEL.get(),
    ) {
        SessionManagementAppDialogHeader(
            appTitle = appTitle,
            packageName = entry.packageName,
            isSystemApp = entry.isSystemApp,
            isEnabled = entry.isEnabled,
            isRunning = isRunning,
            iconBitmap = iconBitmap,
        )
        SessionManagementDialogCard {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SessionManagementActionRow(
                    icon = Icons.Default.Info,
                    label = ManagementTexts.Apps.APP_DETAILS.get(),
                    onClick = onDetails
                )
                SessionManagementActionRow(
                    icon = Icons.Default.PlayArrow,
                    label = ManagementTexts.Apps.LAUNCH_DEVICE.get(),
                    onClick = onLaunch
                )
                SessionManagementActionRow(
                    icon = Icons.Default.VerifiedUser,
                    label = actionLabel,
                    onClick = onToggleEnabled
                )
                SessionManagementActionRow(
                    icon = Icons.Default.DeleteOutline,
                    label = ManagementTexts.Apps.UNINSTALL.get(),
                    onClick = onUninstall
                )
                SessionManagementActionRow(
                    icon = Icons.Default.Build,
                    label = ManagementTexts.Apps.CLEAR_DATA.get(),
                    onClick = onClearData
                )
                SessionManagementActionRow(
                    icon = Icons.Default.PowerSettingsNew,
                    label = ManagementTexts.Apps.FORCE_STOP.get(),
                    enabled = isRunning,
                    onClick = onForceStop
                )
                SessionManagementActionRow(
                    icon = Icons.Default.Download,
                    label = ManagementTexts.Apps.EXPORT_APK.get(),
                    onClick = onDownloadApk
                )
            }
        }
    }
}

@Composable
internal fun SessionManagementAppOptionsMenu(
    expanded: Boolean,
    selectedFilters: Set<AppListFilter>,
    selectedSort: AppListSort,
    sortAscending: Boolean,
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
        offset = AppsOptionsMenuOffset,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DropdownMenuItem(
            modifier = Modifier.height(AppsOptionsMenuRowHeight),
            text = { Text(ManagementTexts.Apps.REFRESH_APPS.get()) },
            onClick = onRefreshList,
        )
        HorizontalDivider()
        AppListSort.entries.forEach { option ->
            DropdownMenuItem(
                modifier = Modifier.height(AppsOptionsMenuRowHeight),
                text = { Text(option.label) },
                trailingIcon = {
                    if (selectedSort == option) {
                        Icon(
                            imageVector =
                                if (sortAscending) {
                                    Icons.Default.ArrowUpward
                                } else {
                                    Icons.Default.ArrowDownward
                                },
                            contentDescription = null,
                            modifier = Modifier.size(AppsOptionsMenuIconSize),
                        )
                    }
                },
                onClick = { onSortSelected(option) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            modifier = Modifier.height(AppsOptionsMenuRowHeight),
            text = { Text(ManagementTexts.Apps.PACKAGE_NAMES_ONLY.get()) },
            trailingIcon = {
                if (packageNameOnlyMode) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(AppsOptionsMenuIconSize),
                    )
                }
            },
            onClick = { onPackageNameOnlyModeChanged(!packageNameOnlyMode) },
        )
        HorizontalDivider()
        AppListFilter.entries.forEach { option ->
            DropdownMenuItem(
                modifier = Modifier.height(AppsOptionsMenuRowHeight),
                text = { Text(option.label) },
                trailingIcon = {
                    if (option in selectedFilters) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(AppsOptionsMenuIconSize),
                        )
                    }
                },
                onClick = { onToggleFilter(option) },
            )
        }
    }
}

@Composable
internal fun SessionManagementAppDetailDialog(
    entry: AppInventoryEntry,
    isRunning: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val detail by produceState(
        initialValue = SessionManagementAppCache.cachedAppDetail(entry.packageName) ?: AppDetailSnapshot.loading(entry),
        key1 = entry.packageName,
    ) {
        value = loadAppDetailSnapshot(context, entry)
    }
    val iconBitmap = SessionManagementAppCache.cachedIcon(entry.packageName)

    SessionManagementCenteredDialog(
        title = ManagementTexts.Apps.APP_DETAILS.get(),
        onDismiss = onDismiss,
        widthRatio = SessionManagementContentWidthFraction,
    ) {
        SessionManagementAppDialogHeader(
            appTitle = detail.appTitle,
            packageName = detail.packageName,
            isSystemApp = detail.isSystemApp,
            isEnabled = entry.isEnabled,
            isRunning = isRunning,
            iconBitmap = iconBitmap,
            showPackageName = false,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppsDetailContentHeight),
        ) {
            SessionManagementAppDetailContent(detail, isRunning)
        }
    }
}

@Composable
internal fun SessionManagementAppUninstallDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    val context = LocalContext.current
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
        value = loadAppDetailSnapshot(context, appEntry)
    }
    var keepData by remember(packageName) { mutableStateOf(false) }

    SessionManagementCenteredDialog(
        title =
            if (detail.isSystemApp) {
                ManagementTexts.Apps.SYSTEM_APP_UNINSTALL_CAREFULLY.get()
            } else {
                ManagementTexts.Apps.CONFIRM_UNINSTALL_PACKAGE.format(packageName)
            },
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Apps.CANCEL.get(),
        rightButtonText = ManagementTexts.Apps.UNINSTALL.get(),
        onRightButtonClick = { onConfirm(keepData) },
    ) {
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
                text = if (keepData) ManagementTexts.Apps.KEEP_DATA.get() else ManagementTexts.Apps.REMOVE_DATA.get(),
                accent = MaterialTheme.colorScheme.primary,
                available = keepData,
            )
            Text(ManagementTexts.Apps.TRY_KEEP_APP_DATA.get())
        }
    }
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
            tint =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
        )
    }
}

@Composable
private fun SessionManagementAppDialogHeader(
    appTitle: String,
    packageName: String,
    isSystemApp: Boolean,
    isEnabled: Boolean,
    isRunning: Boolean = false,
    iconBitmap: Bitmap?,
    packageNameMaxLines: Int = 2,
    showPackageName: Boolean = true,
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
            if (showPackageName) {
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = packageNameMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppsBadgeSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isRunning) {
                    SessionManagementUtilityBadge(
                        text = ManagementTexts.Apps.RUNNING.get(),
                        accent = MaterialTheme.colorScheme.primary,
                    )
                }
                if (!isEnabled) {
                    SessionManagementUtilityBadge(
                        text = ManagementTexts.Apps.DISABLED.get(),
                        accent = AppsDisabledBadgeAccent,
                    )
                }
                if (isSystemApp) {
                    SessionManagementUtilityBadge(
                        text = ManagementTexts.Apps.SYSTEM.get(),
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
                    .verticalScroll(rememberScrollState())
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
private fun SessionManagementAppDetailContent(
    detail: AppDetailSnapshot,
    isRunning: Boolean,
) {
    val context = LocalContext.current
    when {
        detail.isLoading -> {
            SessionManagementInfoLoadingCard(
                labels =
                    listOf(
                        ManagementTexts.Apps.PACKAGE.get(),
                        ManagementTexts.Apps.APK_SIZE.get(),
                        ManagementTexts.Apps.VERSION.get(),
                        ManagementTexts.Apps.VERSION_CODE.get(),
                        ManagementTexts.Apps.NATIVE_ABIS.get(),
                        ManagementTexts.Apps.UID.get(),
                        ManagementTexts.Apps.RUNNING_STATE.get(),
                        ManagementTexts.Apps.ENABLED_STATE.get(),
                        ManagementTexts.Apps.SYSTEM_APP.get(),
                        ManagementTexts.Apps.MIN_SDK.get(),
                        ManagementTexts.Apps.TARGET_SDK.get(),
                        ManagementTexts.Apps.APK_PATH.get(),
                        ManagementTexts.Apps.FIRST_INSTALLED.get(),
                        ManagementTexts.Apps.LAST_UPDATED.get(),
                    ),
            )
        }

        detail.errorMessage != null -> {
            SessionManagementNoteCard(
                title = ManagementTexts.Apps.COULDN_T_LOAD_APP_DETAILS.get(),
                text = detail.errorMessage,
            )
        }

        else -> {
            val detailItems =
                listOf(
                    AppDetailItem(ManagementTexts.Apps.PACKAGE.get(), detail.packageName, useSmallText = true),
                    AppDetailItem(ManagementTexts.Apps.APK_SIZE.get(), detail.apkSize, fieldName = "apkSizeBytes"),
                    AppDetailItem(ManagementTexts.Apps.VERSION.get(), detail.versionName, fieldName = "versionName"),
                    AppDetailItem(
                        ManagementTexts.Apps.VERSION_CODE.get(),
                        detail.versionCode,
                        fieldName = "versionCode"
                    ),
                    AppDetailItem(ManagementTexts.Apps.NATIVE_ABIS.get(), detail.nativeAbis, fieldName = "nativeAbis"),
                    AppDetailItem(ManagementTexts.Apps.UID.get(), detail.uid, fieldName = "uid"),
                    AppDetailItem(
                        ManagementTexts.Apps.RUNNING_STATE.get(),
                        if (isRunning) ManagementTexts.Apps.RUNNING.get() else ManagementTexts.Apps.NOT_RUNNING.get(),
                    ),
                    AppDetailItem(
                        ManagementTexts.Apps.ENABLED_STATE.get(),
                        if (detail.isEnabled) ManagementTexts.Apps.ENABLED.get() else ManagementTexts.Apps.DISABLED.get(),
                        fieldName = "enabled",
                    ),
                    AppDetailItem(
                        ManagementTexts.Apps.SYSTEM_APP.get(),
                        if (detail.isSystemApp) ManagementTexts.Apps.YES.get() else ManagementTexts.Apps.NO.get(),
                        fieldName = "systemApp"
                    ),
                    AppDetailItem(ManagementTexts.Apps.MIN_SDK.get(), detail.minSdk, fieldName = "minSdk"),
                    AppDetailItem(ManagementTexts.Apps.TARGET_SDK.get(), detail.targetSdk, fieldName = "targetSdk"),
                    AppDetailItem(
                        ManagementTexts.Apps.APK_PATH.get(),
                        detail.apkPath,
                        useSmallText = true,
                        fieldName = "sourceDir"
                    ),
                    AppDetailItem(
                        ManagementTexts.Apps.FIRST_INSTALLED.get(),
                        detail.firstInstallTime,
                        useSmallText = true,
                        fieldName = "firstInstallTime"
                    ),
                    AppDetailItem(
                        ManagementTexts.Apps.LAST_UPDATED.get(),
                        detail.lastUpdateTime,
                        useSmallText = true,
                        fieldName = "lastUpdateTime"
                    ),
                ).map { item ->
                    val issue = item.fieldName?.let(detail.fieldErrors::get)
                    val issueLabel =
                        when {
                            item.fieldName == null || !detail.fieldErrors.containsKey(item.fieldName) -> null
                            issue.isNullOrBlank() -> ManagementTexts.Apps.FIELD_NOT_REPORTED.get()
                            else -> ManagementTexts.Apps.FIELD_READ_FAILED.format(issue)
                        }
                    item.copy(
                        value =
                            when {
                                item.value.isNotBlank() && issueLabel != null -> "${item.value} · $issueLabel"
                                item.value.isNotBlank() -> item.value
                                issueLabel != null -> issueLabel
                                else -> ManagementTexts.Apps.NOT_AVAILABLE.get()
                            },
                    )
                }
            SessionManagementDialogCard {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = AppsInfoCardHorizontalPadding,
                                vertical = AppsInfoCardVerticalPadding,
                            ),
                    verticalArrangement = Arrangement.Top,
                ) {
                    detailItems.forEachIndexed { index, item ->
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (index == 0) {
                                            Modifier.combinedClickable(
                                                onClick = {},
                                                onLongClickLabel = ManagementTexts.Apps.COPY_PACKAGE_NAME.get(),
                                                onLongClick = {
                                                    val clipboard =
                                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(
                                                        ClipData.newPlainText(
                                                            ManagementTexts.Apps.APP_PACKAGE_NAME.get(),
                                                            detail.packageName,
                                                        ),
                                                    )
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            ManagementTexts.Apps.PACKAGE_NAME_COPIED.get(),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                },
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                        ) {
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
                        }
                        if (index != detailItems.lastIndex) {
                            AppDivider(modifier = Modifier.padding(start = AppsDetailDividerInset))
                        }
                    }
                }
            }
        }
    }
}

private data class AppDetailItem(
    val label: String,
    val value: String,
    val useSmallText: Boolean = false,
    val fieldName: String? = null,
)

@Composable
internal fun SessionManagementAppBatchDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onForceStop: () -> Unit,
    onUninstall: () -> Unit,
    onExport: () -> Unit,
) {
    SessionManagementCenteredDialog(
        title = ManagementTexts.Apps.SELECTED_COUNT.format(selectedCount),
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Apps.CANCEL.get(),
    ) {
        SessionManagementDialogCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                SessionManagementActionRow(
                    Icons.Default.VerifiedUser,
                    ManagementTexts.Apps.BATCH_ENABLE.get(),
                    onClick = onEnable
                )
                SessionManagementActionRow(
                    Icons.Default.VerifiedUser,
                    ManagementTexts.Apps.BATCH_DISABLE.get(),
                    onClick = onDisable
                )
                SessionManagementActionRow(
                    Icons.Default.PowerSettingsNew,
                    ManagementTexts.Apps.BATCH_FORCE_STOP.get(),
                    onClick = onForceStop
                )
                SessionManagementActionRow(
                    Icons.Default.DeleteOutline,
                    ManagementTexts.Apps.BATCH_UNINSTALL.get(),
                    onClick = onUninstall
                )
                SessionManagementActionRow(
                    Icons.Default.Download,
                    ManagementTexts.Apps.BATCH_EXPORT.get(),
                    onClick = onExport
                )
            }
        }
    }
}

@Composable
internal fun SessionManagementAppConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SessionManagementCenteredDialog(
        title = title,
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Apps.CANCEL.get(),
        rightButtonText = confirmText,
        onRightButtonClick = onConfirm,
    ) {
        SessionManagementDialogMessage(message)
    }
}
