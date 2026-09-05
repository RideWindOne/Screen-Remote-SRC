package com.screen.remote.android.feature.session.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.core.data.datastore.PreferencesManager
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.domain.model.AppSettings
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.ScrcpySession
import com.screen.remote.android.core.domain.model.SessionColor
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.feature.remote.presentation.ConnectStatus
import com.screen.remote.android.feature.remote.notification.NotificationMonitorManager
import com.screen.remote.android.feature.session.viewmodel.MainViewModel
import com.screen.remote.android.feature.session.viewmodel.ManagementConnectStatus
import com.screen.remote.android.feature.settings.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import com.screen.remote.android.core.designsystem.component.IOSAlertDialog as AlertDialog

@Composable
fun SessionsScreen(
    viewModel: MainViewModel,
    onManageSession: (SessionData) -> Unit = {},
    onResumeConnectedSession: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesManager = remember { PreferencesManager(context) }
    val settingsViewModel: SettingsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = SettingsViewModel.provideFactory(preferencesManager),
        )
    val settings by settingsViewModel.settings.collectAsState()
    val filteredSessions by viewModel.filteredSessions.collectAsState()
    val connectStatus by viewModel.connectStatus.collectAsState()
    val managementConnectStatus by viewModel.managementConnectStatus.collectAsState()
    val connectedSessionId by viewModel.connectedSessionId.collectAsState()
    val mdnsSessionPresence by viewModel.mdnsSessionPresence.collectAsState()
    val usbDevices by viewModel.usbDevices.collectAsState()
    val connectedAdbDevices by viewModel.connectedAdbDevices.collectAsState()
    val connectedAdbDeviceIds =
        connectedAdbDevices.mapTo(linkedSetOf()) { it.deviceId }
    val discoveredDeviceIds =
        buildSet {
            mdnsSessionPresence.onlineMdnsSerials.forEach(::add)
            usbDevices.toDeviceIdentifiers().forEach(::add)
        }
    val confirmingDeviceIds = mdnsSessionPresence.confirmingMdnsSerials
    var sessionToDelete by remember { mutableStateOf<ScrcpySession?>(null) }
    var resettingConnectionSessionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val connectionResetSuccess = rememberText(SessionTexts.SESSION_RESET_CONNECTION_SUCCESS)
    val connectionResetFailed = rememberText(SessionTexts.SESSION_RESET_CONNECTION_FAILED)
    val sessionUrlCopied = rememberText(SessionTexts.SESSION_URL_COPIED)

    // 查询通知相关状态
    var showQueryNotificationDialog by remember { mutableStateOf(false) }
    var isQueryingNotifications by remember { mutableStateOf(false) }
    var queriedNotifications by remember { mutableStateOf<List<com.screen.remote.android.feature.remote.notification.DeviceNotification>>(emptyList()) }
    var queryDeviceName by remember { mutableStateOf("") }

    SessionDeleteDialog(
        sessionToDelete = sessionToDelete,
        onConfirmDelete = { session ->
            viewModel.removeSession(session.id)
            sessionToDelete = null
        },
        onDismiss = { sessionToDelete = null },
    )

    // 查询通知结果对话框
    if (showQueryNotificationDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showQueryNotificationDialog = false
                queriedNotifications = emptyList()
            },
            title = {
                Text(
                    text = if (isQueryingNotifications) "正在查询 $queryDeviceName 的通知..." else "$queryDeviceName 的通知（${queriedNotifications.size}条）",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                if (isQueryingNotifications) {
                    Text("请稍候，正在连接设备并查询通知...")
                } else if (queriedNotifications.isEmpty()) {
                    Text("暂无通知")
                } else {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        queriedNotifications.forEach { notification ->
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(
                                    text = notification.title.ifBlank { "(无标题)" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                )
                                if (notification.text.isNotBlank()) {
                                    Text(
                                        text = notification.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = notification.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            androidx.compose.material3.HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showQueryNotificationDialog = false
                    queriedNotifications = emptyList()
                }) {
                    Text("关闭")
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (filteredSessions.isEmpty()) {
            EmptySessionsView()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(filteredSessions) { index, sessionData ->
                    val isRemoteConnected = connectedSessionId == sessionData.id
                    val isConnecting =
                        (connectStatus is ConnectStatus.Connecting &&
                            (connectStatus as? ConnectStatus.Connecting)?.sessionId == sessionData.id) ||
                            (managementConnectStatus is ManagementConnectStatus.Connecting &&
                                (managementConnectStatus as? ManagementConnectStatus.Connecting)?.sessionId == sessionData.id)
                    val badgeState =
                        resolveSessionBadgeState(
                            sessionData = sessionData,
                            connectedAdbDeviceIds = connectedAdbDeviceIds,
                            discoveredDeviceIds = discoveredDeviceIds,
                            confirmingDeviceIds = confirmingDeviceIds,
                        )

                    SessionCard(
                        session = sessionCardModel(sessionData, isRemoteConnected),
                        sessionData = sessionData,
                        index = index,
                        isConnected = isRemoteConnected,
                        endpointStatus = badgeState.status,
                        displayTransport = badgeState.displayTransport,
                        isConnecting = isConnecting,
                        isResettingConnection = sessionData.id in resettingConnectionSessionIds,
                        onClick = {
                            if (isRemoteConnected) {
                                onResumeConnectedSession()
                            } else {
                                viewModel.connectSession(sessionData.id)
                            }
                        },
                        onConnect = {
                            if (isRemoteConnected) {
                                onResumeConnectedSession()
                            } else {
                                viewModel.connectSession(sessionData.id)
                            }
                        },
                        onManage = { onManageSession(sessionData) },
                        onEdit = { viewModel.showEditSessionDialog(sessionData.id) },
                        onCopy = { data -> viewModel.copySession(data) },
                        onCopyUrl = { data ->
                            scope.launch {
                                val url = viewModel.createSessionUrl(data.id) ?: return@launch
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Screen Remote session URL", url))
                                Toast.makeText(context, sessionUrlCopied, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onResetConnection = {
                            if (sessionData.id !in resettingConnectionSessionIds) {
                                resettingConnectionSessionIds += sessionData.id
                                scope.launch {
                                    val result = viewModel.resetSessionConnectionAndDetection(sessionData.id)
                                    resettingConnectionSessionIds -= sessionData.id
                                    Toast.makeText(
                                        context,
                                        if (result.isSuccess) {
                                            connectionResetSuccess
                                        } else {
                                            result.exceptionOrNull()?.message
                                                ?.takeIf(String::isNotBlank)
                                                ?.let { "$connectionResetFailed: $it" }
                                                ?: connectionResetFailed
                                        },
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                        onDelete = {
                            sessionToDelete = sessionCardModel(sessionData, isConnected = false)
                        },
                        isNotificationMonitoring = NotificationMonitorManager.monitoringSessionId == sessionData.id,
                        onStartNotificationMonitor = {
                            NotificationMonitorManager.start(
                                context,
                                sessionData,
                                settings.notifyAllNotificationsOnStart,
                                settings.blockSystemNotifications,
                            )
                        },
                        onStopNotificationMonitor = {
                            NotificationMonitorManager.stop(context)
                        },
                        onQueryNotifications = {
                            queryDeviceName = sessionData.name
                            isQueryingNotifications = true
                            showQueryNotificationDialog = true
                            scope.launch {
                                val result = NotificationMonitorManager.queryNotifications(
                                    context,
                                    sessionData,
                                    settings.blockSystemNotifications,
                                )
                                queriedNotifications = result
                                isQueryingNotifications = false
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun List<com.screen.remote.android.infrastructure.adb.usb.UsbDeviceInfo>.toDeviceIdentifiers(): Set<String> =
    flatMap { device -> listOf(device.serialNumber, device.deviceName) }
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .mapTo(linkedSetOf(), DeviceTransportSerial::usb)

@Composable
private fun SessionDeleteDialog(
    sessionToDelete: ScrcpySession?,
    onConfirmDelete: (ScrcpySession) -> Unit,
    onDismiss: () -> Unit,
) {
    if (sessionToDelete == null) {
        return
    }

    val txtConfirmDelete = rememberText(SessionTexts.SESSION_CONFIRM_DELETE)
    val txtDelete = rememberText(SessionTexts.SESSION_DELETE)
    val txtCancel = rememberText(SessionTexts.SESSION_CANCEL)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(txtConfirmDelete) },
        text = {
            Text(
                SessionTexts.SESSION_CONFIRM_DELETE_MESSAGE.format(sessionToDelete.name),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirmDelete(sessionToDelete) }) {
                Text(txtDelete)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(txtCancel)
            }
        },
    )
}

private fun sessionCardModel(
    sessionData: SessionData,
    isConnected: Boolean,
): ScrcpySession =
    ScrcpySession(
        id = sessionData.id,
        name = sessionData.name,
        color = SessionColor.valueOf(sessionData.color),
        isConnected = isConnected,
        hasWifi = sessionData.toConnectionCandidates().any { it.transport != ConnectionTransport.USB },
        hasWarning = false,
    )
