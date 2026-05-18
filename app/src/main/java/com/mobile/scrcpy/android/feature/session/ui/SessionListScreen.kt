package com.mobile.scrcpy.android.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.manager.LanguageManager
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.common.util.formatHostForAuthority
import com.mobile.scrcpy.android.core.data.repository.SessionData
import com.mobile.scrcpy.android.core.domain.model.ScrcpySession
import com.mobile.scrcpy.android.core.domain.model.SessionColor
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.remote.presentation.ConnectStatus
import com.mobile.scrcpy.android.feature.session.viewmodel.ManagementConnectStatus
import com.mobile.scrcpy.android.feature.session.viewmodel.MainViewModel

@Composable
fun SessionsScreen(
    viewModel: MainViewModel,
    onManageSession: (SessionData) -> Unit = {},
) {
    val filteredSessions by viewModel.filteredSessions.collectAsState()
    val connectStatus by viewModel.connectStatus.collectAsState()
    val managementConnectStatus by viewModel.managementConnectStatus.collectAsState()
    val connectedSessionId by viewModel.connectedSessionId.collectAsState()
    var sessionToDelete by remember { mutableStateOf<ScrcpySession?>(null) }

    SessionDeleteDialog(
        sessionToDelete = sessionToDelete,
        onConfirmDelete = { session ->
            viewModel.removeSession(session.id)
            sessionToDelete = null
        },
        onDismiss = { sessionToDelete = null },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        if (filteredSessions.isEmpty()) {
            EmptySessionsView()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(filteredSessions) { index, sessionData ->
                    SessionCard(
                        session = sessionCardModel(sessionData, connectedSessionId == sessionData.id),
                        sessionData = sessionData,
                        index = index,
                        isConnected = connectedSessionId == sessionData.id,
                        isConnecting =
                            (connectStatus is ConnectStatus.Connecting &&
                                (connectStatus as? ConnectStatus.Connecting)?.sessionId == sessionData.id) ||
                                (managementConnectStatus is ManagementConnectStatus.Connecting &&
                                    (managementConnectStatus as? ManagementConnectStatus.Connecting)?.sessionId == sessionData.id),
                        onClick = { viewModel.connectSession(sessionData.id) },
                        onConnect = { viewModel.connectSession(sessionData.id) },
                        onManage = { onManageSession(sessionData) },
                        onEdit = { viewModel.showEditSessionDialog(sessionData.id) },
                        onCopy = { data -> viewModel.copySession(data) },
                        onDelete = {
                            sessionToDelete = sessionCardModel(sessionData, isConnected = false)
                        },
                    )
                }
            }
        }
    }
}

fun buildUrlScheme(sessionData: SessionData): String {
    val params = mutableListOf<String>()

    if (sessionData.maxSize.isNotBlank()) {
        params.add("max-size=${sessionData.maxSize}")
    }
    if (sessionData.videoBitrate.isNotBlank()) {
        params.add("video-bit-rate=${sessionData.videoBitrate}")
    }
    if (sessionData.forceAdb) {
        params.add("force-adb-forward=true")
    }
    if (sessionData.stayAwake) {
        params.add("stay-awake=true")
    }
    if (sessionData.turnScreenOff) {
        params.add("turn-screen-off=true")
    }
    if (sessionData.powerOffOnClose) {
        params.add("power-off-on-close=true")
    }
    if (sessionData.enableAudio) {
        params.add("enable-audio=true")
    }

    val port = if (sessionData.port.isNotBlank()) ":${sessionData.port}" else ""
    val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""

    return "scrcpy2://${formatHostForAuthority(sessionData.host)}$port$query"
}

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
                if (LanguageManager.isChinese()) {
                    "确定要删除会话 \"${sessionToDelete.name}\" 吗？"
                } else {
                    "Are you sure you want to delete session \"${sessionToDelete.name}\"?"
                },
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
        hasWifi = sessionData.host.isNotBlank(),
        hasWarning = false,
    )
