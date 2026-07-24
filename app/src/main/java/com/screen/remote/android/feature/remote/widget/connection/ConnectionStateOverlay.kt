package com.screen.remote.android.feature.remote.widget.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.constants.AppTextSizes
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.designsystem.component.MessageList
import com.screen.remote.android.core.designsystem.component.MessageListState
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionState

@Composable
fun ConnectionStateOverlay(
    connectionState: ConnectionState,
    sessionName: String,
    messageListState: MessageListState,
    onReconnect: () -> Unit,
    onClose: () -> Unit,
) {
    // 调试日志
    // LogManager.d(LogTags.REMOTE_DISPLAY, "ConnectionStateOverlay: state=${connectionState::class.simpleName}")

    when {
        connectionState is ConnectionState.Connecting ||
            connectionState is ConnectionState.Reconnecting ||
            connectionState !is ConnectionState.Connected &&
            connectionState !is ConnectionState.Error -> {
            ConnectionProgressBox {
                MessageList(
                    state = messageListState,
                    title =
                        when (connectionState) {
                            is ConnectionState.Reconnecting -> "Reconnecting..."
                            is ConnectionState.Connecting -> CommonTexts.STATUS_CONNECTING.get()
                            else -> CommonTexts.STATUS_CONNECTING.get()
                        },
                )
            }
        }

        connectionState is ConnectionState.Error -> {
            val displaySessionName = sessionName.trim()
            ConnectionActionOverlay(
                title =
                    if (displaySessionName.isNotEmpty()) {
                        rememberText(RemoteTexts.REMOTE_SESSION_CONNECTION_FAILED_TITLE).format(displaySessionName)
                    } else {
                        rememberText(CommonTexts.CONNECTION_FAILED_TITLE)
                    },
                message = connectionFailureDisplayMessage(connectionState.message, displaySessionName),
                confirmText = rememberText(CommonTexts.BUTTON_RECONNECT),
                dismissText = rememberText(CommonTexts.BUTTON_CANCEL_CONNECTION),
                onConfirm = onReconnect,
                onDismiss = onClose,
            )
        }
    }
}

internal fun connectionFailureDisplayMessage(
    message: String,
    sessionName: String,
): String {
    val replacement = sessionName.ifBlank { "session" }
    return message
        .replace(BRACKETED_IPV4_ENDPOINT, replacement)
        .replace(IPV4_ENDPOINT, replacement)
        .replace(BRACKETED_IPV6_ENDPOINT, replacement)
}

private val BRACKETED_IPV4_ENDPOINT =
    Regex("""\[(?:(?:tcp|udp):)?(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?]""", RegexOption.IGNORE_CASE)
private val IPV4_ENDPOINT =
    Regex("""(?:(?:tcp|udp):)?(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?""", RegexOption.IGNORE_CASE)
private val BRACKETED_IPV6_ENDPOINT =
    Regex("""\[(?:(?:tcp|udp):)?[0-9a-f:.%]+](?::\d+)?""", RegexOption.IGNORE_CASE)

@Composable
fun ConnectionActionOverlay(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 85.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            shape = RoundedCornerShape(24.dp),
                        ).padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onConfirm,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF007AFF),
                            ),
                    ) {
                        Text(
                            text = confirmText,
                            fontSize = AppTextSizes.subtitle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Text(
                            text = dismissText,
                            fontSize = AppTextSizes.subtitle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
