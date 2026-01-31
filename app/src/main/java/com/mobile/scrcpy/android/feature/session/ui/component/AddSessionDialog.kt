package com.mobile.scrcpy.android.feature.session.ui.component

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.data.repository.SessionData
import com.mobile.scrcpy.android.core.designsystem.component.DialogBottomSpacer
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.GroupSelectorDialog
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.i18n.CodecTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.codec.component.EncoderSelectionDialog
import com.mobile.scrcpy.android.feature.codec.component.EncoderType
import com.mobile.scrcpy.android.feature.codec.ui.AudioCodecSelectorScreen
import com.mobile.scrcpy.android.feature.codec.ui.VideoCodecSelectorScreen
import com.mobile.scrcpy.android.feature.codec.util.CodecUtils
import com.mobile.scrcpy.android.feature.device.ui.component.UsbDeviceSelectionDialog
import com.mobile.scrcpy.android.feature.session.ui.component.sections.AudioConfigSection
import com.mobile.scrcpy.android.feature.session.ui.component.sections.ConnectionOptionsSection
import com.mobile.scrcpy.android.feature.session.ui.component.sections.OtherOptionsSection
import com.mobile.scrcpy.android.feature.session.ui.component.sections.RemoteDeviceSection
import com.mobile.scrcpy.android.feature.session.ui.component.sections.VideoConfigSection

/**
 * 添加/编辑会话对话框
 */
@Composable
fun AddSessionDialog(
    sessionData: SessionData? = null,
    availableGroups: List<DeviceGroup>,
    onDismiss: () -> Unit,
    onConfirm: (SessionData) -> Unit,
) {
    val state = remember(sessionData) { SessionDialogState(sessionData) }
    val isEditMode = sessionData != null
    val context = LocalContext.current

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
                onConfirm(state.toSessionData(sessionData?.id))
                onDismiss()
            }
        },
        enableScroll = true,
        verticalSpacing = 8.dp,
    ) {
        // 远程设备配置
        RemoteDeviceSection(
            state = state,
            availableGroups = availableGroups,
            onUsbDeviceClick = {
                state.showUsbDeviceDialog = true
            },
            onGroupSelectorClick = {
                state.showGroupSelector = true
            },
        )

        // 连接选项
        ConnectionOptionsSection(state = state)

        // 视频配置
        VideoConfigSection(state = state)

        // 音频配置
        AudioConfigSection(state = state)

        // 其他选项
        OtherOptionsSection(state = state)

        DialogBottomSpacer()
    }

    // 视频编码器选择
    if (state.showEncoderOptionsDialog) {
        EncoderSelectionDialog(
            encoderType = EncoderType.VIDEO,
            sessionId = sessionData?.id, // 传入 sessionId，新建时为 null
            host = if (state.isUsbMode) state.usbSerialNumber else state.host,
            port = state.port,
            currentEncoder = state.userVideoEncoder,
            cachedEncoders = state.remoteVideoEncoders,
            onDismiss = { state.showEncoderOptionsDialog = false },
            onEncoderSelected = { encoder ->
                if (CodecUtils.isCodecProtocolMatch(encoder, state.userVideoDecoder, CodecUtils.CodecType.VIDEO)) {
                    state.userVideoEncoder = encoder
                } else {
                    Toast
                        .makeText(
                            context,
                            CodecTexts.CODEC_PROTOCOL_MISMATCH.get(),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                state.showEncoderOptionsDialog = false
            },
            onEncodersDetected = { encoders ->
                state.remoteVideoEncoders = encoders
            },
        )
    }

    // 视频解码器选择
    if (state.showVideoDecoderSelector) {
        VideoCodecSelectorScreen(
            currentCodecName = state.userVideoDecoder.ifBlank { null },
            onCodecSelected = { decoder ->
                if (CodecUtils.isCodecProtocolMatch(state.userVideoEncoder, decoder, CodecUtils.CodecType.VIDEO)) {
                    state.userVideoDecoder = decoder
                } else {
                    Toast
                        .makeText(
                            context,
                            CodecTexts.CODEC_PROTOCOL_MISMATCH.get(),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                state.showVideoDecoderSelector = false
            },
            onBack = {
                state.showVideoDecoderSelector = false
            },
        )
    }

    // 音频编码器选择
    if (state.showAudioEncoderDialog) {
        EncoderSelectionDialog(
            encoderType = EncoderType.AUDIO,
            sessionId = sessionData?.id, // 传入 sessionId，新建时为 null
            host = if (state.isUsbMode) state.usbSerialNumber else state.host,
            port = state.port,
            currentEncoder = state.userAudioEncoder,
            cachedEncoders = state.remoteAudioEncoders,
            onDismiss = { state.showAudioEncoderDialog = false },
            onEncoderSelected = { encoder ->
                if (CodecUtils.isCodecProtocolMatch(encoder, state.userAudioDecoder, CodecUtils.CodecType.AUDIO)) {
                    state.userAudioEncoder = encoder
                } else {
                    Toast
                        .makeText(
                            context,
                            CodecTexts.CODEC_PROTOCOL_MISMATCH.get(),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                state.showAudioEncoderDialog = false
            },
            onEncodersDetected = { encoders ->
                state.remoteAudioEncoders = encoders
            },
        )
    }

    // 音频解码器选择
    if (state.showAudioDecoderSelector) {
        AudioCodecSelectorScreen(
            currentCodecName = state.userAudioDecoder.ifBlank { null },
            onCodecSelected = { decoder ->
                if (CodecUtils.isCodecProtocolMatch(state.userAudioEncoder, decoder, CodecUtils.CodecType.AUDIO)) {
                    state.userAudioDecoder = decoder
                } else {
                    Toast
                        .makeText(
                            context,
                            CodecTexts.CODEC_PROTOCOL_MISMATCH.get(),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                state.showAudioDecoderSelector = false
            },
            onBack = {
                state.showAudioDecoderSelector = false
            },
        )
    }

    // USB 设备选择对话框
    if (state.showUsbDeviceDialog) {
        UsbDeviceSelectionDialog(
            currentSerialNumber = state.usbSerialNumber,
            onDeviceSelected = { serialNumber, deviceName ->
                state.usbSerialNumber = serialNumber
                state.isUsbMode = true
                state.host = "" // 清空 host 输入框
                state.showUsbDeviceDialog = false

                // 如果会话名为空，使用设备名称作为名称
                if (state.sessionName.isBlank()) {
                    state.sessionName = deviceName
                }
            },
            onDismiss = {
                state.showUsbDeviceDialog = false
                // 如果取消选择且没有序列号，清空 host 并退出 USB 模式
                if (state.usbSerialNumber.isBlank()) {
                    state.host = ""
                    state.isUsbMode = false
                }
            },
        )
    }

    // 分组选择对话框
    if (state.showGroupSelector) {
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
}
