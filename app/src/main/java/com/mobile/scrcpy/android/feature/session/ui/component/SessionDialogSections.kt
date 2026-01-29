package com.mobile.scrcpy.android.feature.session.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.PlaceholderTexts
import com.mobile.scrcpy.android.core.common.ScrcpyConstants
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.HelpIcon
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenu
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenuItem
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.codec.component.CodecMapper

/**
 * 远程设备配置区域
 */
@Composable
fun RemoteDeviceSection(
    state: SessionDialogState,
    availableGroups: List<DeviceGroup>,
    onUsbDeviceClick: () -> Unit,
    onGroupSelectorClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(SessionTexts.SECTION_REMOTE_DEVICE.get())
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                LabeledTextField(
                    label = SessionTexts.LABEL_SESSION_NAME.get(),
                    value = state.sessionName,
                    onValueChange = { state.sessionName = it },
                    placeholder = SessionTexts.PLACEHOLDER_SESSION_NAME.get(),
                    helpText = SessionTexts.HELP_SESSION_NAME.get(),
                )

                AppDivider()

                if (state.isUsbMode) {
                    // USB 模式：显示设备选择按钮
                    CompactClickableRow(
                        text = AdbTexts.USB_SELECT_DEVICE.get(),
                        trailingText =
                            if (state.usbSerialNumber.isNotBlank()) {
                                state.usbSerialNumber
                            } else {
                                AdbTexts.USB_NO_DEVICE_SELECTED.get()
                            },
                        onClick = onUsbDeviceClick,
                    )
                } else {
                    // TCP 模式：显示 IP 输入框
                    LabeledTextField(
                        label = SessionTexts.LABEL_HOST.get(),
                        value = state.host,
                        onValueChange = { newValue ->
                            state.host = newValue
                            // 检测输入 "usb" 触发 USB 设备选择
                            if (newValue.equals("usb", ignoreCase = true)) {
                                onUsbDeviceClick()
                            }
                        },
                        placeholder = PlaceholderTexts.HOST,
                        helpText = SessionTexts.HELP_HOST.get(),
                    )

                    AppDivider()

                    LabeledTextField(
                        label = SessionTexts.LABEL_PORT.get(),
                        value = state.port,
                        onValueChange = { state.port = it },
                        placeholder = PlaceholderTexts.PORT,
                        keyboardType = KeyboardType.Number,
                        helpText = SessionTexts.HELP_PORT.get(),
                    )
                }

                AppDivider()

                CompactClickableRow(
                    text = SessionTexts.GROUP_SELECT.get(),
                    trailingText = formatGroupDisplay(state.selectedGroupIds, availableGroups),
                    onClick = onGroupSelectorClick,
                    helpText = SessionTexts.HELP_SELECT_GROUP.get(),
                )
            }
        }
    }
}

/**
 * 连接选项配置区域
 */
@Composable
fun ConnectionOptionsSection(state: SessionDialogState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(SessionTexts.SECTION_CONNECTION_OPTIONS.get())
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            CompactSwitchRow(
                text = SessionTexts.SWITCH_FORCE_ADB.get(),
                checked = state.forceAdb,
                onCheckedChange = { state.forceAdb = it },
                helpText = SessionTexts.HELP_FORCE_ADB.get(),
            )
        }
    }
}

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
 * 其他选项区域（独立 Section）
 */
@Composable
fun OtherOptionsSection(state: SessionDialogState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(SessionTexts.SECTION_OTHER_OPTIONS.get())
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            OtherOptionsContent(state)
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
        label = SessionTexts.LABEL_BITRATE.get(),
        value = state.bitrate,
        onValueChange = { state.bitrate = it },
        placeholder = "500k、4m、8M",
        helpText = SessionTexts.HELP_BITRATE.get(),
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

    LabeledDropdownRow(
        label = SessionTexts.LABEL_VIDEO_CODEC.get(),
        trailingText = CodecMapper.toDisplayFormat(state.videoCodec),
        onClick = { state.showVideoCodecMenu = true },
        helpText = SessionTexts.HELP_VIDEO_CODEC.get(),
    ) {
        IOSStyledDropdownMenu(
            alignment = Alignment.TopCenter,
            expanded = state.showVideoCodecMenu,
            offset = DpOffset(0.dp, 66.dp),
            onDismissRequest = { state.showVideoCodecMenu = false },
        ) {
            ScrcpyConstants.VIDEO_CODECS.forEach { codec ->
                IOSStyledDropdownMenuItem(
                    text = CodecMapper.toDisplayFormat(codec),
                    onClick = {
                        state.videoCodec = codec
                        if (!CodecMapper.isEncoderMatchCodec(state.videoEncoder, codec)) {
                            state.videoEncoder = ""
                        }
                        state.showVideoCodecMenu = false
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
                state.videoEncoder.isNotEmpty() -> state.videoEncoder
                else -> SessionTexts.PLACEHOLDER_DEFAULT_ENCODER.get()
            },
        onClick = {
            if (state.hasValidDevice()) {
                state.showEncoderOptionsDialog = true
            }
        },
        showArrow = state.hasValidDevice(),
        helpText = SessionTexts.HELP_VIDEO_ENCODER.get(),
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

        LabeledDropdownRow(
            label = SessionTexts.LABEL_AUDIO_CODEC.get(),
            trailingText = CodecMapper.toDisplayFormat(state.audioCodec),
            onClick = { state.showAudioCodecMenu = true },
            helpText = SessionTexts.HELP_AUDIO_CODEC.get(),
        ) {
            IOSStyledDropdownMenu(
                expanded = state.showAudioCodecMenu,
                offset = DpOffset(0.dp, 66.dp),
                onDismissRequest = { state.showAudioCodecMenu = false },
            ) {
                listOf("aac", "opus", "flac", "raw").forEach { codec ->
                    IOSStyledDropdownMenuItem(
                        text = CodecMapper.toDisplayFormat(codec),
                        onClick = {
                            state.audioCodec = codec
                            if (!CodecMapper.isEncoderMatchCodec(state.audioEncoder, codec)) {
                                state.audioEncoder = ""
                            }
                            state.showAudioCodecMenu = false
                        },
                    )
                }
            }
        }

        AppDivider()

        CompactClickableRow(
            text = SessionTexts.LABEL_AUDIO_ENCODER.get(),
            trailingText =
                when {
                    !state.hasValidDevice() -> SessionTexts.ENCODER_ERROR_INPUT_HOST.get()
                    state.audioEncoder.isNotEmpty() -> state.audioEncoder
                    else -> SessionTexts.PLACEHOLDER_DEFAULT_AUDIO_ENCODER.get()
                },
            onClick = {
                if (state.hasValidDevice()) {
                    state.showAudioEncoderDialog = true
                }
            },
            showArrow = state.hasValidDevice(),
            helpText = SessionTexts.HELP_AUDIO_ENCODER.get(),
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

/**
 * 其他选项区域
 */
@Composable
private fun OtherOptionsContent(state: SessionDialogState) {
    CompactSwitchRow(
        text = SessionTexts.SWITCH_STAY_AWAKE.get(),
        checked = state.stayAwake,
        onCheckedChange = { state.stayAwake = it },
        helpText = SessionTexts.HELP_STAY_AWAKE.get(),
    )
    AppDivider()

    CompactSwitchRow(
        text = SessionTexts.SWITCH_TURN_SCREEN_OFF.get(),
        checked = state.turnScreenOff,
        onCheckedChange = { state.turnScreenOff = it },
        helpText = SessionTexts.HELP_TURN_SCREEN_OFF.get(),
    )
    AppDivider()

    CompactSwitchRow(
        text = SessionTexts.SWITCH_POWER_OFF_ON_CLOSE.get(),
        checked = state.powerOffOnClose,
        onCheckedChange = { state.powerOffOnClose = it },
        helpText = SessionTexts.HELP_POWER_OFF_ON_CLOSE.get(),
    )
    AppDivider()

    CompactSwitchRow(
        text = SessionTexts.SWITCH_KEEP_DEVICE_AWAKE.get(),
        checked = state.keepDeviceAwake,
        onCheckedChange = { state.keepDeviceAwake = it },
        helpText = SessionTexts.HELP_KEEP_DEVICE_AWAKE.get(),
    )
    AppDivider()

    CompactSwitchRow(
        text = SessionTexts.SWITCH_ENABLE_HARDWARE_DECODING.get(),
        checked = state.enableHardwareDecoding,
        onCheckedChange = { state.enableHardwareDecoding = it },
        helpText = SessionTexts.HELP_ENABLE_HARDWARE_DECODING.get(),
    )
    AppDivider()

    CompactSwitchRow(
        text = SessionTexts.SWITCH_FOLLOW_ORIENTATION.get(),
        checked = state.followRemoteOrientation,
        onCheckedChange = { state.followRemoteOrientation = it },
        helpText = SessionTexts.HELP_FOLLOW_ORIENTATION.get(),
    )

    AppDivider()

    CompactSwitchRow(
        text = SessionTexts.SWITCH_NEW_DISPLAY.get(),
        checked = state.showNewDisplay,
        onCheckedChange = { state.showNewDisplay = it },
        helpText = SessionTexts.HELP_NEW_DISPLAY.get(),
    )
}

/**
 * 格式化分组显示
 * 显示分组名称（最后一级），最多显示 3 个
 * 例如：
 * [] -> "未分组" / "Ungrouped"
 * ["id1"] -> "FRP"
 * ["id1", "id2"] -> "FRP, HZ"
 * ["id1", "id2", "id3"] -> "FRP, HZ, SH"
 * ["id1", "id2", "id3", "id4"] -> "FRP, HZ, SH +1"
 */
private fun formatGroupDisplay(
    selectedGroupIds: List<String>,
    availableGroups: List<DeviceGroup>,
): String {
    if (selectedGroupIds.isEmpty()) {
        return SessionTexts.GROUP_UNGROUPED.get()
    }

    val selectedGroups = availableGroups.filter { it.id in selectedGroupIds }
    val groupNames = selectedGroups.map { it.name }

    return when {
        groupNames.size <= 3 -> {
            groupNames.joinToString(", ")
        }

        else -> {
            val first3 = groupNames.take(3).joinToString(", ")
            val remaining = groupNames.size - 3
            "$first3 +$remaining"
        }
    }
}
