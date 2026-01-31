/**
 * 控制配置区域组件
 * 
 * 从 SessionDialogSections.kt 提取的远程设备和连接选项相关 Composable 函数
 */
package com.mobile.scrcpy.android.feature.session.ui.component.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.PlaceholderTexts
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.session.ui.component.CompactClickableRow
import com.mobile.scrcpy.android.feature.session.ui.component.CompactSwitchRow
import com.mobile.scrcpy.android.feature.session.ui.component.LabeledTextField
import com.mobile.scrcpy.android.feature.session.ui.component.SessionDialogState

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
                            state.usbSerialNumber.ifBlank {
                                AdbTexts.USB_NO_DEVICE_SELECTED.get()
                            },
                        onClick = onUsbDeviceClick,
                        showArrow = true,
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
                    showArrow = true,
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
