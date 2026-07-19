package com.screen.remote.android.feature.session.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.designsystem.component.IOSStyledDropdownMenu
import com.screen.remote.android.core.designsystem.component.IOSStyledDropdownMenuItem
import com.screen.remote.android.core.domain.model.ScrcpySession
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.i18n.SessionTexts

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionCard(
    session: ScrcpySession,
    sessionData: SessionData?,
    index: Int,
    isConnected: Boolean = false,
    endpointStatus: SessionEndpointStatus = SessionEndpointStatus.UNAVAILABLE,
    displayTransport: ConnectionTransport = ConnectionTransport.TCP,
    isConnecting: Boolean = false,
    onClick: () -> Unit = {},
    onConnect: () -> Unit = {},
    onManage: () -> Unit = {},
    onEdit: () -> Unit = {},
    onCopy: (SessionData) -> Unit = {},
    isResettingConnection: Boolean = false,
    onResetConnection: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val cardColor = getCardColorByIndex(index)
    val connectionResetEnabled = !isConnecting && !isResettingConnection
    var showMenu by remember { mutableStateOf(false) }
    var menuOffsetX by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val txtClickToConnect = rememberText(SessionTexts.SESSION_CLICK_TO_CONNECT)
    val txtConnected = rememberText(SessionTexts.SESSION_CONNECTED)
    val txtEditSession = rememberText(SessionTexts.SESSION_EDIT)
    val txtDeleteSession = rememberText(SessionTexts.SESSION_DELETE_SESSION)
    val txtConnect = rememberText(SessionTexts.SESSION_CONNECT)
    val txtCopySession = rememberText(SessionTexts.SESSION_COPY)
    val txtManage = rememberText(SessionTexts.SESSION_MANAGE)
    val txtGameModeBadge = rememberText(SessionTexts.SESSION_GAME_MODE_BADGE)
    val txtResetConnection = rememberText(SessionTexts.SESSION_RESET_CONNECTION)

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (!isConnecting) {
                                onClick()
                            }
                        },
                        onLongPress = { offset ->
                            if (!isConnecting) {
                                with(density) {
                                    menuOffsetX = offset.x.toDp() - (size.width / 2f).toDp() + 25.dp
                                }
                                showMenu = true
                            }
                        },
                    )
                },
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = cardColor,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(10.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            SessionCardStatusBadge(
                modifier = Modifier.align(Alignment.TopEnd),
                displayTransport = displayTransport,
                endpointStatus = endpointStatus,
            )

            if (sessionData?.config?.gameMode == true) {
                SessionGameModeBadge(
                    modifier = Modifier.align(Alignment.CenterStart),
                    label = txtGameModeBadge,
                )
            }

            Text(
                text = if (isConnected) txtConnected else txtClickToConnect,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomStart),
                color = Color.White.copy(alpha = 0.9f),
            )

            if (sessionData != null) {
                IconButton(
                    onClick = onResetConnection,
                    enabled = connectionResetEnabled,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(32.dp),
                ) {
                    if (isResettingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White.copy(alpha = 0.9f),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = txtResetConnection,
                            tint = Color.White.copy(alpha = if (connectionResetEnabled) 0.9f else 0.4f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (showMenu && sessionData != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(menuOffsetX, 50.dp),
                ) {
                    IOSStyledDropdownMenu(
                        offset = DpOffset.Zero,
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        IOSStyledDropdownMenuItem(
                            text = txtConnect,
                            onClick = {
                                showMenu = false
                                onConnect()
                            },
                        )
                        IOSStyledDropdownMenuItem(
                            text = txtManage,
                            onClick = {
                                showMenu = false
                                onManage()
                            },
                        )
                        IOSStyledDropdownMenuItem(
                            text = txtEditSession,
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                        )
                        IOSStyledDropdownMenuItem(
                            text = txtCopySession,
                            onClick = {
                                showMenu = false
                                onCopy(sessionData)
                            },
                        )
                        IOSStyledDropdownMenuItem(
                            text = txtDeleteSession,
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            textColor = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionGameModeBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.22f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SessionCardStatusBadge(
    modifier: Modifier = Modifier,
    displayTransport: ConnectionTransport,
    endpointStatus: SessionEndpointStatus,
) {
    val transportIcon =
        when (displayTransport) {
            ConnectionTransport.TCP -> Icons.Default.Wifi
            ConnectionTransport.USB -> Icons.Default.Usb
            ConnectionTransport.MDNS -> Icons.Default.Sensors
        }
    val transportLabel = displayTransport.name
    val statusColor =
        when (endpointStatus) {
            SessionEndpointStatus.ADB_CONNECTED -> Color(0xFF00C853)
            SessionEndpointStatus.DISCOVERED,
            SessionEndpointStatus.UNAVAILABLE,
            -> Color(0xFFFFD700)
        }
    val statusDescription =
        when (endpointStatus) {
            SessionEndpointStatus.ADB_CONNECTED -> "ADB connected"
            SessionEndpointStatus.DISCOVERED -> "Device discovered"
            SessionEndpointStatus.UNAVAILABLE -> "Unavailable"
        }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = transportIcon,
                    contentDescription = transportLabel,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
                Icon(
                    imageVector =
                        when (endpointStatus) {
                            SessionEndpointStatus.ADB_CONNECTED -> Icons.Default.FlashOn
                            SessionEndpointStatus.DISCOVERED -> Icons.Default.FlashOn
                            SessionEndpointStatus.UNAVAILABLE -> Icons.Default.Warning
                        },
                    contentDescription = statusDescription,
                    tint = statusColor,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

fun getCardColorByIndex(index: Int): Color {
    val colors =
        listOf(
            Color(0xFF4A90E2),
            Color(0xFFFF6B6B),
            Color(0xFF4ECDC4),
            Color(0xFFFFBE0B),
            Color(0xFF9B59B6),
            Color(0xFF2ECC71),
            Color(0xFFFF8C42),
            Color(0xFF3498DB),
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
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = txtNoSessions,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = txtEmptyHint,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
