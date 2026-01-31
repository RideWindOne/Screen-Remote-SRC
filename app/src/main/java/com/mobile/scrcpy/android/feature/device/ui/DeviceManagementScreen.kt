package com.mobile.scrcpy.android.feature.device.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobile.scrcpy.android.core.designsystem.component.DialogContainer
import com.mobile.scrcpy.android.core.designsystem.component.DialogHeader
import com.mobile.scrcpy.android.feature.device.ui.component.UsbDeviceDialog
import com.mobile.scrcpy.android.feature.device.viewmodel.feature.device.DeviceViewModel
import com.mobile.scrcpy.android.infrastructure.adb.connection.DeviceInfo

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun DeviceManagementScreen(
    viewModel: DeviceViewModel = viewModel(),
    onDeviceSelected: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
    ) {
        DialogContainer {
            DialogHeader(
                title = "设备管理",
                onDismiss = onDismiss,
                showBackButton = false,
                trailingContent = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加设备",
                            tint = Color(0xFF007AFF),
                        )
                    }
                },
            )

            // 设备列表
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (connectedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "暂无连接设备",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "点击右上角 + 添加设备",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(connectedDevices) { device ->
                            DeviceCard(
                                device = device,
                                onConnect = { onDeviceSelected(device.deviceId) },
                                onDisconnect = { viewModel.disconnectDevice(device.deviceId) },
                            )
                        }
                    }
                }
            }
        }
    }

    // 添加设备对话框
    if (showAddDialog) {
        AddDeviceDialog(
            viewModel = viewModel,
            connectionState = connectionState,
            onDismiss = {
                showAddDialog = false
                viewModel.resetConnectionState()
            },
            onConnect = { host, port, name ->
                viewModel.connectDevice(host, port, name)
            },
        )
    }

    // 连接成功后关闭对话框
    LaunchedEffect(connectionState) {
        if (connectionState is DeviceViewModel.ConnectionState.Success) {
            showAddDialog = false
            viewModel.resetConnectionState()
        }
    }
}

@Composable
fun DeviceCard(
    device: DeviceInfo,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${device.manufacturer} ${device.model}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = device.deviceId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onConnect) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "连接",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(onClick = onDisconnect) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "断开",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
fun AddDeviceDialog(
    viewModel: DeviceViewModel,
    connectionState: DeviceViewModel.ConnectionState,
    onDismiss: () -> Unit,
    onConnect: (String, Int, String?) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }
    var showUsbDialog by remember { mutableStateOf(false) }

    val usbDevices by viewModel.usbDevices.collectAsState()
    val isUsbScanning by viewModel.usbScanningState.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("添加设备") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // TCP/IP 连接
                Text(
                    text = "TCP/IP 连接",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP 地址") },
                    placeholder = { Text("192.168.1.100") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("端口") },
                    placeholder = { Text("5555") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // USB 连接按钮
                Button(
                    onClick = { showUsbDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("USB 有线连接")
                }

                if (connectionState is DeviceViewModel.ConnectionState.Connecting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting...")
                    }
                }

                if (connectionState is DeviceViewModel.ConnectionState.Error) {
                    Text(
                        text = connectionState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val portInt = port.toIntOrNull() ?: 5555
                    onConnect(host, portInt, null)
                },
                enabled = host.isNotBlank() && connectionState !is DeviceViewModel.ConnectionState.Connecting,
            ) {
                Text("连接")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )

    // USB 设备选择对话框
    if (showUsbDialog) {
        UsbDeviceDialog(
            onDismiss = { showUsbDialog = false },
            onScanDevices = { viewModel.scanUsbDevices() },
            onConnectDevice = { usbDevice ->
                viewModel.connectUsbDevice(usbDevice)
            },
            usbDevices = usbDevices,
            isScanning = isUsbScanning,
        )
    }
}
