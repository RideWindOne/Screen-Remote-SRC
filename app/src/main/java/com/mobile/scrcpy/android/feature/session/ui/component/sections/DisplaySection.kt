/**
 * 显示配置区域组件
 * 
 * 从 SessionDialogSections.kt 提取的显示配置相关 Composable 函数
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
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.session.ui.component.CompactSwitchRow
import com.mobile.scrcpy.android.feature.session.ui.component.SessionDialogState

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
