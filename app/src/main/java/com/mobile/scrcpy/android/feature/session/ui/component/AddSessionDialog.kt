package com.mobile.scrcpy.android.feature.session.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.feature.device.ui.component.UsbDeviceSelectionDialog
import com.mobile.scrcpy.android.feature.session.data.repository.SessionData

import com.mobile.scrcpy.android.core.i18n.SessionTexts
/**
 * 添加/编辑会话对话框
 */
@Composable
fun AddSessionDialog(
    sessionData: SessionData? = null,
    availableGroups: List<DeviceGroup>,
    onDismiss: () -> Unit,
    onConfirm: (SessionData) -> Unit
) {
    val state = remember(sessionData) { SessionDialogState(sessionData) }
    val isEditMode = sessionData != null
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            SessionTexts.SESSION_CANCEL.get(),
                            color = Color(0xFF007AFF)
                        )
                    }
                    
                    Text(
                        text = if (isEditMode) 
                            SessionTexts.SESSION_EDIT.get()
                        else 
                            SessionTexts.SESSION_ADD.get(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    TextButton(
                        onClick = {
                            if (state.validate()) {
                                onConfirm(state.toSessionData(sessionData?.id))
                                onDismiss()
                            }
                        }
                    ) {
                        Text(
                            SessionTexts.SESSION_SAVE.get(),
                            color = Color(0xFF007AFF)
                        )
                    }
                }
                
                // 滚动内容区域
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 远程设备配置
                    RemoteDeviceSection(
                        state = state,
                        availableGroups = availableGroups,
                        onUsbDeviceClick = {
                            state.showUsbDeviceDialog = true
                        },
                        onGroupSelectorClick = {
                            // TODO: 显示分组选择对话框
                        }
                    )
                    
                    // 连接选项
                    ConnectionOptionsSection(state = state)
                    
                    // ADB 会话选项
                    AdbSessionOptionsSection(state = state)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
    
    // 编码器选择对话框
    if (state.showEncoderOptionsDialog) {
        EncoderSelectionDialog(
            host = state.host,
            codec = state.videoCodec,
            currentEncoder = state.videoEncoder,
            onDismiss = { state.showEncoderOptionsDialog = false },
            onSelect = { encoder ->
                state.videoEncoder = encoder
                state.showEncoderOptionsDialog = false
            }
        )
    }
    
    if (state.showAudioEncoderDialog) {
        EncoderSelectionDialog(
            host = state.host,
            codec = state.audioCodec,
            currentEncoder = state.audioEncoder,
            onDismiss = { state.showAudioEncoderDialog = false },
            onSelect = { encoder ->
                state.audioEncoder = encoder
                state.showAudioEncoderDialog = false
            }
        )
    }
    
    // USB 设备选择对话框
    if (state.showUsbDeviceDialog) {
        UsbDeviceSelectionDialog(
            currentSerialNumber = state.usbSerialNumber,
            onDeviceSelected = { serialNumber ->
                state.usbSerialNumber = serialNumber
                state.isUsbMode = true
                state.host = ""  // 清空 host 输入框
                state.showUsbDeviceDialog = false
                
                // 如果会话名为空，使用序列号作为名称
                if (state.sessionName.isBlank()) {
                    state.sessionName = serialNumber
                }
            },
            onDismiss = { 
                state.showUsbDeviceDialog = false
                // 如果取消选择且没有序列号，清空 host 并退出 USB 模式
                if (state.usbSerialNumber.isBlank()) {
                    state.host = ""
                    state.isUsbMode = false
                }
            }
        )
    }
}

/**
 * 编码器选择对话框（占位实现）
 */
@Composable
private fun EncoderSelectionDialog(
    host: String,
    codec: String,
    currentEncoder: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = SessionTexts.LABEL_VIDEO_ENCODER.get(),
                    style = MaterialTheme.typography.titleMedium
                )
                
                Text(
                    text = SessionTexts.ENCODER_ERROR_INPUT_HOST.get(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(SessionTexts.SESSION_CANCEL.get())
                    }
                }
            }
        }
    }
}
