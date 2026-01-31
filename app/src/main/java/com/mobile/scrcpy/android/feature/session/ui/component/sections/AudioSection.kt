/**
 * 音频配置区域组件
 * 
 * 从 SessionDialogSections.kt 提取的音频配置相关 Composable 函数
 */
package com.mobile.scrcpy.android.feature.session.ui.component.sections

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.HelpIcon
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.session.ui.component.CompactClickableRow
import com.mobile.scrcpy.android.feature.session.ui.component.CompactSwitchRow
import com.mobile.scrcpy.android.feature.session.ui.component.LabeledTextField
import com.mobile.scrcpy.android.feature.session.ui.component.SessionDialogState

/**
 * 音频配置区域（独立 Section）
 */
@SuppressLint("DefaultLocale")
@Composable
fun AudioConfigSection(state: SessionDialogState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(SessionTexts.SECTION_AUDIO_CONFIG.get())
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            AudioConfigContent(state)
        }
    }
}

/**
 * 音频配置内容
 */
@SuppressLint("DefaultLocale")
@Composable
private fun AudioConfigContent(state: SessionDialogState) {
    CompactSwitchRow(
        text = SessionTexts.SWITCH_ENABLE_AUDIO.get(),
        checked = state.enableAudio,
        onCheckedChange = { state.enableAudio = it },
        helpText = SessionTexts.HELP_ENABLE_AUDIO.get(),
    )

    if (state.enableAudio) {
        AppDivider()

        LabeledTextField(
            label = SessionTexts.LABEL_AUDIO_BITRATE.get(),
            value = state.audioBitrate,
            onValueChange = { state.audioBitrate = it },
            placeholder = "128k、192k、256k",
            helpText = SessionTexts.HELP_AUDIO_BITRATE.get(),
        )

        AppDivider()

        LabeledTextField(
            label = SessionTexts.LABEL_AUDIO_BUFFER.get(),
            value = state.audioBufferMs,
            onValueChange = { state.audioBufferMs = it },
            placeholder = "50、120",
            keyboardType = KeyboardType.Number,
            helpText = SessionTexts.HELP_AUDIO_BUFFER.get(),
        )

        AppDivider()

        CompactClickableRow(
            text = SessionTexts.LABEL_AUDIO_ENCODER.get(),
            trailingText =
                when {
                    !state.hasValidDevice() -> SessionTexts.ENCODER_ERROR_INPUT_HOST.get()
                    state.userAudioEncoder.isNotEmpty() -> state.userAudioEncoder
                    else -> SessionTexts.LABEL_DEFAULT.get()
                },
            onClick = {
                if (state.hasValidDevice()) {
                    state.showAudioEncoderDialog = true
                }
            },
            helpText = SessionTexts.HELP_AUDIO_ENCODER.get(),
        )

        AppDivider()

        CompactClickableRow(
            text = SessionTexts.LABEL_AUDIO_DECODER.get(),
            trailingText =
                state.userAudioDecoder.ifEmpty {
                    SessionTexts.LABEL_DEFAULT.get()
                },
            onClick = { state.showAudioDecoderSelector = true },
            helpText = SessionTexts.HELP_AUDIO_DECODER.get(),
        )

        AppDivider()

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppDimens.listItemHeight)
                    .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.widthIn(min = 30.dp, max = 120.dp).wrapContentWidth(),
            ) {
                Text(
                    SessionTexts.LABEL_AUDIO_VOLUME.get(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                HelpIcon(helpText = SessionTexts.HELP_AUDIO_VOLUME.get())
            }
            Slider(
                value = state.audioVolume,
                onValueChange = { state.audioVolume = it },
                valueRange = 0.1f..2.0f,
                steps = 18,
                modifier = Modifier.weight(1f),
                colors =
                    SliderDefaults.colors(
                        thumbColor = Color(0xFF007AFF),
                        activeTrackColor = Color(0xFF007AFF),
                        inactiveTrackColor = Color(0xFFE5E5EA),
                    ),
            )
            Text(
                "${String.format("%.1f", state.audioVolume)}x",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(50.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}
