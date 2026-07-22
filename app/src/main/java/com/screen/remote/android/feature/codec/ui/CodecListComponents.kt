package com.screen.remote.android.feature.codec.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.core.i18n.CodecTexts
import com.screen.remote.android.feature.codec.model.CodecInfo

@Composable
fun CodecListItem(
    codec: CodecInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    showTestButton: Boolean = false,
    isTesting: Boolean = false,
    onTest: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onSelect)
                .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = codec.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = codec.type,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (codec.capabilities.isNotEmpty()) {
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = codec.capabilities,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showTestButton && onTest != null) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = CodecTexts.CODEC_TEST_BUTTON.get(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onTest),
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
fun CodecList(
    codecs: List<CodecInfo>,
    selectedCodec: String,
    onCodecSelect: (CodecInfo) -> Unit,
    showTestButton: Boolean = false,
    testingCodec: String? = null,
    onTest: ((CodecInfo) -> Unit)? = null,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            codecs.forEachIndexed { index, codec ->
                CodecListItem(
                    codec = codec,
                    isSelected = selectedCodec == codec.name,
                    onSelect = { onCodecSelect(codec) },
                    showTestButton = showTestButton,
                    isTesting = testingCodec == codec.name,
                    onTest =
                        if (onTest != null) {
                            { onTest(codec) }
                        } else {
                            null
                        },
                    enabled = testingCodec == null,
                )
                if (index < codecs.size - 1) {
                    AppDivider()
                }
            }
        }
    }
}
