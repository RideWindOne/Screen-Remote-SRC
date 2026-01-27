package com.mobile.scrcpy.android.feature.device.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import com.mobile.scrcpy.android.app.ScreenRemoteApp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.feature.device.viewmodel.ui.viewmodels.AdbKeysViewModel
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.DialogHeader
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import kotlinx.coroutines.launch

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbKeyManagementDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val adbConnectionManager = remember { ScreenRemoteApp.instance.adbConnectionManager }
    
    // 创建专用的 AdbKeysViewModel
    val viewModel: AdbKeysViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory =AdbKeysViewModel.provideFactory(context, adbConnectionManager)
    )
    
    var privateKeyVisible by remember { mutableStateOf(false) }
    var adbKeysDir by remember { mutableStateOf("") }
    var privateKeyEditable by remember { mutableStateOf("") }
    var publicKeyEditable by remember { mutableStateOf("") }
    var showGenerateDialog by remember { mutableStateOf(false) }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    var keysLoadStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val privateKeyFocusRequester = remember { FocusRequester() }

    // 双语文本
    val txtTitle = rememberText(AdbTexts.ADB_KEY_MANAGEMENT_TITLE)
    val txtKeyDir = rememberText(AdbTexts.ADB_KEY_DIR_LABEL)
    val txtPrivateKey = rememberText(AdbTexts.ADB_PRIVATE_KEY_LABEL)
    val txtPublicKey = rememberText(AdbTexts.ADB_PUBLIC_KEY_LABEL)
    val txtSaveSuccess = rememberText(AdbTexts.ADB_KEY_SAVE_SUCCESS)
    val txtSaveFailed = rememberText(AdbTexts.ADB_KEY_SAVE_FAILED)
    val txtImportSuccess = rememberText(AdbTexts.ADB_KEY_IMPORT_SUCCESS)
    val txtImportFailed = rememberText(AdbTexts.ADB_KEY_IMPORT_FAILED)
    val txtExportSuccess = rememberText(AdbTexts.ADB_KEY_EXPORT_SUCCESS)
    val txtExportFailed = rememberText(AdbTexts.ADB_KEY_EXPORT_FAILED)
    val txtGenerateSuccess = rememberText(AdbTexts.ADB_KEY_GENERATE_SUCCESS)
    val txtGenerateFailed = rememberText(AdbTexts.ADB_KEY_GENERATE_FAILED)
    val txtGenerateKeys = rememberText(AdbTexts.BUTTON_GENERATE_KEYS)
    val txtImportKeys = rememberText(AdbTexts.BUTTON_IMPORT_KEYS)
    val txtExportKeys = rememberText(AdbTexts.BUTTON_EXPORT_KEYS)
    val txtSaveKeys = rememberText(AdbTexts.BUTTON_SAVE_KEYS)
    val txtKeyNotFound = rememberText(AdbTexts.ADB_KEY_NOT_FOUND)
    val txtKeyInfo = rememberText(AdbTexts.LABEL_KEY_INFO)
    val txtKeyOperations = rememberText(AdbTexts.LABEL_KEY_OPERATIONS)
    val txtStatus = rememberText(CommonTexts.LABEL_STATUS)
    val txtHide = rememberText(CommonTexts.BUTTON_HIDE)
    val txtShow = rememberText(CommonTexts.BUTTON_SHOW)
    val txtClose = rememberText(CommonTexts.BUTTON_CLOSE)

    fun refreshKeys() {
        scope.launch {
            viewModel.getAdbKeysInfo().collect { info ->
                adbKeysDir = info.keysDir
                privateKeyEditable = info.privateKey
                publicKeyEditable = info.publicKey
                keysLoadStatus = if (info.privateKey.isNotEmpty() && info.publicKey.isNotEmpty()) {
                    "ADB keys loaded successfully"
                } else {
                    txtKeyNotFound
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshKeys()
    }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val dialogHeight = screenHeight * 0.8f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(dialogHeight),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                DialogHeader(
                    title = txtTitle,
                    onDismiss = onDismiss
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    // 密钥信息
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(txtKeyInfo)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                KeyInfoItem(
                                    label = txtKeyDir,
                                    value = adbKeysDir
                                )
                                AppDivider()
                                KeyEditItem(
                                    label = txtPrivateKey,
                                    value = privateKeyEditable,
                                    onValueChange = { privateKeyEditable = it },
                                    isVisible = privateKeyVisible,
                                    onVisibilityToggle = { privateKeyVisible = !privateKeyVisible },
                                    focusRequester = privateKeyFocusRequester,
                                    txtHide = txtHide,
                                    txtShow = txtShow
                                )
                                AppDivider()
                                KeyEditItem(
                                    label = txtPublicKey,
                                    value = publicKeyEditable,
                                    onValueChange = { publicKeyEditable = it },
                                    isVisible = true,
                                    onVisibilityToggle = null,
                                    focusRequester = null,
                                    txtHide = txtHide,
                                    txtShow = txtShow
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 密钥操作
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(txtKeyOperations)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                KeyActionItem(
                                    icon = Icons.Default.Save,
                                    title = txtSaveKeys,
                                    onClick = {
                                        scope.launch {
                                            val result = viewModel.saveAdbKeys(privateKeyEditable, publicKeyEditable)
                                            if (result.isSuccess) {
                                                snackbarMessage = txtSaveSuccess
                                                showSnackbar = true
                                                refreshKeys()
                                            } else {
                                                snackbarMessage = "$txtSaveFailed: ${result.exceptionOrNull()?.message}"
                                                showSnackbar = true
                                            }
                                        }
                                    }
                                )
                                AppDivider()
                                KeyActionItem(
                                    icon = Icons.Default.Download,
                                    title = txtImportKeys,
                                    onClick = {
                                        scope.launch {
                                            val result = viewModel.importAdbKeys(privateKeyEditable, publicKeyEditable)
                                            if (result.isSuccess) {
                                                snackbarMessage = txtImportSuccess
                                                showSnackbar = true
                                                refreshKeys()
                                            } else {
                                                snackbarMessage = "$txtImportFailed: ${result.exceptionOrNull()?.message}"
                                                showSnackbar = true
                                            }
                                        }
                                    }
                                )
                                AppDivider()
                                KeyActionItem(
                                    icon = Icons.Default.Upload,
                                    title = txtExportKeys,
                                    onClick = {
                                        scope.launch {
                                            val result = viewModel.exportAdbKeys()
                                            if (result.isSuccess) {
                                                snackbarMessage = "$txtExportSuccess: ${result.getOrNull()}"
                                                showSnackbar = true
                                            } else {
                                                snackbarMessage = "$txtExportFailed: ${result.exceptionOrNull()?.message}"
                                                showSnackbar = true
                                            }
                                        }
                                    }
                                )
                                AppDivider()
                                KeyActionItem(
                                    icon = Icons.Default.Key,
                                    title = txtGenerateKeys,
                                    onClick = { showGenerateDialog = true }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 状态
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(txtStatus)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(AppDimens.listItemHeight)
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = keysLoadStatus,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (keysLoadStatus.contains("successfully")) {
                                        Color(0xFF34C759)
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        if (showGenerateDialog) {
            GenerateKeyPairConfirmDialog(
                onConfirm = {
                    showGenerateDialog = false
                    scope.launch {
                        val result = viewModel.generateAdbKeys()
                        if (result.isSuccess) {
                            snackbarMessage = txtGenerateSuccess
                            showSnackbar = true
                            refreshKeys()
                        } else {
                            snackbarMessage = "$txtGenerateFailed: ${result.exceptionOrNull()?.message}"
                            showSnackbar = true
                        }
                    }
                },
                onDismiss = { showGenerateDialog = false }
            )
        }

        if (showSnackbar) {
            LaunchedEffect(snackbarMessage) {
                kotlinx.coroutines.delay(3000)
                showSnackbar = false
            }
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { }) {
                        Text(txtClose)
                    }
                }
            ) {
                Text(snackbarMessage)
            }
        }
    }
}

@Composable
fun KeyInfoItem(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun KeyEditItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isVisible: Boolean,
    onVisibilityToggle: (() -> Unit)?,
    focusRequester: FocusRequester?,
    txtHide: String = CommonTexts.BUTTON_HIDE.get(),
    txtShow: String = CommonTexts.BUTTON_SHOW.get()
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onVisibilityToggle != null) {
                TextButton(
                    onClick = onVisibilityToggle,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isVisible) txtHide else txtShow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isVisible) txtHide else txtShow,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isVisible) {
            val textFieldModifier = if (focusRequester != null) {
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            } else {
                Modifier.fillMaxWidth()
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = textFieldModifier,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 3,
                maxLines = 8
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onVisibilityToggle?.invoke() }
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = "••••••••••••••••••••",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 焦点请求逻辑：仅对可切换显示/隐藏的字段生效
    // 当 isVisible 从 false 变为 true 时自动聚焦
    if (focusRequester != null && onVisibilityToggle != null) {
        LaunchedEffect(isVisible) {
            if (isVisible) {
                // 延迟确保 TextField 已完成布局
                kotlinx.coroutines.delay(50)
                focusRequester.requestFocus()
            }
        }
    }
}

@Composable
fun KeyActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.listItemHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF007AFF),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
fun GenerateKeyPairConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
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
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
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
                    color = MaterialTheme.colorScheme.error
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
                    style = MaterialTheme.typography.titleSmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(txtConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(txtCancel)
            }
        }
    )
}
