package com.mobile.scrcpy.android.feature.session.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.designsystem.component.DialogBottomSpacer
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.GroupSelectorDialog
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.codec.component.EncoderSelectionDialog
import com.mobile.scrcpy.android.feature.codec.component.EncoderType
import com.mobile.scrcpy.android.feature.device.ui.component.UsbDeviceSelectionDialog
import com.mobile.scrcpy.android.feature.session.data.repository.SessionData

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

    // 编码器选择对话框
    if (state.showEncoderOptionsDialog) {
        EncoderSelectionDialog(
            encoderType = EncoderType.VIDEO,
            host = if (state.isUsbMode) state.usbSerialNumber else state.host,
            port = state.port,
            currentEncoder = state.videoEncoder,
            onDismiss = { state.showEncoderOptionsDialog = false },
            onEncoderSelected = { encoder ->
                state.videoEncoder = encoder
                state.showEncoderOptionsDialog = false
            },
        )
    }

    if (state.showAudioEncoderDialog) {
        EncoderSelectionDialog(
            encoderType = EncoderType.AUDIO,
            host = if (state.isUsbMode) state.usbSerialNumber else state.host,
            port = state.port,
            currentEncoder = state.audioEncoder,
            onDismiss = { state.showAudioEncoderDialog = false },
            onEncoderSelected = { encoder ->
                state.audioEncoder = encoder
                state.showAudioEncoderDialog = false
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
