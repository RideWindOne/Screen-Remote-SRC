package com.screen.remote.android.feature.session.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.screen.remote.android.core.designsystem.component.DialogContainer
import com.screen.remote.android.core.designsystem.component.DialogHeader
import com.screen.remote.android.core.i18n.ManagementTexts

private val ManagementDialogOptionCornerRadius = 14.dp
private val ManagementDialogCardCornerRadius = 20.dp
private val ManagementCenteredDialogContentPadding = 16.dp
private val ManagementCenteredDialogContentSpacing = 12.dp
private val ManagementDialogMessageMaxHeight = 240.dp
private val ManagementDialogOptionListMaxHeight = 360.dp
private val ManagementDialogOptionShadowPadding = 2.dp
private val ManagementActivationDialogContentMaxHeight = 320.dp

@Composable
internal fun SessionManagementDialogCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(ManagementDialogCardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        content()
    }
}

@Composable
internal fun SessionManagementDialogMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    SelectionContainer {
        Text(
            text = text,
            modifier =
                modifier
                    .fillMaxWidth()
                    .heightIn(max = ManagementDialogMessageMaxHeight)
                    .verticalScroll(scrollState),
        )
    }
}

@Composable
internal fun SessionManagementMessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    SessionManagementCenteredDialog(
        title = title,
        onDismiss = onDismiss,
    ) {
        SessionManagementDialogMessage(message)
    }
}

@Composable
internal fun SessionManagementCenteredDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    widthRatio: Float = 0.9f,
    maxHeightRatio: Float = 0.78f,
    contentPadding: Dp = ManagementCenteredDialogContentPadding,
    contentSpacing: Dp = ManagementCenteredDialogContentSpacing,
    leftButtonText: String? = ManagementTexts.Dialogs.CLOSE.get(),
    rightButtonText: String? = null,
    onRightButtonClick: (() -> Unit)? = null,
    rightButtonEnabled: Boolean = true,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = dismissOnBackPress,
                dismissOnClickOutside = dismissOnClickOutside,
            ),
    ) {
        DialogContainer(
            modifier = modifier.imePadding(),
            widthRatio = widthRatio,
            maxHeightRatio = maxHeightRatio,
            backgroundColor = MaterialTheme.colorScheme.background,
        ) {
            DialogHeader(
                title = title,
                onDismiss = onDismiss,
                showBackButton = false,
                leftButtonText = leftButtonText,
                rightButtonText = rightButtonText,
                onRightButtonClick = onRightButtonClick,
                rightButtonEnabled = rightButtonEnabled,
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
                content = content,
            )
        }
    }
}

@Composable
internal fun SessionManagementTextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue) }
    val trimmed = value.trim()

    SessionManagementCenteredDialog(
        title = title,
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Dialogs.CANCEL.get(),
        rightButtonText = confirmText,
        onRightButtonClick = { onConfirm(trimmed) },
        rightButtonEnabled = trimmed.isNotBlank(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SessionManagementAppAddDialog(
    onDismiss: () -> Unit,
    onPickApk: () -> Unit,
    onPickInstalledApp: () -> Unit,
) {
    SessionManagementCenteredDialog(
        title = ManagementTexts.Dialogs.INSTALL_APP.get(),
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Dialogs.CANCEL.get(),
    ) {
        SessionManagementDialogCard {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SessionManagementActionRow(
                    icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                    label = ManagementTexts.Dialogs.CHOOSE_APK_FILE.get(),
                    onClick = onPickApk,
                )
                SessionManagementActionRow(
                    icon = Icons.Default.Apps,
                    label = ManagementTexts.Dialogs.CHOOSE_INSTALLED_APP.get(),
                    onClick = onPickInstalledApp,
                )
            }
        }
    }
}

@Composable
internal fun SessionManagementLocalAppPickerDialog(
    apps: List<LocalInstalledApp>,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSelect: (LocalInstalledApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase()
    val visibleApps =
        remember(apps, normalizedQuery) {
            if (normalizedQuery.isBlank()) {
                apps
            } else {
                apps.filter { app ->
                    app.label.lowercase().contains(normalizedQuery) ||
                        app.packageName.lowercase().contains(normalizedQuery)
                }
            }
        }

    SessionManagementCenteredDialog(
        title = ManagementTexts.Dialogs.CHOOSE_INSTALLED_APP.get(),
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Dialogs.CANCEL.get(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(ManagementTexts.Apps.SEARCH_LOCAL_APPS.get()) },
            singleLine = true,
        )

        when {
            isLoading -> Text(ManagementTexts.Apps.LOADING_LOCAL_APPS.get())
            errorMessage != null -> Text(errorMessage, color = MaterialTheme.colorScheme.error)
            visibleApps.isEmpty() -> Text(ManagementTexts.Apps.NO_LOCAL_APPS.get())
            else -> {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = ManagementDialogOptionListMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(visibleApps, key = { app -> app.packageName }) { app ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(app) },
                            shape = RoundedCornerShape(ManagementDialogOptionCornerRadius),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                val metadata =
                                    buildList {
                                        if (app.isSystemApp) add(ManagementTexts.Apps.SYSTEM.get())
                                        if (app.apkPaths.size > 1) {
                                            add(ManagementTexts.Apps.SPLIT_APK_COUNT.format(app.apkPaths.size))
                                        }
                                    }.joinToString(" · ")
                                if (metadata.isNotEmpty()) {
                                    Text(
                                        text = metadata,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SessionManagementProgressDialog(
    title: String,
    message: String,
) {
    SessionManagementCenteredDialog(
        title = title,
        onDismiss = {},
        widthRatio = 0.72f,
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        leftButtonText = null,
    ) {
        SessionManagementDialogMessage(message)
    }
}

@Composable
internal fun SessionManagementExitConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SessionManagementCenteredDialog(
        title = ManagementTexts.Dialogs.LEAVE_MANAGEMENT_PAGE.get(),
        onDismiss = onDismiss,
        widthRatio = SessionManagementContentWidthFraction,
        contentPadding = 24.dp,
        leftButtonText = ManagementTexts.Dialogs.CANCEL.get(),
        rightButtonText = ManagementTexts.Dialogs.LEAVE.get(),
        onRightButtonClick = onConfirm,
    ) {
        SessionManagementDialogMessage(
            text = ManagementTexts.Dialogs.WILL_LEAVE_CURRENT_DEVICE_MANAGEMENT_PAGE.get(),
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
internal fun SessionManagementRebootDialog(
    onDismiss: () -> Unit,
    onAction: (RebootMode) -> Unit,
) {
    SessionManagementCenteredDialog(
        title = ManagementTexts.Dialogs.ADVANCED_REBOOT.get(),
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Dialogs.CANCEL.get(),
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = ManagementDialogOptionListMaxHeight),
            contentPadding = PaddingValues(vertical = ManagementDialogOptionShadowPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = RebootMode.entries,
                key = { it.name },
            ) { mode ->
                ManagementDialogOptionRow(onClick = { onAction(mode) }) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SessionManagementValueInputDialog(
    state: ManagementValueInputDialogState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(state) { mutableStateOf(state.initialValue) }
    val isValid = value.trim().toFloatOrNull() != null

    SessionManagementCenteredDialog(
        title = state.title,
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Dialogs.CANCEL.get(),
        rightButtonText = state.confirmText,
        onRightButtonClick = { onConfirm(value.trim()) },
        rightButtonEnabled = isValid,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(state.label) },
            placeholder = state.placeholder?.let { placeholder -> { Text(placeholder) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SessionManagementResolutionDialog(
    state: ResolutionDialogState,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var width by remember(state) { mutableStateOf(state.width) }
    var height by remember(state) { mutableStateOf(state.height) }
    val isValid = width.trim().toIntOrNull() != null && height.trim().toIntOrNull() != null

    SessionManagementCenteredDialog(
        title = ManagementTexts.Dialogs.CHANGE_RESOLUTION.get(),
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Dialogs.CANCEL.get(),
        rightButtonText = ManagementTexts.Dialogs.APPLY.get(),
        onRightButtonClick = { onConfirm(width.trim(), height.trim()) },
        rightButtonEnabled = isValid,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = width,
                onValueChange = { width = it },
                label = { Text(ManagementTexts.Dialogs.WIDTH.get()) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = height,
                onValueChange = { height = it },
                label = { Text(ManagementTexts.Dialogs.HEIGHT.get()) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun SessionManagementScreenshotDialog(
    state: ScreenshotPreviewState,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
) {
    SessionManagementCenteredDialog(
        title = ManagementTexts.Dialogs.SCREENSHOT_READY.get(),
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Dialogs.CANCEL.get(),
        rightButtonText = ManagementTexts.Dialogs.SAVE.get(),
        onRightButtonClick = onSave,
    ) {
        SessionManagementDialogMessage(
            ManagementTexts.Dialogs.SAVED_LOCAL_CACHE.format(state.file.absolutePath),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onOpen) {
                Text(ManagementTexts.Dialogs.OPEN.get())
            }
        }
    }
}

@Composable
internal fun SessionManagementAnimationDialog(
    state: AnimationScaleDialogState,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var windowScale by remember(state) { mutableStateOf(state.windowScale) }
    var transitionScale by remember(state) { mutableStateOf(state.transitionScale) }
    var durationScale by remember(state) { mutableStateOf(state.durationScale) }
    val isValid =
        windowScale.trim().toFloatOrNull() != null &&
            transitionScale.trim().toFloatOrNull() != null &&
            durationScale.trim().toFloatOrNull() != null

    SessionManagementCenteredDialog(
        title = ManagementTexts.Dialogs.ANIMATION_SCALE.get(),
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Dialogs.CANCEL.get(),
        rightButtonText = ManagementTexts.Dialogs.APPLY.get(),
        onRightButtonClick = { onConfirm(windowScale.trim(), transitionScale.trim(), durationScale.trim()) },
        rightButtonEnabled = isValid,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = windowScale,
                onValueChange = { windowScale = it },
                label = { Text(ManagementTexts.Dialogs.WINDOW_ANIMATION_SCALE.get()) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = transitionScale,
                onValueChange = { transitionScale = it },
                label = { Text(ManagementTexts.Dialogs.TRANSITION_ANIMATION_SCALE.get()) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = durationScale,
                onValueChange = { durationScale = it },
                label = { Text(ManagementTexts.Dialogs.ANIMATOR_DURATION_SCALE.get()) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun SessionManagementStandbyDialog(
    onDismiss: () -> Unit,
    onAction: (StandbyAction) -> Unit,
) {
    SessionManagementCenteredDialog(
        title = ManagementTexts.Dialogs.SCREEN_STANDBY.get(),
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Dialogs.CANCEL.get(),
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = ManagementDialogOptionListMaxHeight),
            contentPadding = PaddingValues(vertical = ManagementDialogOptionShadowPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = StandbyAction.entries,
                key = { it.name },
            ) { action ->
                ManagementDialogOptionRow(onClick = { onAction(action) }) {
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SessionManagementActivationDialog(
    refreshToken: Int,
    onDismiss: () -> Unit,
    onAction: (ActivationTarget) -> Unit,
    onUnavailable: (String) -> Unit,
) {
    val context = LocalContext.current
    val appInventory by produceState(
        initialValue = AppInventorySnapshot.loading(),
        key1 = refreshToken,
    ) {
        value = loadAppInventorySnapshot(context, includeSystemApps = false)
    }
    val targets = supportedActivationTargets(appInventory.packages.toSet())
    val inventoryByPackage =
        remember(appInventory.apps) {
            appInventory.apps.associateBy { it.packageName }
        }

    SessionManagementCenteredDialog(
        title = ManagementTexts.Dialogs.ACTIVATE_APP.get(),
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Dialogs.CANCEL.get(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = ManagementActivationDialogContentMaxHeight),
        ) {
            when {
                appInventory.isLoading -> {
                    SessionManagementActivationLoadingList()
                }

                appInventory.errorMessage != null -> {
                    SessionManagementDialogMessage(
                        text = appInventory.errorMessage ?: ManagementTexts.Dialogs.COULDN_T_LOAD_APP_LIST.get(),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = ManagementDialogOptionShadowPadding),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = targets,
                            key = { it.packageName },
                        ) { target ->
                            SessionManagementActivationTargetRow(
                                target = target,
                                inventoryEntry = inventoryByPackage[target.packageName],
                                onAction = onAction,
                                onUnavailable = onUnavailable,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionManagementActivationLoadingList() {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = ManagementDialogOptionShadowPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(4) {
            Surface(
                shape = RoundedCornerShape(ManagementDialogOptionCornerRadius),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Box(modifier = Modifier.size(40.dp))
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.58f)
                                        .height(18.dp),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.42f)
                                        .height(14.dp),
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .width(54.dp)
                                    .height(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionManagementActivationTargetRow(
    target: ActivationTarget,
    inventoryEntry: AppInventoryEntry?,
    onAction: (ActivationTarget) -> Unit,
    onUnavailable: (String) -> Unit,
) {
    val context = LocalContext.current
    val installed = target.command.isNotBlank() && inventoryEntry != null
    val presentation by produceState(
        initialValue =
            RemoteAppPresentation(
                title =
                    inventoryEntry?.let { SessionManagementAppCache.appTitle(it.packageName, it.appTitle) }
                        ?: target.label,
                icon = inventoryEntry?.let { SessionManagementAppCache.cachedIcon(it.packageName) },
            ),
        key1 = inventoryEntry?.packageName,
        key2 = inventoryEntry?.apkPath,
    ) {
        if (inventoryEntry != null) {
            value = loadCachedAppPresentation(context, inventoryEntry, packageNameOnlyMode = false)
        }
    }
    val title = if (installed) presentation.title else target.label

    Surface(
        shape = RoundedCornerShape(ManagementDialogOptionCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ManagementDialogOptionCornerRadius))
                    .clickable {
                        if (installed) {
                            onAction(target)
                        } else {
                            onUnavailable(target.label)
                        }
                    }.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SessionManagementAppAvatar(
                    packageName = target.packageName,
                    appTitle = title,
                    isSystemApp = inventoryEntry?.isSystemApp == true,
                    iconBitmap = if (installed) presentation.icon else null,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = target.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SessionManagementUtilityBadge(
                text = if (installed) ManagementTexts.Dialogs.INSTALLED.get() else ManagementTexts.Dialogs.NOT_INSTALLED.get(),
                accent = if (installed) MaterialTheme.colorScheme.primary else Color.Unspecified,
                available = installed,
            )
        }
    }
}

@Composable
private fun ManagementDialogOptionRow(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(ManagementDialogOptionCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ManagementDialogOptionCornerRadius))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            content = content,
        )
    }
}
