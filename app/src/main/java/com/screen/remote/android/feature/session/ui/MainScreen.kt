package com.screen.remote.android.feature.session.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.constants.AppColors
import com.screen.remote.android.core.common.constants.IosDesignTokens
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.designsystem.component.AddActionDialog
import com.screen.remote.android.core.designsystem.component.CompactGroupSelector
import com.screen.remote.android.core.designsystem.component.GroupManagementDialog
import com.screen.remote.android.core.designsystem.component.PathBreadcrumb
import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.domain.model.GroupType
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.core.designsystem.component.IOSAlertDialog as AlertDialog
import com.screen.remote.android.feature.device.ui.component.AdbKeyManagementDialog
import com.screen.remote.android.feature.remote.presentation.ConnectStatus
import com.screen.remote.android.feature.remote.ui.RemoteDisplayScreen
import com.screen.remote.android.feature.session.ui.component.AddSessionDialog
import com.screen.remote.android.feature.session.viewmodel.ManagementConnectStatus
import com.screen.remote.android.feature.session.viewmodel.MainViewModel
import com.screen.remote.android.feature.settings.ui.AboutScreen
import com.screen.remote.android.feature.settings.ui.AppearanceScreen
import com.screen.remote.android.feature.settings.ui.BackupRestoreScreen
import com.screen.remote.android.feature.settings.ui.LanguageScreen
import com.screen.remote.android.feature.settings.ui.LogManagementScreen
import com.screen.remote.android.feature.settings.ui.SettingsScreen

private val MainScreenHeaderPadding = 16.dp
private val MainScreenTabSelectorWidth = 132.dp
private val MainScreenSessionsTabWidth = 70.dp
private val MainScreenActionsTabWidth = 60.dp
private val MainScreenTabSelectorInset = 2.dp
private val MainScreenTabButtonCornerRadius = 15.dp

private enum class MainScreenTab {
    SESSIONS,
    ACTIONS,
}

private enum class MainScreenSettingsDestination {
    ROOT,
    ABOUT,
    APPEARANCE,
    LANGUAGE,
    LOG_MANAGEMENT,
    GROUP_MANAGEMENT,
    ADB_KEYS,
    BACKUP_RESTORE,
}

private class MainScreenRouteState {
    var selectedTab by mutableStateOf(MainScreenTab.SESSIONS)
        private set

    var settingsDestination by mutableStateOf<MainScreenSettingsDestination?>(null)
        private set

    var pendingManagementSessionId by mutableStateOf<String?>(null)
        private set

    var managementSessionId by mutableStateOf<String?>(null)
        private set

    fun selectTab(tab: MainScreenTab) {
        selectedTab = tab
    }

    fun openSettings() {
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
    }

    fun requestSessionManagement(sessionId: String) {
        pendingManagementSessionId = sessionId
        managementSessionId = null
    }

    fun openConnectedSessionManagement(sessionId: String) {
        if (pendingManagementSessionId == sessionId) {
            managementSessionId = sessionId
            pendingManagementSessionId = null
        }
    }

    fun cancelPendingSessionManagement(sessionId: String? = null) {
        if (sessionId == null || pendingManagementSessionId == sessionId) {
            pendingManagementSessionId = null
        }
    }

    fun closeSessionManagement() {
        managementSessionId = null
        pendingManagementSessionId = null
    }
}

@Composable
private fun rememberMainScreenRouteState(): MainScreenRouteState = remember { MainScreenRouteState() }

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val routeState = rememberMainScreenRouteState()
    val showAddDialog by viewModel.showAddSessionDialog.collectAsState()
    val editingSessionId by viewModel.editingSessionId.collectAsState()
    val sessionDataList by viewModel.sessionDataList.collectAsState()
    val showAddActionDialog by viewModel.showAddActionDialog.collectAsState()
    val connectedSessionId by viewModel.connectedSessionId.collectAsState()
    val connectStatus by viewModel.connectStatus.collectAsState()
    val managementConnectStatus by viewModel.managementConnectStatus.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroupPath by viewModel.selectedGroupPath.collectAsState()
    val selectedAutomationGroupPath by viewModel.selectedAutomationGroupPath.collectAsState()

    RequestNotificationPermissionEffect(context = context)

    LaunchedEffect(managementConnectStatus, routeState.pendingManagementSessionId) {
        when (val status = managementConnectStatus) {
            is ManagementConnectStatus.Connected -> {
                routeState.openConnectedSessionManagement(status.sessionId)
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
        val managementSession = sessionDataList.find { it.id == managementSessionId }
        if (managementSession != null) {
            SessionManagementScreen(
                sessionData = managementSession,
                onBack = {
                    routeState.closeSessionManagement()
                },
            )
            return
        } else {
            routeState.closeSessionManagement()
        }
    }

    if (connectedSessionId != null) {
        RemoteDisplayScreen(
            sessionId = connectedSessionId!!,
            mainViewModel = viewModel,
            onClose = {
                viewModel.clearConnectStatus()
                viewModel.disconnectFromDevice()
            },
        )
        return
    }

    MainScreenContent(
        routeState = routeState,
        viewModel = viewModel,
        groups = groups,
        selectedGroupPath = selectedGroupPath,
        selectedAutomationGroupPath = selectedAutomationGroupPath,
    )

    MainScreenDialogs(
        routeState = routeState,
        viewModel = viewModel,
        showAddDialog = showAddDialog,
        editingSessionId = editingSessionId,
        sessionDataList = sessionDataList,
        showAddActionDialog = showAddActionDialog,
        groups = groups,
    )

    val pendingManagementSessionId = routeState.pendingManagementSessionId
    if (pendingManagementSessionId != null) {
        val pendingSession = sessionDataList.find { it.id == pendingManagementSessionId }
        val loadingMessage =
            when (managementConnectStatus) {
                is ManagementConnectStatus.Connecting ->
                    ManagementTexts.text("正在连接 ${pendingSession?.name ?: "目标设备"}", "Connecting to ${pendingSession?.name ?: "target device"}")
                else -> ManagementTexts.text("正在准备管理功能", "Preparing management")
            }
        ManagementLoadingDialog(
            title = ManagementTexts.text("管理功能", "Management"),
            message = loadingMessage,
        )
    }
}

@Composable
private fun RequestNotificationPermissionEffect(context: Context) {
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { }

    LaunchedEffect(context) {
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
) {
    AlertDialog(
        onDismissRequest = {},
        showActionBar = false,
        widthRatio = 0.72f,
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        title = { Text(title) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(
                    modifier = androidx.compose.ui.Modifier.size(22.dp),
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
    selectedAutomationGroupPath: String,
) {
    val txtTitle = rememberText(SessionTexts.MAIN_TITLE_SESSIONS)
    val txtTabSessions = rememberText(SessionTexts.MAIN_TAB_SESSIONS)
    val txtTabActions = rememberText(SessionTexts.MAIN_TAB_ACTIONS)
    val txtAddSession = rememberText(SessionTexts.MAIN_ADD_SESSION)
    val txtAddAction = rememberText(SessionTexts.MAIN_ADD_ACTION)
    val selectedTab = routeState.selectedTab
    val isSessionTab = selectedTab == MainScreenTab.SESSIONS

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
                            contentDescription = ManagementTexts.text("设置", "Settings"),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isSessionTab) {
                                viewModel.showAddSessionDialog()
                            } else {
                                viewModel.showAddActionDialog()
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = if (isSessionTab) txtAddSession else txtAddAction,
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
                        .padding(
                            horizontal = MainScreenHeaderPadding,
                            vertical = MainScreenHeaderPadding,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(IosDesignTokens.compactSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MainScreenTabSelector(
                        selectedTab = selectedTab,
                        tabSessionsText = txtTabSessions,
                        tabActionsText = txtTabActions,
                        onTabSelected = routeState::selectTab,
                    )

                    CompactGroupSelector(
                        groups =
                            groups.filter {
                                it.type == if (isSessionTab) GroupType.SESSION else GroupType.AUTOMATION
                            },
                        selectedGroupPath =
                            if (isSessionTab) {
                                selectedGroupPath
                            } else {
                                selectedAutomationGroupPath
                            },
                        onGroupSelected = {
                            if (isSessionTab) {
                                viewModel.selectGroup(it)
                            } else {
                                viewModel.selectAutomationGroup(it)
                            }
                        },
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                when (selectedTab) {
                    MainScreenTab.SESSIONS ->
                        SessionsScreen(
                            viewModel = viewModel,
                            onManageSession = { sessionData ->
                                routeState.requestSessionManagement(sessionData.id)
                                viewModel.connectManagementSession(sessionData.id)
                            },
                        )
                    MainScreenTab.ACTIONS -> ActionsScreen(viewModel)
                }
            }

            PathBreadcrumb(
                selectedGroupPath =
                    if (isSessionTab) {
                        selectedGroupPath
                    } else {
                        selectedAutomationGroupPath
                    },
            )
        }
    }
}

@Composable
private fun MainScreenDialogs(
    routeState: MainScreenRouteState,
    viewModel: MainViewModel,
    showAddDialog: Boolean,
    editingSessionId: String?,
    sessionDataList: List<SessionData>,
    showAddActionDialog: Boolean,
    groups: List<DeviceGroup>,
) {
    if (showAddDialog) {
        val editingSession =
            editingSessionId?.let { id ->
                sessionDataList.find { it.id == id }
            }
        key(editingSessionId ?: "new") {
            AddSessionDialog(
                sessionData = editingSession,
                availableGroups = groups,
                onDismiss = viewModel::hideAddSessionDialog,
                onConfirm = viewModel::saveSessionData,
            )
        }
    }

    if (showAddActionDialog) {
        AddActionDialog(
            onDismiss = viewModel::hideAddActionDialog,
            onConfirm = viewModel::addAction,
        )
    }

    when (routeState.settingsDestination) {
        null -> Unit
        MainScreenSettingsDestination.ROOT -> {
            SettingsScreen(
                viewModel = viewModel,
                onBack = routeState::closeSettings,
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
    }
}

@Composable
private fun MainScreenTabSelector(
    selectedTab: MainScreenTab,
    tabSessionsText: String,
    tabActionsText: String,
    onTabSelected: (MainScreenTab) -> Unit,
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Box(
        modifier =
            Modifier
                .width(MainScreenTabSelectorWidth)
                .height(IosDesignTokens.segmentedControlHeight)
                .clip(RoundedCornerShape(IosDesignTokens.segmentedControlContainerCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(MainScreenTabSelectorInset),
    ) {
        MainScreenTabButton(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .width(MainScreenSessionsTabWidth),
            isDarkTheme = isDarkTheme,
            selected = selectedTab == MainScreenTab.SESSIONS,
            text = tabSessionsText,
            onClick = { onTabSelected(MainScreenTab.SESSIONS) },
        )

        MainScreenTabButton(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(MainScreenActionsTabWidth),
            isDarkTheme = isDarkTheme,
            selected = selectedTab == MainScreenTab.ACTIONS,
            text = tabActionsText,
            onClick = { onTabSelected(MainScreenTab.ACTIONS) },
        )
    }
}

@Composable
private fun MainScreenTabButton(
    modifier: Modifier,
    isDarkTheme: Boolean,
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(MainScreenTabButtonCornerRadius))
                .background(
                    if (selected) {
                        if (isDarkTheme) {
                            AppColors.darkIOSSelectedBackground
                        } else {
                            AppColors.iOSSelectedBackground
                        }
                    } else {
                        Color.Transparent
                    },
                ).then(
                    if (selected) {
                        Modifier.zIndex(1f)
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            colors =
                ButtonDefaults.textButtonColors(
                    contentColor =
                        if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                ),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
