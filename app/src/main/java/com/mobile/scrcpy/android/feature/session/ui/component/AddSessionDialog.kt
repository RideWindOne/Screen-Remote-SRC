package com.mobile.scrcpy.android.feature.session.ui.component

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.PlaceholderTexts
import com.mobile.scrcpy.android.core.data.repository.SessionData
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.DialogBottomSpacer
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.GroupSelectorDialog
import com.mobile.scrcpy.android.core.designsystem.component.HelpIcon
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenu
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenuItem
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CodecTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.codec.component.EncoderSelectionDialog
import com.mobile.scrcpy.android.feature.codec.component.EncoderType
import com.mobile.scrcpy.android.feature.codec.ui.AudioCodecSelectorScreen
import com.mobile.scrcpy.android.feature.codec.ui.VideoCodecSelectorScreen
import com.mobile.scrcpy.android.feature.codec.util.CodecUtils
import com.mobile.scrcpy.android.feature.device.ui.component.UsbDeviceSelectionDialog

private val SessionDialogSectionShape = RoundedCornerShape(8.dp)
private val KeyFrameIntervalMenuOffset = DpOffset(0.dp, 66.dp)
private val AudioVolumeRowHorizontalPadding = 10.dp
private val AudioVolumeLabelMinWidth = 30.dp
private val AudioVolumeLabelMaxWidth = 120.dp
private val AudioVolumeLabelSpacing = 6.dp
private val AudioVolumeValueWidth = 50.dp
private val SessionDialogAccentColor = Color(0xFF007AFF)
private val SessionDialogDividerColor = Color(0xFFE5E5EA)

@Composable
fun AddSessionDialog(
    sessionData: SessionData? = null,
    availableGroups: List<DeviceGroup>,
    onDismiss: () -> Unit,
    onConfirm: (SessionData) -> Unit,
) {
    val state = remember(sessionData) { SessionDialogState(sessionData) }

    AddSessionDialogContent(
        state = state,
        isEditMode = sessionData != null,
        availableGroups = availableGroups,
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm(state.toSessionData(sessionData?.id))
            onDismiss()
        },
    )

    AddSessionDialogOverlays(
        state = state,
        sessionId = sessionData?.id,
        availableGroups = availableGroups,
    )
}

@Composable
private fun AddSessionDialogContent(
    state: SessionDialogState,
    isEditMode: Boolean,
    availableGroups: List<DeviceGroup>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DialogPage(
        title =
            if (isEditMode) {
                SessionTexts.SESSION_EDIT.get()
            } else {
                SessionTexts.SESSION_ADD.get()
            },
        onDismiss = onDismiss,
        leftButtonText = SessionTexts.SESSION_CANCEL.get(),
        rightButtonText = SessionTexts.SESSION_SAVE.get(),
        onRightButtonClick = {
            if (state.validate()) {
                onConfirm()
            }
        },
        enableScroll = true,
        verticalSpacing = 8.dp,
    ) {
        RemoteDeviceSection(
            state = state,
            availableGroups = availableGroups,
            onUsbDeviceClick = { state.showUsbDeviceDialog = true },
            onGroupSelectorClick = { state.showGroupSelector = true },
        )
        ConnectionOptionsSection(state)
        VideoConfigSection(state)
        AudioConfigSection(state)
        OtherOptionsSection(state)
        DialogBottomSpacer()
    }
}

@Composable
private fun AddSessionDialogOverlays(
    state: SessionDialogState,
    sessionId: String?,
    availableGroups: List<DeviceGroup>,
) {
    val context = LocalContext.current

    VideoEncoderSelectionOverlay(
        state = state,
        sessionId = sessionId,
        context = context,
    )
    VideoDecoderSelectionOverlay(
        state = state,
        context = context,
    )
    AudioEncoderSelectionOverlay(
        state = state,
        sessionId = sessionId,
        context = context,
    )
    AudioDecoderSelectionOverlay(
        state = state,
        context = context,
    )
    UsbDeviceSelectionOverlay(state)
    GroupSelectionOverlay(
        state = state,
        availableGroups = availableGroups,
    )
}

@Composable
private fun SessionDialogSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = SessionDialogSectionShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            content()
        }
    }
}

@Composable
private fun RemoteDeviceSection(
    state: SessionDialogState,
    availableGroups: List<DeviceGroup>,
    onUsbDeviceClick: () -> Unit,
    onGroupSelectorClick: () -> Unit,
) {
    SessionDialogSection(title = SessionTexts.SECTION_REMOTE_DEVICE.get()) {
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
                CompactClickableRow(
                    text = AdbTexts.USB_SELECT_DEVICE.get(),
                    trailingText =
                        state.usbSerialNumber.ifBlank {
                            AdbTexts.USB_NO_DEVICE_SELECTED.get()
                        },
                    onClick = onUsbDeviceClick,
                    showArrow = true,
                )
            } else {
                LabeledTextField(
                    label = SessionTexts.LABEL_HOST.get(),
                    value = state.host,
                    onValueChange = { newValue ->
                        state.host = newValue
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
                showArrow = true,
            )
        }
    }
}

@Composable
private fun ConnectionOptionsSection(state: SessionDialogState) {
    SessionDialogSection(title = SessionTexts.SECTION_CONNECTION_OPTIONS.get()) {
        CompactSwitchRow(
            text = SessionTexts.SWITCH_FORCE_ADB.get(),
            checked = state.forceAdb,
            onCheckedChange = { state.forceAdb = it },
            helpText = SessionTexts.HELP_FORCE_ADB.get(),
        )
    }
}

@Composable
private fun VideoConfigSection(state: SessionDialogState) {
    SessionDialogSection(title = SessionTexts.SECTION_VIDEO_CONFIG.get()) {
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
                offset = KeyFrameIntervalMenuOffset,
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

        LabeledClickableRow(
            label = SessionTexts.LABEL_VIDEO_ENCODER.get(),
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
            showArrow = false,
        )

        AppDivider()

        LabeledClickableRow(
            label = SessionTexts.LABEL_VIDEO_DECODER.get(),
            trailingText = state.userVideoDecoder.ifEmpty { SessionTexts.LABEL_DEFAULT.get() },
            onClick = { state.showVideoDecoderSelector = true },
            helpText = SessionTexts.HELP_VIDEO_DECODER.get(),
            showArrow = false,
        )

        AppDivider()

        CompactSwitchRow(
            text = SessionTexts.SWITCH_FULL_SCREEN.get(),
            checked = state.useFullScreen,
            onCheckedChange = { state.useFullScreen = it },
            helpText = SessionTexts.HELP_USE_FULL_SCREEN.get(),
        )
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun AudioConfigSection(state: SessionDialogState) {
    SessionDialogSection(title = SessionTexts.SECTION_AUDIO_CONFIG.get()) {
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

            LabeledClickableRow(
                label = SessionTexts.LABEL_AUDIO_ENCODER.get(),
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
                showArrow = false,
            )

            AppDivider()

            LabeledClickableRow(
                label = SessionTexts.LABEL_AUDIO_DECODER.get(),
                trailingText = state.userAudioDecoder.ifEmpty { SessionTexts.LABEL_DEFAULT.get() },
                onClick = { state.showAudioDecoderSelector = true },
                helpText = SessionTexts.HELP_AUDIO_DECODER.get(),
                showArrow = false,
            )

            AppDivider()

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(AppDimens.listItemHeight)
                        .padding(horizontal = AudioVolumeRowHorizontalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AudioVolumeLabelSpacing),
                    modifier =
                        Modifier
                            .widthIn(min = AudioVolumeLabelMinWidth, max = AudioVolumeLabelMaxWidth)
                            .wrapContentWidth(),
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
                            thumbColor = SessionDialogAccentColor,
                            activeTrackColor = SessionDialogAccentColor,
                            inactiveTrackColor = SessionDialogDividerColor,
                        ),
                )
                Text(
                    "${String.format("%.1f", state.audioVolume)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(AudioVolumeValueWidth),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun OtherOptionsSection(state: SessionDialogState) {
    SessionDialogSection(title = SessionTexts.SECTION_OTHER_OPTIONS.get()) {
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
}

@Composable
private fun VideoEncoderSelectionOverlay(
    state: SessionDialogState,
    sessionId: String?,
    context: Context,
) {
    if (!state.showEncoderOptionsDialog) {
        return
    }

    EncoderSelectionDialog(
        encoderType = EncoderType.VIDEO,
        sessionId = sessionId,
        host = state.connectionTargetHost(),
        port = state.port,
        currentEncoder = state.userVideoEncoder,
        cachedEncoders = state.remoteVideoEncoders,
        onDismiss = { state.showEncoderOptionsDialog = false },
        onEncoderSelected = { encoder ->
            if (CodecUtils.isCodecProtocolMatch(encoder, state.userVideoDecoder, CodecUtils.CodecType.VIDEO)) {
                state.userVideoEncoder = encoder
            } else {
                showCodecProtocolMismatch(context)
            }
            state.showEncoderOptionsDialog = false
        },
        onEncodersDetected = { encoders ->
            state.remoteVideoEncoders = encoders
        },
    )
}

@Composable
private fun VideoDecoderSelectionOverlay(
    state: SessionDialogState,
    context: Context,
) {
    if (!state.showVideoDecoderSelector) {
        return
    }

    VideoCodecSelectorScreen(
        currentCodecName = state.userVideoDecoder.ifBlank { null },
        onCodecSelected = { decoder ->
            if (CodecUtils.isCodecProtocolMatch(state.userVideoEncoder, decoder, CodecUtils.CodecType.VIDEO)) {
                state.userVideoDecoder = decoder
            } else {
                showCodecProtocolMismatch(context)
            }
            state.showVideoDecoderSelector = false
        },
        onBack = {
            state.showVideoDecoderSelector = false
        },
    )
}

@Composable
private fun AudioEncoderSelectionOverlay(
    state: SessionDialogState,
    sessionId: String?,
    context: Context,
) {
    if (!state.showAudioEncoderDialog) {
        return
    }

    EncoderSelectionDialog(
        encoderType = EncoderType.AUDIO,
        sessionId = sessionId,
        host = state.connectionTargetHost(),
        port = state.port,
        currentEncoder = state.userAudioEncoder,
        cachedEncoders = state.remoteAudioEncoders,
        onDismiss = { state.showAudioEncoderDialog = false },
        onEncoderSelected = { encoder ->
            if (CodecUtils.isCodecProtocolMatch(encoder, state.userAudioDecoder, CodecUtils.CodecType.AUDIO)) {
                state.userAudioEncoder = encoder
            } else {
                showCodecProtocolMismatch(context)
            }
            state.showAudioEncoderDialog = false
        },
        onEncodersDetected = { encoders ->
            state.remoteAudioEncoders = encoders
        },
    )
}

@Composable
private fun AudioDecoderSelectionOverlay(
    state: SessionDialogState,
    context: Context,
) {
    if (!state.showAudioDecoderSelector) {
        return
    }

    AudioCodecSelectorScreen(
        currentCodecName = state.userAudioDecoder.ifBlank { null },
        onCodecSelected = { decoder ->
            if (CodecUtils.isCodecProtocolMatch(state.userAudioEncoder, decoder, CodecUtils.CodecType.AUDIO)) {
                state.userAudioDecoder = decoder
            } else {
                showCodecProtocolMismatch(context)
            }
            state.showAudioDecoderSelector = false
        },
        onBack = {
            state.showAudioDecoderSelector = false
        },
    )
}

@Composable
private fun UsbDeviceSelectionOverlay(state: SessionDialogState) {
    if (!state.showUsbDeviceDialog) {
        return
    }

    UsbDeviceSelectionDialog(
        currentSerialNumber = state.usbSerialNumber,
        onDeviceSelected = { serialNumber, deviceName ->
            state.usbSerialNumber = serialNumber
            state.isUsbMode = true
            state.host = ""
            state.showUsbDeviceDialog = false

            if (state.sessionName.isBlank()) {
                state.sessionName = deviceName
            }
        },
        onDismiss = {
            state.showUsbDeviceDialog = false
            if (state.usbSerialNumber.isBlank()) {
                state.host = ""
                state.isUsbMode = false
            }
        },
    )
}

@Composable
private fun GroupSelectionOverlay(
    state: SessionDialogState,
    availableGroups: List<DeviceGroup>,
) {
    if (!state.showGroupSelector) {
        return
    }

    GroupSelectorDialog(
        selectedGroupIds = state.selectedGroupIds,
        availableGroups = availableGroups,
        onGroupsSelected = { selectedIds ->
            state.selectedGroupIds = selectedIds
            state.showGroupSelector = false
        },
        onDismiss = {
            state.showGroupSelector = false
        },
    )
}

private fun formatGroupDisplay(
    selectedGroupIds: List<String>,
    availableGroups: List<DeviceGroup>,
): String {
    if (selectedGroupIds.isEmpty()) {
        return SessionTexts.GROUP_UNGROUPED.get()
    }

    val groupNames = availableGroups.filter { it.id in selectedGroupIds }.map { it.name }

    return if (groupNames.size <= 3) {
        groupNames.joinToString(", ")
    } else {
        val firstThree = groupNames.take(3).joinToString(", ")
        val remaining = groupNames.size - 3
        "$firstThree +$remaining"
    }
}

private fun SessionDialogState.connectionTargetHost(): String =
    if (isUsbMode) {
        usbSerialNumber
    } else {
        host
    }

private fun showCodecProtocolMismatch(context: Context) {
    Toast
        .makeText(
            context,
            CodecTexts.CODEC_PROTOCOL_MISMATCH.get(),
            Toast.LENGTH_SHORT,
        ).show()
}
