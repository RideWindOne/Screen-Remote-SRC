package com.mobile.scrcpy.android.feature.device.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.scrcpy.android.app.ScreenRemoteApp
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.AppTextSizes
import com.mobile.scrcpy.android.core.designsystem.component.DialogContainer
import com.mobile.scrcpy.android.core.designsystem.component.DialogHeader
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.LogTexts
import kotlinx.coroutines.launch

/**
 * USB 设备选择对话框（用于会话编辑）
 */
@Composable
fun UsbDeviceSelectionDialog(
    currentSerialNumber: String,
    onDeviceSelected: (serialNumber: String, deviceName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val adbConnectionManager =
        remember {
            ScreenRemoteApp.instance.adbConnectionManager
        }

    val usbDevices by adbConnectionManager.getUsbDevices().collectAsState()
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 自动扫描一次
    LaunchedEffect(Unit) {
        isScanning = true
        adbConnectionManager.scanUsbDevices()
        isScanning = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
    ) {
        DialogContainer {
            // 使用通用 DialogHeader
            DialogHeader(
                title = AdbTexts.USB_SELECT_DEVICE.get(),
                onDismiss = onDismiss,
                showBackButton = false,
                leftButtonText = CommonTexts.BUTTON_CLOSE.get(),
                trailingContent = {
                    // 右上角刷新按钮
                    IconButton(
                        onClick = {
                            scope.launch {
                                isScanning = true
                                adbConnectionManager.scanUsbDevices()
                                isScanning = false
                            }
                        },
                        enabled = !isScanning,
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = LogTexts.LOG_REFRESH_BUTTON.get(),
                                tint = AppColors.iOSBlue,
                            )
                        }
                    }
                },
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = AppDimens.paddingStandard, end = AppDimens.paddingStandard),
            ) {
                // 设备列表
                if (usbDevices.isEmpty() && !isScanning) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = AdbTexts.USB_NO_DEVICES_FOUND.get(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = AppTextSizes.body,
                        )
                    }
                } else {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        usbDevices.forEach { deviceInfo ->
                            UsbDeviceItem(
                                deviceInfo = deviceInfo,
                                isSelected = deviceInfo.serialNumber == currentSerialNumber,
                                showPermissionHint = true,
                                onClick = {
                                    // 如果没有权限，先请求权限
                                    if (!deviceInfo.hasPermission) {
                                        scope.launch {
                                            val permissionResult =
                                                adbConnectionManager.requestUsbPermission(
                                                    deviceInfo.device,
                                                )
                                            if (permissionResult.isSuccess) {
                                                // 权限授予后，重新扫描设备列表
                                                adbConnectionManager.scanUsbDevices()
                                                // 只有序列号不为空才返回上一级
                                                if (deviceInfo.serialNumber.isNotBlank()) {
                                                    onDeviceSelected(
                                                        deviceInfo.serialNumber,
                                                        deviceInfo.getDisplayName(),
                                                    )
                                                }
                                            } else {
                                                // 权限被拒绝，显示提示
                                                android.widget.Toast
                                                    .makeText(
                                                        context,
                                                        AdbTexts.USB_PERMISSION_DENIED.get(),
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    ).show()
                                            }
                                        }
                                    } else {
                                        // 已有权限，只有序列号不为空才返回上一级
                                        if (deviceInfo.serialNumber.isNotBlank()) {
                                            onDeviceSelected(deviceInfo.serialNumber, deviceInfo.getDisplayName())
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
