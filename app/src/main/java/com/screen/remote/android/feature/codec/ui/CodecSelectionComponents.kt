package com.screen.remote.android.feature.codec.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.core.i18n.CodecTexts
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.feature.session.ui.component.CompactTextField

@Composable
fun CodecOptionsSection(
    selectedCodec: String,
    customCodecName: String,
    onDefaultSelected: () -> Unit,
    onCustomCodecChange: (String) -> Unit,
    placeholderText: String,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(AppDimens.listItemHeight)
                        .clickable(onClick = onDefaultSelected)
                        .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = SessionTexts.LABEL_DEFAULT.get(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector =
                        if (selectedCodec.isEmpty() && customCodecName.isEmpty()) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.RadioButtonUnchecked
                        },
                    contentDescription = null,
                    tint =
                        if (selectedCodec.isEmpty() && customCodecName.isEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                )
            }

            AppDivider()

            CompactTextField(
                value = customCodecName,
                onValueChange = onCustomCodecChange,
                placeholder = placeholderText,
                keyboardType = KeyboardType.Text,
            )
        }
    }
}

@Composable
fun EmptyCodecState(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CodecCountInfo(
    count: Int,
    codecType: String,
) {
    Text(
        text = "${CodecTexts.CODEC_TEST_FOUND_COUNT.get()} $count $codecType",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
