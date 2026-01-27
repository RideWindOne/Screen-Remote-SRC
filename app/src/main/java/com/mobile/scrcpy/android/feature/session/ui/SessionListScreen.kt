package com.mobile.scrcpy.android.feature.session.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.manager.LanguageManager
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.domain.model.ScrcpySession
import com.mobile.scrcpy.android.feature.remote.viewmodel.ConnectStatus
import com.mobile.scrcpy.android.feature.session.viewmodel.MainViewModel
import com.mobile.scrcpy.android.feature.session.data.repository.SessionData
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenu
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenuItem

import com.mobile.scrcpy.android.core.i18n.SessionTexts
@Composable
fun SessionsScreen(viewModel: MainViewModel) {
    val sessionDataList by viewModel.sessionDataList.collectAsState()
    val filteredSessions by viewModel.filteredSessions.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroupPath by viewModel.selectedGroupPath.collectAsState()
    val connectStatus by viewModel.connectStatus.collectAsState()
    val connectedSessionId by viewModel.connectedSessionId.collectAsState()
    val context = LocalContext.current

    var sessionToDelete by remember { mutableStateOf<ScrcpySession?>(null) }
    
    // 双语文本
    val txtConfirmDelete = rememberText(SessionTexts.SESSION_CONFIRM_DELETE)
    val txtDelete = rememberText(SessionTexts.SESSION_DELETE)
    val txtCancel = rememberText(SessionTexts.SESSION_CANCEL)
    val txtUrlCopied = rememberText(SessionTexts.SESSION_URL_COPIED)

    // 删除确认对话框
    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text(txtConfirmDelete) },
            text = { 
                val message = if (LanguageManager.isChinese()) {
                    "确定要删除会话 \"${session.name}\" 吗？"
                } else {
                    "Are you sure you want to delete session \"${session.name}\"?"
                }
                Text(message)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSession(session.id)
                    sessionToDelete = null
                }) {
                    Text(txtDelete)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text(txtCancel)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (filteredSessions.isEmpty()) {
            EmptySessionsView()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(filteredSessions) { index, sessionData ->
                    SessionCard(
                        session = ScrcpySession(
                            id = sessionData.id,
                            name = sessionData.name,
                            color = com.mobile.scrcpy.android.core.domain.model.SessionColor.valueOf(sessionData.color),
                            isConnected = connectedSessionId == sessionData.id,
                            hasWifi = sessionData.host.isNotBlank(),
                            hasWarning = false
                        ),
                        sessionData = sessionData,
                        index = index,
                        isConnected = connectedSessionId == sessionData.id,
                        isConnecting = connectStatus is ConnectStatus.Connecting &&
                                (connectStatus as? ConnectStatus.Connecting)?.sessionId == sessionData.id,
                        onClick = {
                            viewModel.connectSession(sessionData.id)
                        },
                        onConnect = {
                            viewModel.connectSession(sessionData.id)
                        },
                        onEdit = { viewModel.showEditSessionDialog(sessionData.id) },
                        onCopyUrl = { data ->
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val url = buildUrlScheme(data)
                            val clip = ClipData.newPlainText("URL Scheme", url)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(
                                context,
                                txtUrlCopied,
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        onDelete = { sessionToDelete = ScrcpySession(
                            id = sessionData.id,
                            name = sessionData.name,
                            color = com.mobile.scrcpy.android.core.domain.model.SessionColor.valueOf(sessionData.color),
                            isConnected = false,
                            hasWifi = false,
                            hasWarning = false
                        ) }
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
    if (sessionData.bitrate.isNotBlank()) {
        params.add("video-bit-rate=${sessionData.bitrate}")
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

    return "scrcpy2://${sessionData.host}${port}${query}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionCard(
    session: ScrcpySession,
    sessionData: SessionData?,
    index: Int,
    isConnected: Boolean = false,
    isConnecting: Boolean = false,
    onClick: () -> Unit = {},
    onConnect: () -> Unit = {},
    onEdit: () -> Unit = {},
    onCopyUrl: (SessionData) -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val cardColor = getCardColorByIndex(index)
    var showMenu by remember { mutableStateOf(false) }
    
    // 双语文本
    val txtClickToConnect = rememberText(SessionTexts.SESSION_CLICK_TO_CONNECT)
    val txtConnected = rememberText(SessionTexts.SESSION_CONNECTED)
    val txtEditSession = rememberText(SessionTexts.SESSION_EDIT)
    val txtDeleteSession = rememberText(SessionTexts.SESSION_DELETE_SESSION)
    val txtConnect = rememberText(SessionTexts.SESSION_CONNECT)
    val txtCopySession = rememberText(SessionTexts.SESSION_COPY)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .combinedClickable(
                enabled = !isConnecting,
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp, 10.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "WiFi",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        if (isConnected) {
                            Text(
                                text = "${(0..20).random()}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF00FF00),
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Connected",
                                tint = Color(0xFF00FF00),
                                modifier = Modifier.size(13.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Disconnected",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            // 底部提示文字
            Text(
                text = if (isConnected) txtConnected else txtClickToConnect,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomStart),
                color = Color.White.copy(alpha = 0.9f)
            )

            // 长按菜单
            if (showMenu && sessionData != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 10.dp)
                ) {
                    IOSStyledDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        IOSStyledDropdownMenuItem(
                            text = txtConnect,
                            onClick = {
                                showMenu = false
                                onConnect()
                            }
                        )
                        IOSStyledDropdownMenuItem(
                            text = txtEditSession,
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        IOSStyledDropdownMenuItem(
                            text = txtCopySession,
                            onClick = {
                                showMenu = false
                                onCopyUrl(sessionData)
                            }
                        )
                        IOSStyledDropdownMenuItem(
                            text = txtDeleteSession,
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            textColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

fun getCardColorByIndex(index: Int): Color {
    val colors = listOf(
        Color(0xFF4A90E2),
        Color(0xFFFF6B6B),
        Color(0xFF4ECDC4),
        Color(0xFFFFBE0B),
        Color(0xFF9B59B6),
        Color(0xFF2ECC71),
        Color(0xFFFF8C42),
        Color(0xFF3498DB)
    )
    return colors[index % colors.size]
}

@Composable
fun EmptySessionsView() {
    val txtNoSessions = rememberText(SessionTexts.SESSION_NO_SESSIONS)
    val txtEmptyHint = rememberText(SessionTexts.SESSION_EMPTY_HINT)
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = txtNoSessions,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = txtEmptyHint,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

//fun SessionColor.toComposeColor(): Color = when (this) {
//    SessionColor.BLUE -> Color(0xFF4A90E2)
//    SessionColor.RED -> Color(0xFFE85D75)
//    SessionColor.GREEN -> Color(0xFF50C878)
//    SessionColor.ORANGE -> Color(0xFFFF9F40)
//    SessionColor.PURPLE -> Color(0xFF9B59B6)
//}


