/*
 * USB 设备选择对话框
 *
 * 功能：
 * - 扫描并显示可用的 USB 设备
 * - 请求 USB 权限
 * - 连接选中的 USB 设备
 */

package com.screen.remote.android.feature.device.ui.component

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.common.AppTextSizes
import com.screen.remote.android.core.designsystem.component.DialogContainer
import com.screen.remote.android.core.designsystem.component.DialogHeader
import com.screen.remote.android.core.designsystem.component.DialogHeaderSpacer
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.LogTexts
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.adb.usb.UsbDeviceInfo
import kotlinx.coroutines.launch

/**
 * USB 设备选择对话框
 *
 * @param onDismiss 关闭对话框回调
 * @param onScanDevices 扫描设备回调
 * @param onConnectDevice 连接设备回调
 * @param usbDevices USB 设备列表
 * @param isScanning 是否正在扫描
 */
@Composable
fun UsbDeviceDialog(
    onDismiss: () -> Unit,
    onScanDevices: suspend () -> Unit,
    onConnectDevice: suspend (UsbDevice) -> Result<String>,
    usbDevices: List<UsbDeviceInfo>,
    isScanning: Boolean,
) {
    val scope = rememberCoroutineScope()
    var isConnecting by remember { mutableStateOf(false) }
    var connectingDeviceId by remember { mutableStateOf<String?>(null) }

    // 自动扫描一次
    LaunchedEffect(Unit) {
        onScanDevices()
    }

    Dialog(onDismissRequest = onDismiss) {
        DialogContainer {
            DialogHeader(
                title = AdbTexts.USB_DEVICE_LIST_TITLE.get(),
                onDismiss = onDismiss,
                showBackButton = false,
                leftButtonText = CommonTexts.BUTTON_CLOSE.get(),
                trailingContent = {
                    IconButton(
                        onClick = { scope.launch { onScanDevices() } },
                        enabled = !isScanning && !isConnecting,
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
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )

            DialogHeaderSpacer()

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AppDimens.paddingStandard,
                            end = AppDimens.paddingStandard,
                            bottom = AppDimens.paddingStandard,
                        ),
            ) {
                // 设备列表
                if (usbDevices.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                text = AdbTexts.USB_NO_DEVICES_FOUND.get(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = AppTextSizes.body,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(usbDevices.size) { index ->
                            val deviceInfo = usbDevices[index]
                            UsbDeviceItem(
                                deviceInfo = deviceInfo,
                                isConnecting = isConnecting && connectingDeviceId == deviceInfo.deviceName,
                                showConnectButton = true,
                                showPermissionHint = true,
                                onClick = {
                                    scope.launch {
                                        isConnecting = true
                                        connectingDeviceId = deviceInfo.deviceName
                                        val result = onConnectDevice(deviceInfo.device)
                                        isConnecting = false
                                        connectingDeviceId = null

                                        if (result.isSuccess) {
                                            onDismiss()
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

@Composable
fun UsbDeviceSelectionDialog(
    currentSerialNumber: String,
    onDeviceSelected: (serialNumber: String, deviceName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val adbConnectionManager =
        remember(appContext) {
            AdbConnectionManager.getInstance(appContext)
        }

    val usbDevices by adbConnectionManager.getUsbDevices().collectAsState()
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            DialogHeader(
                title = AdbTexts.USB_SELECT_DEVICE.get(),
                onDismiss = onDismiss,
                showBackButton = false,
                leftButtonText = CommonTexts.BUTTON_CLOSE.get(),
                trailingContent = {
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
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )

            DialogHeaderSpacer()

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AppDimens.paddingStandard,
                            end = AppDimens.paddingStandard,
                            bottom = AppDimens.paddingStandard,
                        ),
            ) {
                if (usbDevices.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                text = AdbTexts.USB_NO_DEVICES_FOUND.get(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = AppTextSizes.body,
                            )
                        }
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
                                    if (!deviceInfo.hasPermission) {
                                        scope.launch {
                                            val permissionResult =
                                                adbConnectionManager.requestUsbPermission(
                                                    deviceInfo.device,
                                                )
                                            if (permissionResult.isSuccess) {
                                                adbConnectionManager.scanUsbDevices()
                                                if (deviceInfo.serialNumber.isNotBlank()) {
                                                    onDeviceSelected(
                                                        deviceInfo.serialNumber,
                                                        deviceInfo.getDisplayName(),
                                                    )
                                                }
                                            } else {
                                                android.widget.Toast
                                                    .makeText(
                                                        context,
                                                        AdbTexts.USB_PERMISSION_DENIED.get(),
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    ).show()
                                            }
                                        }
                                    } else {
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
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.5.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = deviceInfo.getDisplayName(),
                    fontSize = AppTextSizes.body,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(4.dp))

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
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = AdbTexts.USB_PERMISSION_GRANTED_STATUS.get(),
                                fontSize = AppTextSizes.caption,
                                color = MaterialTheme.colorScheme.secondary,
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
