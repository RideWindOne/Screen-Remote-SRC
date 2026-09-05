package com.screen.remote.android.feature.session.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screen.remote.android.app.deeplink.ManageDestination
import com.screen.remote.android.app.deeplink.ManageSection
import com.screen.remote.android.app.deeplink.NewSessionPrefill
import com.screen.remote.android.app.deeplink.ScreenRemoteDeepLink
import com.screen.remote.android.app.deeplink.resolveSessionTarget
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.util.formatHostPort
import com.screen.remote.android.core.data.datastore.PreferencesManager
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.data.repository.toData
import com.screen.remote.android.core.designsystem.component.CompactGroupSelector
import com.screen.remote.android.core.designsystem.component.GroupManagementDialog
import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.domain.model.SessionColor
import com.screen.remote.android.core.domain.model.parseSessionAddressCandidate
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.core.telemetry.TelemetryManager
import com.screen.remote.android.core.update.GitHubReleaseInfo
import com.screen.remote.android.core.update.GitHubReleaseUpdateChecker
import com.screen.remote.android.core.update.isAutomaticUpdateCheckDue
import com.screen.remote.android.core.update.shouldShowAutomaticUpdate
import com.screen.remote.android.feature.device.ui.component.AdbKeyManagementDialog
import com.screen.remote.android.feature.remote.ui.RemoteDisplayScreen
import com.screen.remote.android.feature.session.ui.component.AddSessionDialog
import com.screen.remote.android.feature.session.viewmodel.MainViewModel
import com.screen.remote.android.feature.session.viewmodel.ManagementConnectStatus
import com.screen.remote.android.feature.session.viewmodel.SessionOnboardingState
import com.screen.remote.android.feature.settings.ui.AboutScreen
import com.screen.remote.android.feature.settings.ui.AppearanceScreen
import com.screen.remote.android.feature.settings.ui.BackupRestoreScreen
import com.screen.remote.android.feature.settings.ui.CustomCommandsScreen
import com.screen.remote.android.feature.settings.ui.LanguageScreen
import com.screen.remote.android.feature.settings.ui.LogManagementScreen
import com.screen.remote.android.feature.settings.ui.SettingsScreen
import com.screen.remote.android.feature.settings.ui.UpdateAvailableDialog
import kotlinx.coroutines.flow.first
import com.screen.remote.android.app.deeplink.SettingsDestination as DeepLinkSettingsDestination
import com.screen.remote.android.core.designsystem.component.IOSAlertDialog as AlertDialog

private enum class MainScreenSettingsDestination {
    ROOT,
    ABOUT,
    APPEARANCE,
    LANGUAGE,
    LOG_MANAGEMENT,
    GROUP_MANAGEMENT,
    ADB_KEYS,
    BACKUP_RESTORE,
    CUSTOM_COMMANDS,
}

private class MainScreenRouteState {
    var settingsDestination by mutableStateOf<MainScreenSettingsDestination?>(null)
        private set

    var openDevicePairingOnSettingsEntry by mutableStateOf(false)
        private set

    var devicePairingHostPort by mutableStateOf("")
        private set

    var pendingManagementSessionId by mutableStateOf<String?>(null)
        private set

    var managementSessionId by mutableStateOf<String?>(null)
        private set

    var pendingManagementDestination by mutableStateOf(ManageDestination())
        private set

    var managementDestination by mutableStateOf(ManageDestination())
        private set

    var pendingManagementParameters by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var managementParameters by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var remoteDisplayMinimized by mutableStateOf(false)
        private set

    fun minimizeRemoteDisplay() {
        remoteDisplayMinimized = true
    }

    fun restoreRemoteDisplay() {
        remoteDisplayMinimized = false
    }

    fun openSettings() {
        openDevicePairingOnSettingsEntry = false
        devicePairingHostPort = ""
        settingsDestination = MainScreenSettingsDestination.ROOT
    }

    fun openDevicePairingSettings(
        host: String,
        port: Int,
    ) {
        openDevicePairingOnSettingsEntry = true
        devicePairingHostPort = formatHostPort(host, port)
        settingsDestination = MainScreenSettingsDestination.ROOT
    }

    fun navigateToSettings(destination: MainScreenSettingsDestination) {
        settingsDestination = destination
    }

    fun returnToSettingsRoot() {
        settingsDestination = MainScreenSettingsDestination.ROOT
    }

    fun closeSettings() {
        settingsDestination = null
        openDevicePairingOnSettingsEntry = false
        devicePairingHostPort = ""
    }

    fun requestSessionManagement(
        sessionId: String,
        destination: ManageDestination = ManageDestination(),
        parameters: Map<String, String> = emptyMap(),
    ) {
        pendingManagementSessionId = sessionId
        pendingManagementDestination = destination
        pendingManagementParameters = parameters
        managementSessionId = null
    }

    fun openConnectedSessionManagement(sessionId: String) {
        if (pendingManagementSessionId == sessionId) {
            managementSessionId = sessionId
            managementDestination = pendingManagementDestination
            managementParameters = pendingManagementParameters
            pendingManagementSessionId = null
        }
    }

    fun cancelPendingSessionManagement(sessionId: String? = null) {
        if (sessionId == null || pendingManagementSessionId == sessionId) {
            pendingManagementSessionId = null
            pendingManagementDestination = ManageDestination()
            pendingManagementParameters = emptyMap()
        }
    }

    fun closeSessionManagement() {
        managementSessionId = null
        pendingManagementSessionId = null
        pendingManagementDestination = ManageDestination()
        managementDestination = ManageDestination()
        pendingManagementParameters = emptyMap()
        managementParameters = emptyMap()
    }
}

@Composable
private fun rememberMainScreenRouteState(): MainScreenRouteState = remember { MainScreenRouteState() }

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(factory = MainViewModel.provideFactory(LocalContext.current)),
    deepLink: ScreenRemoteDeepLink? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val routeState = rememberMainScreenRouteState()
    val showAddDialog by viewModel.showAddSessionDialog.collectAsState()
    val editingSessionId by viewModel.editingSessionId.collectAsState()
    val newSessionPrefill by viewModel.newSessionPrefill.collectAsState()
    val sessionDataList by viewModel.sessionDataList.collectAsState()
    val allSessionDataList by viewModel.sessionRepository.sessionDataFlow.collectAsState(initial = emptyList())
    val urlSessionData by viewModel.urlSessionData.collectAsState()
    val availableSessionData =
        remember(sessionDataList, urlSessionData) {
            sessionDataList + listOfNotNull(urlSessionData?.takeIf { url -> sessionDataList.none { it.id == url.id } })
        }
    val connectedSessionId by viewModel.connectedSessionId.collectAsState()
    val managementConnectStatus by viewModel.managementConnectStatus.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroupPath by viewModel.selectedGroupPath.collectAsState()
    val onboardingState by viewModel.sessionOnboardingState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val updatePreferences = remember(context) { PreferencesManager(context.applicationContext) }
    val updateChecker = remember { GitHubReleaseUpdateChecker() }
    val managementDataProvider = remember { SessionManagementDataProvider() }
    val nearbyAdbScanController = rememberNearbyAdbScanController(allSessionDataList)
    var availableUpdate by remember { mutableStateOf<GitHubReleaseInfo?>(null) }

    LaunchedEffect(deepLink, onboardingState, availableSessionData) {
        val link = deepLink ?: return@LaunchedEffect
        if (onboardingState != SessionOnboardingState.HIDDEN) return@LaunchedEffect
        try {
            val storedSessions =
                when (link) {
                    is ScreenRemoteDeepLink.EditSession,
                    is ScreenRemoteDeepLink.ScrcpySession,
                    is ScreenRemoteDeepLink.ManageSession,
                        -> viewModel.sessionRepository.sessionDataFlow.first()

                    else -> emptyList()
                }
            when (link) {
                ScreenRemoteDeepLink.Sessions -> Unit
                is ScreenRemoteDeepLink.AddSession -> viewModel.showAddSessionDialog(link.prefill)
                is ScreenRemoteDeepLink.EditSession -> {
                    val session = storedSessions.resolveDeepLinkSession(link.sessionSelector)
                    if (session != null) {
                        viewModel.showEditSessionDialog(session.id)
                    } else {
                        LogManager.w("DeepLink", "Session not found for edit URL: ${link.sessionSelector}")
                    }
                }

                is ScreenRemoteDeepLink.ScrcpySession -> {
                    val storedSession = storedSessions.resolveDeepLinkSession(link.sessionSelector)
                    val session = storedSession ?: createTransientDeepLinkSession(link.sessionSelector)
                    if (session != null) {
                        viewModel.connectUrlSession(session, link.parameters)
                    } else {
                        LogManager.w("DeepLink", "Session not found for scrcpy URL: ${link.sessionSelector}")
                    }
                }

                is ScreenRemoteDeepLink.ManageSession -> {
                    val storedSession = storedSessions.resolveDeepLinkSession(link.sessionSelector)
                    val session = storedSession ?: createTransientDeepLinkSession(link.sessionSelector)
                    if (session != null) {
                        routeState.requestSessionManagement(session.id, link.destination, link.parameters)
                        if (storedSession != null) {
                            viewModel.connectManagementSession(storedSession.id)
                        } else {
                            viewModel.connectManagementSession(session)
                        }
                    } else {
                        LogManager.w("DeepLink", "Session not found for management URL: ${link.sessionSelector}")
                    }
                }

                is ScreenRemoteDeepLink.Settings -> {
                    routeState.navigateToSettings(link.destination.toMainScreenDestination())
                }

                ScreenRemoteDeepLink.DiagnosticLogs -> {
                    routeState.navigateToSettings(MainScreenSettingsDestination.LOG_MANAGEMENT)
                }

                is ScreenRemoteDeepLink.SettingValue -> {
                    viewModel.applyUrlSetting(link.setting, link.value)
                }

                ScreenRemoteDeepLink.GenerateAdbKeys -> viewModel.generateAdbKeys()
                ScreenRemoteDeepLink.Disconnect -> {
                    viewModel.clearConnectStatus()
                    viewModel.disconnectFromDevice()
                }
            }
        } finally {
            onDeepLinkConsumed()
        }
    }

    RequestNotificationPermissionEffect(
        context = context,
        enabled = onboardingState == SessionOnboardingState.HIDDEN,
    )

    LaunchedEffect(onboardingState) {
        if (onboardingState != SessionOnboardingState.HIDDEN) return@LaunchedEffect
        TelemetryManager.runDaily(context.applicationContext)
    }

    LaunchedEffect(onboardingState) {
        if (onboardingState != SessionOnboardingState.HIDDEN) return@LaunchedEffect
        val settings = updatePreferences.settingsFlow.first()
        if (!settings.autoCheckUpdates) return@LaunchedEffect

        val cache = updatePreferences.updateCheckCacheFlow.first()
        val now = System.currentTimeMillis()
        if (!isAutomaticUpdateCheckDue(cache, now)) return@LaunchedEffect

        updateChecker
            .check(
                channel = settings.updateChannel,
            ).onSuccess { release ->
                updatePreferences.recordUpdateCheck(now, release)
                availableUpdate = release?.takeIf { shouldShowAutomaticUpdate(it, cache) }
            }
    }

    LaunchedEffect(managementConnectStatus, routeState.pendingManagementSessionId) {
        when (val status = managementConnectStatus) {
            is ManagementConnectStatus.Connected -> {
                if (routeState.pendingManagementSessionId == status.sessionId) {
                    availableSessionData
                        .find { it.id == status.sessionId }
                        ?.let { sessionData ->
                            managementDataProvider.startPrefetch(
                                context = context.applicationContext,
                                sessionData = sessionData,
                            )
                            routeState.openConnectedSessionManagement(status.sessionId)
                        }
                }
            }

            is ManagementConnectStatus.Failed -> {
                routeState.cancelPendingSessionManagement(status.sessionId)
                viewModel.clearManagementConnectStatus()
            }

            else -> Unit
        }
    }

    val managementSessionId = routeState.managementSessionId
    if (managementSessionId != null) {
        val managementSession = availableSessionData.find { it.id == managementSessionId }
        if (managementSession != null) {
            SessionManagementScreen(
                sessionData = managementSession,
                dataProvider = managementDataProvider,
                initialSection = routeState.managementDestination.section.toManagementSection(),
                initialFilePath = routeState.managementDestination.filePath,
                initialCommand = routeState.managementParameters["command"].orEmpty(),
                customCommands = settings.customShellCommands,
                replaceDefaultCommands = settings.replaceDefaultShellCommands,
                onBack = {
                    routeState.closeSessionManagement()
                },
            )
            return
        } else {
            routeState.closeSessionManagement()
        }
    }

    if (connectedSessionId != null && !routeState.remoteDisplayMinimized) {
        RemoteDisplayScreen(
            sessionId = connectedSessionId!!,
            mainViewModel = viewModel,
            onClose = {
                routeState.restoreRemoteDisplay()
                viewModel.clearConnectStatus()
                viewModel.disconnectFromDevice()
            },
            onBackToApp = {
                // 只最小化远程界面，不断开连接
                routeState.minimizeRemoteDisplay()
            },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MainScreenContent(
            routeState = routeState,
            viewModel = viewModel,
            groups = groups,
            selectedGroupPath = selectedGroupPath,
            nearbyAdbScanController = nearbyAdbScanController,
        )

        if (onboardingState == SessionOnboardingState.HIDDEN) {
            MainScreenDialogs(
                routeState = routeState,
                viewModel = viewModel,
                showAddDialog = showAddDialog,
                editingSessionId = editingSessionId,
                newSessionPrefill = newSessionPrefill,
                sessionDataList = sessionDataList,
                groups = groups,
            )

            val pendingManagementSessionId = routeState.pendingManagementSessionId
            if (pendingManagementSessionId != null) {
                val pendingSession = sessionDataList.find { it.id == pendingManagementSessionId }
                val loadingMessage =
                    when (managementConnectStatus) {
                        is ManagementConnectStatus.Connecting ->
                            ManagementTexts.General.CONNECTING_TO_DEVICE.format(
                                pendingSession?.name ?: ManagementTexts.General.TARGET_DEVICE.get(),
                            )

                        else -> ManagementTexts.General.PREPARING_MANAGEMENT.get()
                    }
                ManagementLoadingDialog(
                    title = ManagementTexts.General.MANAGEMENT.get(),
                    message = loadingMessage,
                    onDismiss = {
                        routeState.cancelPendingSessionManagement(pendingManagementSessionId)
                        viewModel.cancelManagementConnect(pendingManagementSessionId)
                    },
                )
            }

            availableUpdate?.let { release ->
                UpdateAvailableDialog(
                    release = release,
                    onDismiss = { availableUpdate = null },
                )
            }
        }

        if (onboardingState != SessionOnboardingState.HIDDEN) {
            SessionOnboardingBackground(
                showLogo = onboardingState == SessionOnboardingState.LOADING,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zIndex(1f),
            )
        }

        when (onboardingState) {
            SessionOnboardingState.INTRODUCTION ->
                FirstSessionWelcomeCard(
                    onDismiss = viewModel::completeSessionOnboarding,
                    modifier = Modifier.zIndex(2f),
                )

            SessionOnboardingState.RECENT_UPDATES ->
                RecentUpdatesCard(
                    onDismiss = viewModel::completeSessionOnboarding,
                    modifier = Modifier.zIndex(2f),
                )

            else -> Unit
        }
    }
}

private fun DeepLinkSettingsDestination.toMainScreenDestination(): MainScreenSettingsDestination =
    when (this) {
        DeepLinkSettingsDestination.ROOT -> MainScreenSettingsDestination.ROOT
        DeepLinkSettingsDestination.ABOUT -> MainScreenSettingsDestination.ABOUT
        DeepLinkSettingsDestination.APPEARANCE -> MainScreenSettingsDestination.APPEARANCE
        DeepLinkSettingsDestination.LANGUAGE -> MainScreenSettingsDestination.LANGUAGE
        DeepLinkSettingsDestination.LOGS -> MainScreenSettingsDestination.LOG_MANAGEMENT
        DeepLinkSettingsDestination.GROUPS -> MainScreenSettingsDestination.GROUP_MANAGEMENT
        DeepLinkSettingsDestination.ADB_KEYS -> MainScreenSettingsDestination.ADB_KEYS
        DeepLinkSettingsDestination.BACKUP -> MainScreenSettingsDestination.BACKUP_RESTORE
    }

private fun List<SessionData>.resolveDeepLinkSession(selector: String): SessionData? =
    resolveSessionTarget(
        candidates = this,
        selector = selector,
        sessionId = SessionData::id,
        sessionName = SessionData::name,
    )

private fun createTransientDeepLinkSession(selector: String): SessionData? =
    parseSessionAddressCandidate(selector)?.let { candidate ->
        SessionData(
            id = "url:${candidate.deviceIdentifier()}",
            name = selector,
            connectionCandidates = listOf(candidate.toData()),
            color = SessionColor.BLUE.name,
        )
    }

private fun ManageSection.toManagementSection(): SessionManagementSection =
    when (this) {
        ManageSection.DEVICE -> SessionManagementSection.DeviceInfo
        ManageSection.UTILITY -> SessionManagementSection.Utility
        ManageSection.FILE -> SessionManagementSection.Files
        ManageSection.APP -> SessionManagementSection.Apps
        ManageSection.PROCESS -> SessionManagementSection.Process
        ManageSection.PORT_FORWARD -> SessionManagementSection.PortForward
        ManageSection.COMMAND -> SessionManagementSection.Command
    }

@Composable
private fun RequestNotificationPermissionEffect(
    context: Context,
    enabled: Boolean,
) {
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { }

    LaunchedEffect(context, enabled) {
        if (!enabled) {
            return@LaunchedEffect
        }
        if (!ApiCompatHelper.needsNotificationPermission()) {
            return@LaunchedEffect
        }

        val hasPermission =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun ManagementLoadingDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        showActionBar = false,
        widthRatio = 0.72f,
        dismissOnBackPress = true,
        dismissOnClickOutside = false,
        title = { Text(title) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(
    routeState: MainScreenRouteState,
    viewModel: MainViewModel,
    groups: List<DeviceGroup>,
    selectedGroupPath: String,
    nearbyAdbScanController: NearbyAdbScanController,
) {
    val txtTitle = rememberText(SessionTexts.MAIN_TITLE_SESSIONS)
    val txtAddSession = rememberText(SessionTexts.MAIN_ADD_SESSION)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = txtTitle,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = routeState::openSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = ManagementTexts.General.SETTINGS.get(),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.showAddSessionDialog() },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = txtAddSession,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                SessionsScreen(
                    viewModel = viewModel,
                    onManageSession = { sessionData ->
                        routeState.requestSessionManagement(sessionData.id)
                        viewModel.connectManagementSession(sessionData.id)
                    },
                    onResumeConnectedSession = {
                        routeState.restoreRemoteDisplay()
                    },
                )
            }

            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                val groupSelectorMaxWidth = maxWidth / 2
                Row(
                    modifier = Modifier.animateContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactGroupSelector(
                        groups = groups,
                        selectedGroupPath = selectedGroupPath,
                        onGroupSelected = viewModel::selectGroup,
                        modifier = Modifier.widthIn(max = groupSelectorMaxWidth),
                    )
                    NearbyAdbDevicesButton(
                        controller = nearbyAdbScanController,
                        onPairingRequired = routeState::openDevicePairingSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreenDialogs(
    routeState: MainScreenRouteState,
    viewModel: MainViewModel,
    showAddDialog: Boolean,
    editingSessionId: String?,
    newSessionPrefill: NewSessionPrefill,
    sessionDataList: List<SessionData>,
    groups: List<DeviceGroup>,
) {
    if (showAddDialog) {
        val editingSession =
            editingSessionId?.let { id ->
                sessionDataList.find { it.id == id }
            }
        key(editingSessionId ?: newSessionPrefill) {
            AddSessionDialog(
                sessionData = editingSession,
                initialPrefill = newSessionPrefill,
                availableGroups = groups,
                onDismiss = viewModel::hideAddSessionDialog,
                onConfirm = viewModel::saveSessionData,
            )
        }
    }

    when (routeState.settingsDestination) {
        null -> Unit
        MainScreenSettingsDestination.ROOT -> {
            SettingsScreen(
                viewModel = viewModel,
                onBack = routeState::closeSettings,
                openDevicePairingOnEntry = routeState.openDevicePairingOnSettingsEntry,
                devicePairingHostPort = routeState.devicePairingHostPort,
                onNavigateToAbout = {
                    routeState.navigateToSettings(MainScreenSettingsDestination.ABOUT)
                },
                onNavigateToAppearance = {
                    routeState.navigateToSettings(MainScreenSettingsDestination.APPEARANCE)
                },
                onNavigateToLanguage = {
                    routeState.navigateToSettings(MainScreenSettingsDestination.LANGUAGE)
                },
                onNavigateToAdbKeys = {
                    routeState.navigateToSettings(MainScreenSettingsDestination.ADB_KEYS)
                },
                onNavigateToLogManagement = {
                    routeState.navigateToSettings(MainScreenSettingsDestination.LOG_MANAGEMENT)
                },
                onNavigateToGroupManagement = {
                    routeState.navigateToSettings(MainScreenSettingsDestination.GROUP_MANAGEMENT)
                },
                onNavigateToBackupRestore = {
                    routeState.navigateToSettings(MainScreenSettingsDestination.BACKUP_RESTORE)
                },
                onNavigateToCustomCommands = {
                    routeState.navigateToSettings(MainScreenSettingsDestination.CUSTOM_COMMANDS)
                },
            )
        }

        MainScreenSettingsDestination.ABOUT -> {
            AboutScreen(onBack = routeState::returnToSettingsRoot)
        }

        MainScreenSettingsDestination.APPEARANCE -> {
            AppearanceScreen(
                viewModel = viewModel,
                onBack = routeState::returnToSettingsRoot,
            )
        }

        MainScreenSettingsDestination.LANGUAGE -> {
            LanguageScreen(
                viewModel = viewModel,
                onBack = routeState::returnToSettingsRoot,
            )
        }

        MainScreenSettingsDestination.LOG_MANAGEMENT -> {
            LogManagementScreen(
                onDismiss = routeState::returnToSettingsRoot,
            )
        }

        MainScreenSettingsDestination.GROUP_MANAGEMENT -> {
            GroupManagementDialog(
                groups = groups,
                onDismiss = routeState::returnToSettingsRoot,
                onAddGroup = viewModel::addGroup,
                onUpdateGroup = viewModel::updateGroup,
                onDeleteGroup = viewModel::removeGroup,
            )
        }

        MainScreenSettingsDestination.ADB_KEYS -> {
            AdbKeyManagementDialog(onDismiss = routeState::returnToSettingsRoot)
        }

        MainScreenSettingsDestination.BACKUP_RESTORE -> {
            BackupRestoreScreen(
                viewModel = viewModel,
                onBack = routeState::returnToSettingsRoot,
            )
        }

        MainScreenSettingsDestination.CUSTOM_COMMANDS -> {
            CustomCommandsScreen(
                settings = viewModel.settings.collectAsState().value,
                onBack = routeState::returnToSettingsRoot,
                onUpdateSettings = viewModel::updateSettings,
            )
        }
    }
}
