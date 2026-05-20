package com.screen.remote.android.feature.device.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.AdbPairingConstants
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.feature.device.data.PairingHistoryItem
import com.screen.remote.android.feature.session.ui.component.LabeledTextField

@Composable
internal fun PairingInstructionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Text(
            text = AdbTexts.PAIRING_INSTRUCTION_CONTENT.get(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
internal fun PairingHistoryCard(
    history: List<PairingHistoryItem>,
    onClearHistory: () -> Unit,
    onDeleteHistory: (hostPort: String) -> Unit,
    onSelectHistory: (hostPort: String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            history.forEachIndexed { index, item ->
                if (index > 0) {
                    AppDivider()
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(AppDimens.listItemHeight)
                            .clickable { onSelectHistory(item.hostPort) }
                            .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.hostPort,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = item.getFormattedTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(
                        onClick = { onDeleteHistory(item.hostPort) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            AppDivider()

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(AppDimens.listItemHeight)
                        .clickable(onClick = onClearHistory)
                        .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = AdbTexts.PAIRING_HISTORY_CLEAR.get(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun PairingInputCard(
    hostPort: String,
    onHostPortChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    errorMessage: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LabeledTextField(
                label = AdbTexts.PAIRING_HOST_PORT_LABEL.get(),
                value = hostPort,
                onValueChange = onHostPortChange,
                placeholder = "192.168.1.100:12345",
                keyboardType = KeyboardType.Text,
            )

            AppDivider()

            LabeledTextField(
                label = AdbTexts.PAIRING_CODE_LABEL.get(),
                value = pairingCode,
                onValueChange = {
                    if (it.length <= AdbPairingConstants.PAIRING_CODE_LENGTH && it.all(Char::isDigit)) {
                        onPairingCodeChange(it)
                    }
                },
                placeholder = "123456",
                keyboardType = KeyboardType.Number,
            )

            if (errorMessage.isNotEmpty()) {
                AppDivider()
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
