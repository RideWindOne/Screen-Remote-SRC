package com.screen.remote.android.feature.remote.widget.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.designsystem.component.MessageList
import com.screen.remote.android.core.designsystem.component.MessageListState
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionState

@Composable
fun ConnectionStateOverlay(
    connectionState: ConnectionState,
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
                            text = rememberText(CommonTexts.CONNECTION_FAILED_TITLE),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = connectionState.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Button(
                                onClick = onReconnect,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF007AFF),
                                    ),
                            ) {
                                Text(
                                    rememberText(CommonTexts.BUTTON_RECONNECT),
                                )
                            }
                            OutlinedButton(
                                onClick = onClose,
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                            ) {
                                Text(
                                    rememberText(CommonTexts.BUTTON_CANCEL_CONNECTION),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
