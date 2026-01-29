package com.mobile.scrcpy.android.feature.device.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.AppTextSizes
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.infrastructure.adb.usb.UsbDeviceInfo

/**
 * USB 设备列表项 - 通用组件
 *
 * @param deviceInfo USB 设备信息
 * @param isSelected 是否选中（用于选择模式）
 * @param isConnecting 是否正在连接（用于连接模式）
 * @param showConnectButton 是否显示连接按钮
 * @param showPermissionHint 是否显示权限提示
 * @param onClick 点击回调
 */
@Composable
fun UsbDeviceItem(
    deviceInfo: UsbDeviceInfo,
    isSelected: Boolean = false,
    isConnecting: Boolean = false,
    showConnectButton: Boolean = false,
    showPermissionHint: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppDimens.cardCornerRadius))
                .clickable(enabled = !isConnecting) { onClick() },
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // USB 图标
            Icon(
                imageVector = Icons.Default.Usb,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint =
                    if (deviceInfo.hasPermission) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 设备信息
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = deviceInfo.getDisplayName(),
                    fontSize = AppTextSizes.body,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 序列号
                if (deviceInfo.serialNumber.isNotBlank()) {
                    Text(
                        text = "${AdbTexts.USB_SERIAL_NUMBER.get()}: ${deviceInfo.serialNumber}",
                        fontSize = AppTextSizes.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "${AdbTexts.USB_SERIAL_NUMBER.get()}: ",
                        fontSize = AppTextSizes.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 权限状态
                if (showPermissionHint) {
                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${AdbTexts.USB_PERMISSION.get()}: ",
                            fontSize = AppTextSizes.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (deviceInfo.hasPermission) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF4CAF50),
                            )
                            Text(
                                text = AdbTexts.USB_PERMISSION_GRANTED_STATUS.get(),
                                fontSize = AppTextSizes.caption,
                                color = Color(0xFF4CAF50),
                            )
                        } else {
                            Text(
                                text = "${AdbTexts.USB_PERMISSION_NOT_GRANTED_STATUS.get()} (${AdbTexts.USB_CLICK_TO_REQUEST_PERMISSION.get()})",
                                fontSize = AppTextSizes.caption,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // 右侧操作区域
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else if (showConnectButton && deviceInfo.hasPermission) {
                TextButton(onClick = onClick) {
                    Text(AdbTexts.USB_CONNECT_BUTTON.get())
                }
            } else if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
