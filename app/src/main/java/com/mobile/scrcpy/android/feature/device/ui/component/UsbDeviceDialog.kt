/*
 * USB 设备选择对话框
 * 
 * 功能：
 * - 扫描并显示可用的 USB 设备
 * - 请求 USB 权限
 * - 连接选中的 USB 设备
 */

package com.mobile.scrcpy.android.feature.device.ui.component

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.AppTextSizes
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.infrastructure.adb.usb.UsbDeviceInfo
import kotlinx.coroutines.launch

import com.mobile.scrcpy.android.core.i18n.CommonTexts
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
    isScanning: Boolean
) {
    val scope = rememberCoroutineScope()
    var isConnecting by remember { mutableStateOf(false) }
    var connectingDeviceId by remember { mutableStateOf<String?>(null) }
    
    // 自动扫描一次
    LaunchedEffect(Unit) {
        onScanDevices()
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(AppDimens.WINDOW_WIDTH_RATIO)
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(AppDimens.windowCornerRadius),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.paddingStandard)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppDimens.paddingStandard),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AdbTexts.USB_DEVICE_LIST_TITLE.get(),
                        fontSize = AppTextSizes.title,
                        fontWeight = FontWeight.Bold
                    )
                    
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isConnecting
                    ) {
                        Text(CommonTexts.BUTTON_CLOSE.get())
                    }
                }
                
                HorizontalDivider()

                Spacer(modifier = Modifier.height(AppDimens.spacingStandard))
                
                // 扫描按钮
                Button(
                    onClick = {
                        scope.launch {
                            onScanDevices()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isScanning && !isConnecting
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (isScanning) AdbTexts.USB_SCANNING_DEVICES.get()
                        else AdbTexts.USB_SCAN_BUTTON.get()
                    )
                }
                
                Spacer(modifier = Modifier.height(AppDimens.spacingStandard))
                
                // 设备列表
                if (usbDevices.isEmpty() && !isScanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = AdbTexts.USB_NO_DEVICES_FOUND.get(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = AppTextSizes.body
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
