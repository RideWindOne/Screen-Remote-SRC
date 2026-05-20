package com.screen.remote.android.feature.device.ui.component

import android.annotation.SuppressLint
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screen.remote.android.app.ScreenRemoteApp
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.common.util.FilePickerHelper
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.feature.device.ui.component.adbkey.GenerateKeyPairConfirmDialog
import com.screen.remote.android.feature.device.ui.component.adbkey.ImportKeysHintDialog
import com.screen.remote.android.feature.device.viewmodel.AdbKeysViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbKeyManagementDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val adbConnectionManager = remember { ScreenRemoteApp.instance.adbConnectionManager }
    val viewModel: AdbKeysViewModel =
        viewModel(
            factory = AdbKeysViewModel.provideFactory(context, adbConnectionManager),
        )
    val state = rememberAdbKeyManagementDialogState()
    val texts = rememberAdbKeyDialogTexts()
    val scope = rememberCoroutineScope()
    val privateKeyFocusRequester = remember { FocusRequester() }
    val publicKeyFocusRequester = remember { FocusRequester() }

    suspend fun refreshKeys() {
        val info = viewModel.getAdbKeysInfo().first()
        state.updateKeysInfo(
            info = info,
            loadedStatus = texts.keyPairLoaded,
            notFoundStatus = texts.keyNotFound,
        )
    }

    suspend fun handleResult(
        result: Result<Unit>,
        successMessage: String,
        failurePrefix: String,
        refreshOnSuccess: Boolean = false,
    ) {
        if (result.isSuccess) {
            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            if (refreshOnSuccess) {
                refreshKeys()
            }
        } else {
            val message = result.exceptionOrNull()?.message
            Toast.makeText(context, "$failurePrefix: $message", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveKeys() {
        scope.launch {
            handleResult(
                result = viewModel.saveAdbKeys(state.privateKeyEditable, state.publicKeyEditable),
                successMessage = texts.saveSuccess,
                failurePrefix = texts.saveFailed,
                refreshOnSuccess = true,
            )
        }
    }

    fun generateKeys() {
        scope.launch {
            handleResult(
                result = viewModel.generateAdbKeys(),
                successMessage = texts.generateSuccess,
                failurePrefix = texts.generateFailed,
                refreshOnSuccess = true,
            )
        }
    }

    fun importKeys(uris: List<Uri>) {
        if (uris.isEmpty()) {
            return
        }

        scope.launch {
            handleResult(
                result = viewModel.importAdbKeysFromUris(uris),
                successMessage = texts.importSuccess,
                failurePrefix = texts.importFailed,
                refreshOnSuccess = true,
            )
        }
    }

    fun exportKeys(
        privateKeyUri: Uri,
        publicKeyUri: Uri,
    ) {
        scope.launch {
            handleResult(
                result = viewModel.exportAdbKeysSeparately(privateKeyUri, publicKeyUri),
                successMessage = texts.exportSuccess,
                failurePrefix = texts.exportFailed,
            )
        }
    }

    val exportPublicKeyLauncher =
        FilePickerHelper.rememberExportFileLauncher(
            mimeType = "application/octet-stream",
        ) { uri ->
            val privateKeyUri = state.pendingPrivateKeyUri ?: return@rememberExportFileLauncher
            state.pendingPrivateKeyUri = null
            uri ?: return@rememberExportFileLauncher
            exportKeys(privateKeyUri, uri)
        }

    val exportPrivateKeyLauncher =
        FilePickerHelper.rememberExportFileLauncher(
            mimeType = "application/octet-stream",
        ) { uri ->
            uri ?: return@rememberExportFileLauncher
            state.pendingPrivateKeyUri = uri
            exportPublicKeyLauncher.launch("adbkey.pub")
        }

    val importKeysLauncher =
        FilePickerHelper.rememberImportMultipleFilesLauncher(::importKeys)

    LaunchedEffect(Unit) {
        refreshKeys()
    }

    DialogPage(
        title = texts.title,
        onDismiss = onDismiss,
        enableScroll = true,
        horizontalPadding = 10.dp,
        rightButtonText = texts.saveKeys,
        onRightButtonClick = ::saveKeys,
    ) {
        AdbKeyManagementDialogContent(
            state = state,
            texts = texts,
            privateKeyFocusRequester = privateKeyFocusRequester,
            publicKeyFocusRequester = publicKeyFocusRequester,
            onSaveKeys = ::saveKeys,
            onImportKeys = { state.showImportHintDialog = true },
            onExportKeys = { exportPrivateKeyLauncher.launch("adbkey") },
            onGenerateKeys = { state.showGenerateDialog = true },
        )
    }

    if (state.showGenerateDialog) {
        GenerateKeyPairConfirmDialog(
            onConfirm = {
                state.showGenerateDialog = false
                generateKeys()
            },
            onDismiss = { state.showGenerateDialog = false },
        )
    }

    if (state.showImportHintDialog) {
        ImportKeysHintDialog(
            onConfirm = {
                state.showImportHintDialog = false
                importKeysLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { state.showImportHintDialog = false },
        )
    }
}

internal data class AdbKeyDialogTexts(
    val title: String,
    val keyDir: String,
    val privateKey: String,
    val publicKey: String,
    val saveSuccess: String,
    val saveFailed: String,
    val importSuccess: String,
    val importFailed: String,
    val exportSuccess: String,
    val exportFailed: String,
    val generateSuccess: String,
    val generateFailed: String,
    val generateKeys: String,
    val importKeys: String,
    val exportKeys: String,
    val saveKeys: String,
    val keyNotFound: String,
    val keyPairLoaded: String,
    val keyInfo: String,
    val keyOperations: String,
    val status: String,
    val hide: String,
    val show: String,
)

@Composable
internal fun rememberAdbKeyDialogTexts(): AdbKeyDialogTexts =
    AdbKeyDialogTexts(
        title = rememberText(AdbTexts.ADB_KEY_MANAGEMENT_TITLE),
        keyDir = rememberText(AdbTexts.ADB_KEY_DIR_LABEL),
        privateKey = rememberText(AdbTexts.ADB_PRIVATE_KEY_LABEL),
        publicKey = rememberText(AdbTexts.ADB_PUBLIC_KEY_LABEL),
        saveSuccess = rememberText(AdbTexts.ADB_KEY_SAVE_SUCCESS),
        saveFailed = rememberText(AdbTexts.ADB_KEY_SAVE_FAILED),
        importSuccess = rememberText(AdbTexts.ADB_KEY_IMPORT_SUCCESS),
        importFailed = rememberText(AdbTexts.ADB_KEY_IMPORT_FAILED),
        exportSuccess = rememberText(AdbTexts.ADB_KEY_EXPORT_SUCCESS),
        exportFailed = rememberText(AdbTexts.ADB_KEY_EXPORT_FAILED),
        generateSuccess = rememberText(AdbTexts.ADB_KEY_GENERATE_SUCCESS),
        generateFailed = rememberText(AdbTexts.ADB_KEY_GENERATE_FAILED),
        generateKeys = rememberText(AdbTexts.BUTTON_GENERATE_KEYS),
        importKeys = rememberText(AdbTexts.BUTTON_IMPORT_KEYS),
        exportKeys = rememberText(AdbTexts.BUTTON_EXPORT_KEYS),
        saveKeys = rememberText(AdbTexts.BUTTON_SAVE_KEYS),
        keyNotFound = rememberText(AdbTexts.ADB_KEY_NOT_FOUND),
        keyPairLoaded = rememberText(AdbTexts.ADB_KEYPAIR_LOADED),
        keyInfo = rememberText(AdbTexts.LABEL_KEY_INFO),
        keyOperations = rememberText(AdbTexts.LABEL_KEY_OPERATIONS),
        status = rememberText(CommonTexts.LABEL_STATUS),
        hide = rememberText(CommonTexts.BUTTON_HIDE),
        show = rememberText(CommonTexts.BUTTON_SHOW),
    )
