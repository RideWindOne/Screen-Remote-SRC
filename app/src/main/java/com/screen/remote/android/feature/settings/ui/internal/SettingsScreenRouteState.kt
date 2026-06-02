package com.screen.remote.android.feature.settings.ui.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal class SettingsScreenRouteState {
    var showDevicePairingDialog by mutableStateOf(false)
        private set

    var showClearLogsDialog by mutableStateOf(false)
        private set

    fun openDevicePairingDialog() {
        showDevicePairingDialog = true
    }

    fun closeDevicePairingDialog() {
        showDevicePairingDialog = false
    }

    fun openClearLogsDialog() {
        showClearLogsDialog = true
    }

    fun closeClearLogsDialog() {
        showClearLogsDialog = false
    }

}

@Composable
internal fun rememberSettingsScreenRouteState(): SettingsScreenRouteState =
    remember { SettingsScreenRouteState() }
