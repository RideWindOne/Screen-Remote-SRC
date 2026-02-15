package com.mobile.scrcpy.android.feature.device.ui.component

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mobile.scrcpy.android.core.domain.model.AdbKeysInfo

@Composable
internal fun rememberAdbKeyManagementDialogState(): AdbKeyManagementDialogState =
    remember { AdbKeyManagementDialogState() }

@Stable
internal class AdbKeyManagementDialogState {
    var privateKeyVisible by mutableStateOf(false)
    var publicKeyVisible by mutableStateOf(false)
    var adbKeysDir by mutableStateOf("")
    var privateKeyEditable by mutableStateOf("")
    var publicKeyEditable by mutableStateOf("")
    var showGenerateDialog by mutableStateOf(false)
    var showImportHintDialog by mutableStateOf(false)
    var keysLoadStatus by mutableStateOf("")
    var isKeysLoadSuccessful by mutableStateOf(false)
    var pendingPrivateKeyUri by mutableStateOf<Uri?>(null)

    fun updateKeysInfo(
        info: AdbKeysInfo,
        loadedStatus: String,
        notFoundStatus: String,
    ) {
        adbKeysDir = info.keysDir
        privateKeyEditable = info.privateKey
        publicKeyEditable = info.publicKey
        isKeysLoadSuccessful = info.privateKey.isNotEmpty() && info.publicKey.isNotEmpty()
        keysLoadStatus =
            if (isKeysLoadSuccessful) {
                loadedStatus
            } else {
                notFoundStatus
            }
    }
}
