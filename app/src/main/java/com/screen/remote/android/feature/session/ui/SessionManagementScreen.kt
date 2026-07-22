package com.screen.remote.android.feature.session.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.i18n.ManagementTexts
import kotlinx.coroutines.launch

@Composable
internal fun SessionManagementScreen(
    sessionData: SessionData,
    dataProvider: SessionManagementDataProvider,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()
    var selectedSection by remember(sessionData.id) {
        mutableStateOf(SessionManagementSection.DeviceInfo)
    }
    var drawerOpen by remember(sessionData.id) { mutableStateOf(false) }
    var dashboardRefreshTick by remember(sessionData.id) { mutableIntStateOf(0) }
    val contentRefreshTicks = remember(sessionData.id) { mutableStateMapOf<SessionManagementSection, Int>() }
    val appOptionsState = remember(sessionData.id) { SessionManagementAppOptionsState() }
    var progressDialog by remember(sessionData.id) { mutableStateOf<ManagementProgressDialogState?>(null) }
    var resultDialog by remember(sessionData.id) { mutableStateOf<ManagementResultDialogState?>(null) }
    var exitConfirmOpen by remember(sessionData.id) { mutableStateOf(false) }
    var rebootDialogOpen by remember(sessionData.id) { mutableStateOf(false) }
    var activationDialogOpen by remember(sessionData.id) { mutableStateOf(false) }
    var standbyDialogOpen by remember(sessionData.id) { mutableStateOf(false) }
    var dpiDialogState by remember(sessionData.id) { mutableStateOf<ManagementValueInputDialogState?>(null) }
    var fixedPortDialogState by remember(sessionData.id) { mutableStateOf<ManagementValueInputDialogState?>(null) }
    var resolutionDialogState by remember(sessionData.id) { mutableStateOf<ResolutionDialogState?>(null) }
    var animationDialogState by remember(sessionData.id) { mutableStateOf<AnimationScaleDialogState?>(null) }
    var screenshotResult by remember(sessionData.id) { mutableStateOf<ScreenshotPreviewState?>(null) }
    var fileAddMenuOpenTick by remember(sessionData.id) { mutableIntStateOf(0) }
    var appAddMenuOpenTick by remember(sessionData.id) { mutableIntStateOf(0) }
    var fileSelectionMode by remember(sessionData.id) { mutableStateOf(false) }
    var commandInput by remember(sessionData.id) { mutableStateOf("") }
    var commandPresetDialogOpen by remember(sessionData.id) { mutableStateOf(false) }
    val commandTerminalSession = remember(sessionData.id) { ManagementTerminalSession(scope) }
    val snapshot =
        dataProvider.deviceSnapshot
            ?: DeviceDashboardSnapshot.loading(sessionData).copy(
                errorMessage = dataProvider.deviceErrorMessage,
            )
    val snapshotRefreshing = dataProvider.deviceRefreshing
    val fileBrowserState = dataProvider.fileBrowserState

    DisposableEffect(commandTerminalSession) {
        onDispose {
            commandTerminalSession.close()
            dataProvider.invalidate(sessionData.id)
        }
    }

    LaunchedEffect(sessionData.id, dashboardRefreshTick) {
        val hasPreviousSnapshot = dataProvider.deviceSnapshot != null
        val nextSnapshot =
            dataProvider.loadDeviceInformation(
                context = context,
                sessionData = sessionData,
                forceRefresh = dashboardRefreshTick > 0,
            )
        if (nextSnapshot?.errorMessage != null && hasPreviousSnapshot) {
            resultDialog =
                ManagementResultDialogState(
                    title = ManagementTexts.General.DEVICE_INFO.get(),
                    message = nextSnapshot.errorMessage,
                    isSuccess = false,
                )
        }
    }

    val onRefresh =
        remember(sessionData.id, selectedSection) {
            when (selectedSection) {
                SessionManagementSection.Apps -> {
                    appOptionsState::show
                }

                SessionManagementSection.DeviceInfo, SessionManagementSection.Utility -> {
                    { dashboardRefreshTick += 1 }
                }

                SessionManagementSection.Files, SessionManagementSection.Process, SessionManagementSection.PortForward -> {
                    {
                        contentRefreshTicks[selectedSection] =
                            (contentRefreshTicks[selectedSection] ?: 0) + 1
                    }
                }

                else -> {
                    null
                }
            }
        }

    DisposableEffect(activity) {
        activity?.window?.let { window ->
            ApiCompatHelper.setFullScreen(window, false)
        }
        onDispose {
            activity?.window?.let { window ->
                ApiCompatHelper.setFullScreen(window, false)
            }
        }
    }

    fun launchAction(
        title: String,
        message: String,
        refreshDashboardOnSuccess: Boolean = false,
        block: suspend () -> Result<String>,
    ) {
        progressDialog = ManagementProgressDialogState(title = title, message = message)
        scope.launch {
            val result = block()
            progressDialog = null
            resultDialog =
                ManagementResultDialogState(
                    title = title,
                    message =
                        result.getOrNull()?.ifBlank { ManagementTexts.General.COMPLETED.format(title) }
                            ?: (result.exceptionOrNull()?.message ?: ManagementTexts.General.FAILED.format(title)),
                    isSuccess = result.isSuccess,
                )
            if (result.isSuccess && refreshDashboardOnSuccess) {
                dashboardRefreshTick++
            }
        }
    }

    BackHandler {
        when {
            drawerOpen -> drawerOpen = false
            progressDialog != null -> progressDialog = null
            resultDialog != null -> resultDialog = null
            screenshotResult != null -> screenshotResult = null
            rebootDialogOpen -> rebootDialogOpen = false
            activationDialogOpen -> activationDialogOpen = false
            standbyDialogOpen -> standbyDialogOpen = false
            dpiDialogState != null -> dpiDialogState = null
            fixedPortDialogState != null -> fixedPortDialogState = null
            resolutionDialogState != null -> resolutionDialogState = null
            animationDialogState != null -> animationDialogState = null
            else -> exitConfirmOpen = true
        }
    }

    val topActionIcon =
        when (selectedSection) {
            SessionManagementSection.Apps -> Icons.Default.Tune
            SessionManagementSection.Command -> Icons.Default.Apps
            else -> Icons.Default.Refresh
        }
    val topActionContentDescription =
        when (selectedSection) {
            SessionManagementSection.Apps -> ManagementTexts.General.APP_OPTIONS.get()
            SessionManagementSection.Command -> ManagementTexts.General.QUICK_COMMANDS.get()
            else -> ManagementTexts.General.REFRESH.get()
        }
    val topActionCallback: (() -> Unit)? =
        when (selectedSection) {
            SessionManagementSection.Command -> {
                { commandPresetDialogOpen = true }
            }

            else -> {
                onRefresh
            }
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = SessionManagementPageBottomInset),
                contentAlignment = Alignment.TopCenter,
            ) {
                SessionManagementPageFrame(
                    modifier =
                        Modifier
                            .fillMaxWidth(SessionManagementPageOuterWidthFraction)
                            .fillMaxHeight(),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SessionManagementTopRow(
                            modifier = Modifier.fillMaxWidth(),
                            title = selectedSection.title,
                            onOpenMenu = { drawerOpen = true },
                            onRefresh = topActionCallback,
                            actionIcon = topActionIcon,
                            actionContentDescription = topActionContentDescription,
                        )

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            SessionManagementDetailPane(
                                modifier =
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(SessionManagementContentWidthWithinPageFraction),
                                sessionData = sessionData,
                                selectedSection = selectedSection,
                                snapshot = snapshot,
                                snapshotRefreshing = snapshotRefreshing,
                                refreshToken = contentRefreshTicks[selectedSection] ?: 0,
                                appOptionsState = appOptionsState,
                                dataProvider = dataProvider,
                                fileBrowserState = fileBrowserState,
                                fileAddMenuOpenTick = fileAddMenuOpenTick,
                                appAddMenuOpenTick = appAddMenuOpenTick,
                                commandInput = commandInput,
                                commandPresetDialogOpen = commandPresetDialogOpen,
                                commandTerminalSession = commandTerminalSession,
                                onFileSelectionModeChanged = { fileSelectionMode = it },
                                onCommandInputChange = { commandInput = it },
                                onCommandPresetDialogChange = { commandPresetDialogOpen = it },
                                onUtilityAction = { action ->
                                    when (action) {
                                        UtilityAction.FixedPort -> {
                                            fixedPortDialogState =
                                                ManagementValueInputDialogState(
                                                    title = ManagementTexts.General.FIXED_PORT.get(),
                                                    label = ManagementTexts.General.PORT.get(),
                                                    initialValue = snapshot.wirelessDebugPort?.toString().orEmpty(),
                                                    confirmText = ManagementTexts.General.APPLY.get(),
                                                    placeholder = ManagementTexts.General.EXAMPLE_5555.get(),
                                                )
                                        }

                                        UtilityAction.Screenshot -> {
                                            progressDialog =
                                                ManagementProgressDialogState(
                                                    title = ManagementTexts.General.SCREENSHOT.get(),
                                                    message = ManagementTexts.General.CAPTURING_SCREENSHOT_FROM_DEVICE.get(),
                                                )
                                            scope.launch {
                                                val result = captureDeviceScreenshot(context)
                                                progressDialog = null
                                                result.fold(
                                                    onSuccess = { file ->
                                                        screenshotResult = ScreenshotPreviewState(file = file)
                                                    },
                                                    onFailure = { error ->
                                                        resultDialog =
                                                            ManagementResultDialogState(
                                                                title = ManagementTexts.General.SCREENSHOT.get(),
                                                                message = error.message
                                                                    ?: ManagementTexts.General.SCREENSHOT_FAILED.get(),
                                                                isSuccess = false,
                                                            )
                                                    },
                                                )
                                            }
                                        }

                                        UtilityAction.AdvancedReboot -> {
                                            rebootDialogOpen = true
                                        }

                                        UtilityAction.ActivateApp -> {
                                            activationDialogOpen = true
                                        }

                                        UtilityAction.ModifyDpi -> {
                                            dpiDialogState =
                                                ManagementValueInputDialogState(
                                                    title = ManagementTexts.General.CHANGE_DPI.get(),
                                                    label = ManagementTexts.General.DPI_VALUE.get(),
                                                    initialValue = snapshot.currentDpiValue.orEmpty(),
                                                    confirmText = ManagementTexts.General.APPLY.get(),
                                                )
                                        }

                                        UtilityAction.ModifyResolution -> {
                                            resolutionDialogState =
                                                ResolutionDialogState(
                                                    width = snapshot.currentResolutionWidth.orEmpty(),
                                                    height = snapshot.currentResolutionHeight.orEmpty(),
                                                )
                                        }

                                        UtilityAction.AnimationScale -> {
                                            animationDialogState =
                                                AnimationScaleDialogState(
                                                    windowScale = "1.0",
                                                    transitionScale = "1.0",
                                                    durationScale = "1.0",
                                                )
                                        }

                                        UtilityAction.SleepStandby -> {
                                            standbyDialogOpen = true
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        if (selectedSection == SessionManagementSection.Files && !drawerOpen && !fileSelectionMode) {
            SessionManagementAddFab(
                modifier = Modifier.align(Alignment.BottomEnd),
                contentDescription = ManagementTexts.General.ADD.get(),
                onClick = { fileAddMenuOpenTick += 1 },
            )
        }

        if (selectedSection == SessionManagementSection.Apps && !drawerOpen) {
            SessionManagementAddFab(
                modifier = Modifier.align(Alignment.BottomEnd),
                contentDescription = ManagementTexts.General.INSTALL_APP.get(),
                onClick = { appAddMenuOpenTick += 1 },
            )
        }

        if (drawerOpen) {
            SessionManagementDrawer(
                sessionData = sessionData,
                selectedSection = selectedSection,
                onDismiss = { drawerOpen = false },
                onSectionSelected = {
                    appOptionsState.dismiss()
                    selectedSection = it
                    drawerOpen = false
                },
                onExit = {
                    drawerOpen = false
                    onBack()
                },
            )
        }
    }

    progressDialog?.let { dialog ->
        SessionManagementProgressDialog(
            title = dialog.title,
            message = dialog.message,
        )
    }

    resultDialog?.let { dialog ->
        SessionManagementMessageDialog(
            title = dialog.title,
            message = dialog.message,
            onDismiss = { resultDialog = null },
        )
    }

    if (rebootDialogOpen) {
        SessionManagementRebootDialog(
            onDismiss = { rebootDialogOpen = false },
            onAction = { mode ->
                rebootDialogOpen = false
                launchAction(
                    title = ManagementTexts.General.ADVANCED_REBOOT.get(),
                    message = ManagementTexts.General.RUNNING.format(mode.label),
                ) {
                    runShellAction(
                        mode.command,
                        successMessage = ManagementTexts.General.COMMAND_SENT.format(mode.label)
                    )
                }
            },
        )
    }

    if (exitConfirmOpen) {
        SessionManagementExitConfirmDialog(
            onDismiss = { exitConfirmOpen = false },
            onConfirm = {
                exitConfirmOpen = false
                onBack()
            },
        )
    }

    if (activationDialogOpen) {
        SessionManagementActivationDialog(
            refreshToken = dashboardRefreshTick,
            onDismiss = { activationDialogOpen = false },
            onAction = { target ->
                activationDialogOpen = false
                launchAction(
                    title = ManagementTexts.General.ACTIVATE_APP.get(),
                    message = ManagementTexts.General.RUNNING_ACTIVATION.format(target.label),
                ) {
                    runShellAction(
                        command = target.command,
                        successMessage = ManagementTexts.General.ACTIVATION_COMMAND_SENT.format(target.label),
                    )
                }
            },
            onUnavailable = { label ->
                activationDialogOpen = false
                resultDialog =
                    ManagementResultDialogState(
                        title = ManagementTexts.General.ACTIVATE_APP.get(),
                        message = ManagementTexts.General.ACTIVATION_UNAVAILABLE.format(label),
                        isSuccess = false,
                    )
            },
        )
    }

    if (standbyDialogOpen) {
        SessionManagementStandbyDialog(
            onDismiss = { standbyDialogOpen = false },
            onAction = { action ->
                standbyDialogOpen = false
                launchAction(
                    title = ManagementTexts.General.SCREEN_STANDBY.get(),
                    message = ManagementTexts.General.RUNNING_STANDBY_ACTION.format(action.label),
                ) {
                    runShellAction(
                        action.command,
                        successMessage = ManagementTexts.General.COMMAND_SENT.format(action.label)
                    )
                }
            },
        )
    }

    dpiDialogState?.let { dialog ->
        SessionManagementValueInputDialog(
            state = dialog,
            onDismiss = { dpiDialogState = null },
            onConfirm = { value ->
                dpiDialogState = null
                launchAction(
                    title = ManagementTexts.General.CHANGE_DPI.get(),
                    message = ManagementTexts.General.APPLYING_DPI.format(value),
                    refreshDashboardOnSuccess = true,
                ) {
                    runShellAction(
                        "wm density $value",
                        successMessage = ManagementTexts.General.DPI_CHANGED.format(value)
                    )
                }
            },
        )
    }

    fixedPortDialogState?.let { dialog ->
        SessionManagementValueInputDialog(
            state = dialog,
            onDismiss = { fixedPortDialogState = null },
            onConfirm = { value ->
                val port = value.toIntOrNull()
                if (port == null || port !in 1024..65535) {
                    resultDialog =
                        ManagementResultDialogState(
                            title = ManagementTexts.General.FIXED_PORT.get(),
                            message = ManagementTexts.General.PORT_MUST_BE_NUMBER_BETWEEN_1024_65535.get(),
                            isSuccess = false,
                        )
                    fixedPortDialogState = null
                    return@SessionManagementValueInputDialog
                }

                fixedPortDialogState = null

                scope.launch {
                    val connection = SessionManagementAdbConnection.current()
                    if (connection == null) {
                        resultDialog =
                            ManagementResultDialogState(
                                title = ManagementTexts.General.FIXED_PORT.get(),
                                message = ManagementTexts.General.NO_ADB_CONNECTION_AVAILABLE.get(),
                                isSuccess = false,
                            )
                        return@launch
                    }

                    resultDialog =
                        ManagementResultDialogState(
                            title = ManagementTexts.General.FIXED_PORT.get(),
                            message =
                                ManagementTexts.General.PORT_COMMAND_SENT.format(port),
                            isSuccess = true,
                        )
                    // 发送端口设置命令，不依赖响应结果：ADB 服务重启时当前连接可能会被关闭。
                    connection.restartTcpip(port)
                }
            },
        )
    }

    resolutionDialogState?.let { dialog ->
        SessionManagementResolutionDialog(
            state = dialog,
            onDismiss = { resolutionDialogState = null },
            onConfirm = { width, height ->
                resolutionDialogState = null
                launchAction(
                    title = ManagementTexts.General.CHANGE_RESOLUTION.get(),
                    message = ManagementTexts.General.APPLYING_X.format(width, height),
                    refreshDashboardOnSuccess = true,
                ) {
                    runShellAction(
                        "wm size ${width}x$height",
                        successMessage = ManagementTexts.General.RESOLUTION_CHANGED_X.format(width, height)
                    )
                }
            },
        )
    }

    animationDialogState?.let { dialog ->
        SessionManagementAnimationDialog(
            state = dialog,
            onDismiss = { animationDialogState = null },
            onConfirm = { windowScale, transitionScale, durationScale ->
                animationDialogState = null
                launchAction(
                    title = ManagementTexts.General.ANIMATION_SCALE.get(),
                    message = ManagementTexts.General.APPLYING_ANIMATION_SCALE_VALUES.get(),
                ) {
                    runShellAction(
                        command =
                            "settings put global window_animation_scale $windowScale && " +
                                "settings put global transition_animation_scale $transitionScale && " +
                                "settings put global animator_duration_scale $durationScale",
                        successMessage = ManagementTexts.General.ANIMATION_SCALE_VALUES_UPDATED.get(),
                    )
                }
            },
        )
    }

    screenshotResult?.let { state ->
        SessionManagementScreenshotDialog(
            state = state,
            onDismiss = { screenshotResult = null },
            onOpen = {
                openImagePreview(context, state.file)
            },
            onSave = {
                scope.launch {
                    val result = saveImageToGallery(context, state.file)
                    if (result.isSuccess) {
                        screenshotResult = null
                        Toast.makeText(
                            context,
                            ManagementTexts.General.SCREENSHOT_SAVED_GALLERY.get(),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        resultDialog =
                            ManagementResultDialogState(
                                title = ManagementTexts.General.SAVE_SCREENSHOT.get(),
                                message = result.exceptionOrNull()?.message
                                    ?: ManagementTexts.General.SAVE_TO_GALLERY_FAILED.get(),
                                isSuccess = false,
                            )
                    }
                }
            },
        )
    }
}

private val SessionManagementPageBottomInset = 12.dp

@Composable
private fun SessionManagementDetailPane(
    modifier: Modifier = Modifier,
    sessionData: SessionData,
    selectedSection: SessionManagementSection,
    snapshot: DeviceDashboardSnapshot,
    snapshotRefreshing: Boolean,
    refreshToken: Int,
    appOptionsState: SessionManagementAppOptionsState,
    dataProvider: SessionManagementDataProvider,
    fileBrowserState: SessionManagementFileBrowserState,
    fileAddMenuOpenTick: Int,
    appAddMenuOpenTick: Int,
    commandInput: String,
    commandPresetDialogOpen: Boolean,
    commandTerminalSession: ManagementTerminalSession,
    onFileSelectionModeChanged: (Boolean) -> Unit,
    onCommandInputChange: (String) -> Unit,
    onCommandPresetDialogChange: (Boolean) -> Unit,
    onUtilityAction: (UtilityAction) -> Unit,
) {
    when (selectedSection) {
        SessionManagementSection.DeviceInfo -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = SessionManagementPageInnerTopPadding,
                        bottom = SessionManagementPageInnerBottomPadding,
                    ),
            ) {
                item {
                    Box {
                        SessionManagementHomeSnapshot(snapshot = snapshot)
                        if (snapshotRefreshing) {
                            SessionManagementLoadingBar(modifier = Modifier.align(Alignment.TopCenter))
                        }
                    }
                }
            }
        }

        SessionManagementSection.Utility -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = SessionManagementPageInnerTopPadding,
                        bottom = SessionManagementPageInnerBottomPadding,
                    ),
            ) {
                item {
                    Box {
                        SessionManagementUtilityList(
                            snapshot = snapshot,
                            onAction = onUtilityAction,
                        )
                        if (snapshotRefreshing) {
                            SessionManagementLoadingBar(modifier = Modifier.align(Alignment.TopCenter))
                        }
                    }
                }
            }
        }

        SessionManagementSection.Files -> {
            SessionManagementFileBrowser(
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(top = SessionManagementPageInnerTopPadding),
                state = fileBrowserState,
                dataProvider = dataProvider,
                sessionId = sessionData.id,
                refreshToken = refreshToken,
                externalAddMenuRequestTick = fileAddMenuOpenTick,
                onSelectionModeChanged = onFileSelectionModeChanged,
            )
        }

        SessionManagementSection.Apps -> {
            SessionManagementAppsPage(
                modifier = modifier.fillMaxSize(),
                refreshToken = refreshToken,
                optionsState = appOptionsState,
                addMenuRequestTick = appAddMenuOpenTick,
                cacheScopeKey = sessionData.id,
                dataProvider = dataProvider,
            )
        }

        SessionManagementSection.Process -> {
            SessionManagementProcessPage(
                modifier = modifier.fillMaxSize(),
                snapshot = snapshot,
                refreshToken = refreshToken,
                cacheScopeKey = sessionData.id,
            )
        }

        SessionManagementSection.PortForward -> {
            SessionManagementPortForwardPage(
                sessionData = sessionData,
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(vertical = SessionManagementPageInnerTopPadding),
                refreshToken = refreshToken,
            )
        }

        SessionManagementSection.Command -> {
            SessionManagementCommandPage(
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(vertical = SessionManagementPageInnerTopPadding),
                terminalSession = commandTerminalSession,
                commandInput = commandInput,
                onCommandInputChange = onCommandInputChange,
                showPresetDialog = commandPresetDialogOpen,
                onShowPresetDialogChange = onCommandPresetDialogChange,
            )
        }
    }
}
