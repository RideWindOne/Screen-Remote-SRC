/**
 * 视频配置区域组件
 * 
 * 从 SessionDialogSections.kt 提取的视频配置相关 Composable 函数
 */
package com.mobile.scrcpy.android.feature.session.ui.component.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenu
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenuItem
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.session.ui.component.CompactClickableRow
import com.mobile.scrcpy.android.feature.session.ui.component.CompactSwitchRow
import com.mobile.scrcpy.android.feature.session.ui.component.LabeledDropdownRow
import com.mobile.scrcpy.android.feature.session.ui.component.LabeledTextField
import com.mobile.scrcpy.android.feature.session.ui.component.SessionDialogState

/**
 * 视频配置区域（独立 Section）
 */
@Composable
fun VideoConfigSection(state: SessionDialogState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(SessionTexts.SECTION_VIDEO_CONFIG.get())
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            VideoConfigContent(state)
        }
    }
}

/**
 * 视频配置内容
 */
@Composable
private fun VideoConfigContent(state: SessionDialogState) {
    LabeledTextField(
        label = SessionTexts.LABEL_MAX_SIZE.get(),
        value = state.maxSize,
        onValueChange = { state.maxSize = it },
        placeholder = "720、1080",
        keyboardType = KeyboardType.Number,
        helpText = SessionTexts.HELP_MAX_SIZE.get(),
    )

    AppDivider()

    LabeledTextField(
        label = SessionTexts.LABEL_VIDEO_BITRATE.get(),
        value = state.videoBitrate,
        onValueChange = { state.videoBitrate = it },
        placeholder = "500k、4m、8M",
        helpText = SessionTexts.HELP_VIDEO_BITRATE.get(),
    )

    AppDivider()

    LabeledTextField(
        label = SessionTexts.LABEL_MAX_FPS.get(),
        value = state.maxFps,
        onValueChange = { state.maxFps = it },
        placeholder = "15、30、60",
        keyboardType = KeyboardType.Number,
        helpText = SessionTexts.HELP_MAX_FPS.get(),
    )
    AppDivider()

    LabeledTextField(
        label = SessionTexts.LABEL_VIDEO_BUFFER.get(),
        value = state.videoBufferMs,
        onValueChange = { state.videoBufferMs = it },
        placeholder = "0、33、50",
        keyboardType = KeyboardType.Number,
        helpText = SessionTexts.HELP_VIDEO_BUFFER.get(),
    )

    AppDivider()

    LabeledDropdownRow(
        label = SessionTexts.LABEL_KEY_FRAME_INTERVAL.get(),
        trailingText = "${state.keyFrameInterval}s",
        onClick = { state.showKeyFrameIntervalMenu = true },
        helpText = SessionTexts.HELP_KEY_FRAME_INTERVAL.get(),
    ) {
        IOSStyledDropdownMenu(
            alignment = Alignment.TopCenter,
            offset = DpOffset(0.dp, 66.dp),
            expanded = state.showKeyFrameIntervalMenu,
            onDismissRequest = { state.showKeyFrameIntervalMenu = false },
        ) {
            listOf(1, 2, 3, 5).forEach { interval ->
                IOSStyledDropdownMenuItem(
                    text = "${interval}s",
                    onClick = {
                        state.keyFrameInterval = interval
                        state.showKeyFrameIntervalMenu = false
                    },
                )
            }
        }
    }

    AppDivider()

    CompactClickableRow(
        text = SessionTexts.LABEL_VIDEO_ENCODER.get(),
        trailingText =
            when {
                !state.hasValidDevice() -> SessionTexts.ENCODER_ERROR_INPUT_HOST.get()
                state.userVideoEncoder.isNotEmpty() -> state.userVideoEncoder
                else -> SessionTexts.LABEL_DEFAULT.get()
            },
        onClick = {
            if (state.hasValidDevice()) {
                state.showEncoderOptionsDialog = true
            }
        },
        helpText = SessionTexts.HELP_VIDEO_ENCODER.get(),
    )

    AppDivider()

    CompactClickableRow(
        text = SessionTexts.LABEL_VIDEO_DECODER.get(),
        trailingText =
            state.userVideoDecoder.ifEmpty {
                SessionTexts.LABEL_DEFAULT.get()
            },
        onClick = { state.showVideoDecoderSelector = true },
        helpText = SessionTexts.HELP_VIDEO_DECODER.get(),
    )

    AppDivider()

    CompactSwitchRow(
        text = SessionTexts.SWITCH_FULL_SCREEN.get(),
        checked = state.useFullScreen,
        onCheckedChange = { state.useFullScreen = it },
        helpText = SessionTexts.HELP_USE_FULL_SCREEN.get(),
    )

//    AppDivider()
//
//    CompactSwitchRow(
//        text = SessionTexts.SWITCH_NEW_DISPLAY.get(),
//        checked = state.showNewDisplay,
//        onCheckedChange = { state.showNewDisplay = it }
//    )
}
