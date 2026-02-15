package com.mobile.scrcpy.android.feature.device.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobile.scrcpy.android.core.designsystem.component.DialogContainer
import com.mobile.scrcpy.android.core.designsystem.component.DialogHeader
import com.mobile.scrcpy.android.feature.device.ui.component.AddDeviceDialog
import com.mobile.scrcpy.android.feature.device.viewmodel.DeviceViewModel

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun DeviceManagementScreen(
    viewModel: DeviceViewModel = viewModel(),
    onDeviceSelected: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val screenState = rememberDeviceManagementScreenState(viewModel)

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
                    DeviceManagementAddAction(
                        onClick = { showAddDialog = true },
                    )
                },
            )

            DeviceManagementContent(
                connectedDevices = screenState.connectedDevices,
                onDeviceSelected = onDeviceSelected,
                onDisconnectDevice = viewModel::disconnectDevice,
            )
        }
    }

    if (showAddDialog) {
        AddDeviceDialog(
            viewModel = viewModel,
            connectionState = screenState.connectionState,
            onDismiss = {
                showAddDialog = false
                viewModel.resetConnectionState()
            },
            onConnect = { host, port, name ->
                viewModel.connectDevice(host, port, name)
            },
        )
    }

    LaunchedEffect(screenState.connectionState) {
        if (screenState.connectionState is DeviceViewModel.ConnectionState.Success) {
            showAddDialog = false
            viewModel.resetConnectionState()
        }
    }
}
