package com.screen.remote.android.feature.device.ui.component

import android.annotation.SuppressLint
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.common.util.FilePickerHelper
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.core.designsystem.component.SectionCard
import com.screen.remote.android.core.designsystem.component.IOSAlertDialog as AlertDialog
import com.screen.remote.android.core.domain.model.AdbKeysInfo
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.feature.device.viewmodel.AdbKeysViewModel
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbKeyManagementDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val adbConnectionManager = remember(appContext) { AdbConnectionManager.getInstance(appContext) }
    val viewModel: AdbKeysViewModel =
        viewModel(
            factory = AdbKeysViewModel.provideFactory(appContext, adbConnectionManager),
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

@Composable
internal fun AdbKeyManagementDialogContent(
    state: AdbKeyManagementDialogState,
    texts: AdbKeyDialogTexts,
    privateKeyFocusRequester: FocusRequester,
    publicKeyFocusRequester: FocusRequester,
    onSaveKeys: () -> Unit,
    onImportKeys: () -> Unit,
    onExportKeys: () -> Unit,
    onGenerateKeys: () -> Unit,
) {
    AdbKeyInfoSection(
        state = state,
        texts = texts,
        privateKeyFocusRequester = privateKeyFocusRequester,
        publicKeyFocusRequester = publicKeyFocusRequester,
    )

    Spacer(modifier = Modifier.height(10.dp))

    AdbKeyActionsSection(
        texts = texts,
        onSaveKeys = onSaveKeys,
        onImportKeys = onImportKeys,
        onExportKeys = onExportKeys,
        onGenerateKeys = onGenerateKeys,
    )

    Spacer(modifier = Modifier.height(10.dp))

    AdbKeyStatusSection(
        status = state.keysLoadStatus,
        isSuccess = state.isKeysLoadSuccessful,
        title = texts.status,
    )
}

@Composable
private fun AdbKeyInfoSection(
    state: AdbKeyManagementDialogState,
    texts: AdbKeyDialogTexts,
    privateKeyFocusRequester: FocusRequester,
    publicKeyFocusRequester: FocusRequester,
) {
    AdbKeySectionCard(title = texts.keyInfo) {
        KeyInfoItem(
            label = texts.keyDir,
            value = state.adbKeysDir,
        )
        AppDivider()
        KeyEditItem(
            label = texts.privateKey,
            value = state.privateKeyEditable,
            onValueChange = { state.privateKeyEditable = it },
            isVisible = state.privateKeyVisible,
            onVisibilityToggle = { state.privateKeyVisible = !state.privateKeyVisible },
            focusRequester = privateKeyFocusRequester,
            txtHide = texts.hide,
            txtShow = texts.show,
        )
        AppDivider()
        KeyEditItem(
            label = texts.publicKey,
            value = state.publicKeyEditable,
            onValueChange = { state.publicKeyEditable = it },
            isVisible = state.publicKeyVisible,
            onVisibilityToggle = { state.publicKeyVisible = !state.publicKeyVisible },
            focusRequester = publicKeyFocusRequester,
            txtHide = texts.hide,
            txtShow = texts.show,
        )
    }
}

@Composable
private fun AdbKeyActionsSection(
    texts: AdbKeyDialogTexts,
    onSaveKeys: () -> Unit,
    onImportKeys: () -> Unit,
    onExportKeys: () -> Unit,
    onGenerateKeys: () -> Unit,
) {
    AdbKeySectionCard(title = texts.keyOperations) {
        KeyActionItem(
            icon = Icons.Default.Save,
            title = texts.saveKeys,
            onClick = onSaveKeys,
        )
        AppDivider()
        KeyActionItem(
            icon = Icons.Default.Download,
            title = texts.importKeys,
            onClick = onImportKeys,
        )
        AppDivider()
        KeyActionItem(
            icon = Icons.Default.Upload,
            title = texts.exportKeys,
            onClick = onExportKeys,
        )
        AppDivider()
        KeyActionItem(
            icon = Icons.Default.Key,
            title = texts.generateKeys,
            onClick = onGenerateKeys,
        )
    }
}

@Composable
private fun AdbKeyStatusSection(
    status: String,
    isSuccess: Boolean,
    title: String,
) {
    AdbKeySectionCard(title = title) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppDimens.listItemHeight)
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (isSuccess) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
        }
    }
}

@Composable
private fun AdbKeySectionCard(
    title: String,
    content: @Composable () -> Unit,
) = SectionCard(title = title) {
    content()
}

@Composable
private fun KeyActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = AppDimens.listItemHeight)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun KeyInfoItem(
    label: String,
    value: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun KeyEditItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isVisible: Boolean,
    onVisibilityToggle: (() -> Unit)?,
    focusRequester: FocusRequester?,
    txtHide: String = CommonTexts.BUTTON_HIDE.get(),
    txtShow: String = CommonTexts.BUTTON_SHOW.get(),
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppDimens.listItemHeight)
                    .clickable { onVisibilityToggle?.invoke() }
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (isVisible) txtHide else txtShow,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isVisible) txtHide else txtShow,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (!isVisible) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .clickable { onVisibilityToggle?.invoke() }
                            .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "••••••••••••••••••••••••••••••••••••••••",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        ),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 3,
                maxLines = 8,
            )
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible && focusRequester != null) {
            kotlinx.coroutines.delay(50)
            focusRequester.requestFocus()
        }
    }
}

@Composable
private fun GenerateKeyPairConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val txtTitle = AdbTexts.ADB_KEY_GENERATE_CONFIRM_TITLE.get()
    val txtDestructiveOp = AdbTexts.ADB_KEY_DESTRUCTIVE_OP.get()
    val txtCurrentKeysDeleted = AdbTexts.ADB_KEY_CURRENT_KEYS_DELETED.get()
    val txtDevicesLoseAuth = AdbTexts.ADB_KEY_DEVICES_LOSE_AUTH.get()
    val txtNeedReauth = AdbTexts.ADB_KEY_NEED_REAUTH.get()
    val txtCannotUndo = AdbTexts.ADB_KEY_CANNOT_UNDO.get()
    val txtConfirmGenerate = AdbTexts.ADB_KEY_CONFIRM_GENERATE.get()
    val txtConfirm = CommonTexts.BUTTON_CONFIRM.get()
    val txtCancel = CommonTexts.BUTTON_CANCEL.get()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
        },
        title = {
            Text(txtTitle)
        },
        text = {
            Column {
                Text(
                    txtDestructiveOp,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                Text("• $txtCurrentKeysDeleted")
                Text("• $txtDevicesLoseAuth")
                Spacer(Modifier.height(12.dp))
                Text("• $txtNeedReauth")
                Text("• $txtCannotUndo")
                Spacer(Modifier.height(16.dp))
                Text(
                    txtConfirmGenerate,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(txtConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(txtCancel)
            }
        },
    )
}

@Composable
private fun ImportKeysHintDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val txtTitle = AdbTexts.BUTTON_IMPORT_KEYS.get()
    val txtHint1 = AdbTexts.ADB_KEY_IMPORT_HINT.get()
    val txtHint2 = AdbTexts.ADB_KEY_IMPORT_HINT_MULTISELECT.get()
    val txtHint3 = AdbTexts.ADB_KEY_IMPORT_HINT_BOTH_FILES.get()
    val txtConfirm = CommonTexts.BUTTON_CONFIRM.get()
    val txtCancel = CommonTexts.BUTTON_CANCEL.get()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
        },
        title = {
            Text(txtTitle)
        },
        text = {
            Column {
                Text(
                    txtHint1,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text("• $txtHint2")
                Spacer(Modifier.height(8.dp))
                Text("• $txtHint3")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(txtConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(txtCancel)
            }
        },
    )
}
