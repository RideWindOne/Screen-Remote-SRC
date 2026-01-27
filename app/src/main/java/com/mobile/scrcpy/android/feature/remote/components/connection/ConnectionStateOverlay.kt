package com.mobile.scrcpy.android.feature.remote.components.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionState
import com.mobile.scrcpy.android.core.designsystem.component.MessageList
import com.mobile.scrcpy.android.core.designsystem.component.MessageListState

import com.mobile.scrcpy.android.core.i18n.CommonTexts
@Composable
fun ConnectionStateOverlay(
    connectionState: ConnectionState,
    messageListState: MessageListState,
    onReconnect: () -> Unit,
    onClose: () -> Unit
) {
    when {
        connectionState is ConnectionState.Connecting ||
                connectionState is ConnectionState.Reconnecting ||
                connectionState !is ConnectionState.Connected &&
                connectionState !is ConnectionState.Error -> {
            ConnectionProgressBox {
                MessageList(
                    state = messageListState,
                    title = when (connectionState) {
                        is ConnectionState.Reconnecting -> "Reconnecting..."
                        else -> CommonTexts.STATUS_CONNECTING.get()
                    }
                )
            }
        }

        connectionState is ConnectionState.Error -> {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 85.dp)
                ) {
                    Text(
                        text = rememberText(CommonTexts.CONNECTION_FAILED_TITLE),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = connectionState.message,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onReconnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF007AFF)
                            )
                        ) {
                            Text(
                                rememberText(CommonTexts.BUTTON_RECONNECT)
                            )
                        }
                        OutlinedButton(
                            onClick = onClose,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                rememberText(CommonTexts.BUTTON_CANCEL_CONNECTION)
                            )
                        }
                    }
                }
            }
        }
    }
}
