package com.mobile.scrcpy.android.feature.device.ui.component

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.scrcpy.android.app.ScreenRemoteApp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.manager.LanguageManager.isChinese
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.feature.device.viewmodel.ui.viewmodels.AdbKeysViewModel
import kotlinx.coroutines.launch

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbKeyManagementDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val adbConnectionManager = remember { ScreenRemoteApp.instance.adbConnectionManager }

    // 创建专用的 AdbKeysViewModel
    val viewModel: AdbKeysViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = AdbKeysViewModel.provideFactory(context, adbConnectionManager),
        )

    var privateKeyVisible by remember { mutableStateOf(false) }
    var publicKeyVisible by remember { mutableStateOf(false) }
    var adbKeysDir by remember { mutableStateOf("") }
    var privateKeyEditable by remember { mutableStateOf("") }
    var publicKeyEditable by remember { mutableStateOf("") }
    var showGenerateDialog by remember { mutableStateOf(false) }
    var showImportHintDialog by remember { mutableStateOf(false) }
    var keysLoadStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val privateKeyFocusRequester = remember { FocusRequester() }
    val publicKeyFocusRequester = remember { FocusRequester() }

    // 双语文本（提前声明，供文件选择器回调使用）
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

    // 密钥刷新函数（提前声明，供文件选择器回调使用）
    fun refreshKeys() {
        scope.launch {
            viewModel.getAdbKeysInfo().collect { info ->
                adbKeysDir = info.keysDir
                privateKeyEditable = info.privateKey
                publicKeyEditable = info.publicKey
                keysLoadStatus =
                    if (info.privateKey.isNotEmpty() && info.publicKey.isNotEmpty()) {
                        "ADB keys loaded successfully"
                    } else {
                        txtKeyNotFound
                    }
            }
        }
    }

    // 文件选择器 - 导出（两步：先选私钥位置，再选公钥位置）
    var pendingPrivateKeyUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportPublicKeyLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri ->
            uri?.let { publicKeyUri ->
                pendingPrivateKeyUri?.let { privateKeyUri ->
                    scope.launch {
                        val result = viewModel.exportAdbKeysSeparately(privateKeyUri, publicKeyUri)
                        if (result.isSuccess) {
                            Toast.makeText(context, txtExportSuccess, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast
                                .makeText(
                                    context,
                                    "$txtExportFailed: ${result.exceptionOrNull()?.message}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                        pendingPrivateKeyUri = null
                    }
                }
            }
        }

    val exportPrivateKeyLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri ->
            uri?.let { privateKeyUri ->
                pendingPrivateKeyUri = privateKeyUri
                exportPublicKeyLauncher.launch("adbkey.pub")
            }
        }

    // 文件选择器 - 导入多个文件（提示用户长按多选）
    val importKeysLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isNotEmpty()) {
                scope.launch {
                    val result = viewModel.importAdbKeysFromUris(uris)
                    if (result.isSuccess) {
                        Toast.makeText(context, txtImportSuccess, Toast.LENGTH_SHORT).show()
                        refreshKeys()
                    } else {
                        Toast
                            .makeText(
                                context,
                                "$txtImportFailed: ${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            }
        }

    LaunchedEffect(Unit) {
        refreshKeys()
    }

    DialogPage(
        title = txtTitle,
        onDismiss = onDismiss,
        enableScroll = true,
        horizontalPadding = 10.dp,
        rightButtonText = txtSaveKeys,
        onRightButtonClick = {
            scope.launch {
                val result = viewModel.saveAdbKeys(privateKeyEditable, publicKeyEditable)
                if (result.isSuccess) {
                    Toast.makeText(context, txtSaveSuccess, Toast.LENGTH_SHORT).show()
                    refreshKeys()
                } else {
                    Toast
                        .makeText(
                            context,
                            "$txtSaveFailed: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        },
    ) {
        // 密钥信息
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(txtKeyInfo)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    KeyInfoItem(
                        label = txtKeyDir,
                        value = adbKeysDir,
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
                        txtShow = txtShow,
                    )
                    AppDivider()
                    KeyEditItem(
                        label = txtPublicKey,
                        value = publicKeyEditable,
                        onValueChange = { publicKeyEditable = it },
                        isVisible = publicKeyVisible,
                        onVisibilityToggle = { publicKeyVisible = !publicKeyVisible },
                        focusRequester = publicKeyFocusRequester,
                        txtHide = txtHide,
                        txtShow = txtShow,
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
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    KeyActionItem(
                        icon = Icons.Default.Save,
                        title = txtSaveKeys,
                        onClick = {
                            scope.launch {
                                val result = viewModel.saveAdbKeys(privateKeyEditable, publicKeyEditable)
                                if (result.isSuccess) {
                                    Toast.makeText(context, txtSaveSuccess, Toast.LENGTH_SHORT).show()
                                    refreshKeys()
                                } else {
                                    Toast
                                        .makeText(
                                            context,
                                            "$txtSaveFailed: ${result.exceptionOrNull()?.message}",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }
                        },
                    )
                    AppDivider()
                    KeyActionItem(
                        icon = Icons.Default.Download,
                        title = txtImportKeys,
                        onClick = {
                            showImportHintDialog = true
                        },
                    )
                    AppDivider()
                    KeyActionItem(
                        icon = Icons.Default.Upload,
                        title = txtExportKeys,
                        onClick = {
                            exportPrivateKeyLauncher.launch("adbkey")
                        },
                    )
                    AppDivider()
                    KeyActionItem(
                        icon = Icons.Default.Key,
                        title = txtGenerateKeys,
                        onClick = { showGenerateDialog = true },
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
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            ) {
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
                        text = keysLoadStatus,
                        style = MaterialTheme.typography.bodyLarge,
                        color =
                            if (keysLoadStatus.contains("successfully")) {
                                Color(0xFF34C759)
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                    )
                }
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
                        Toast.makeText(context, txtGenerateSuccess, Toast.LENGTH_SHORT).show()
                        refreshKeys()
                    } else {
                        Toast
                            .makeText(
                                context,
                                "$txtGenerateFailed: ${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            },
            onDismiss = { showGenerateDialog = false },
        )
    }

    if (showImportHintDialog) {
        ImportKeysHintDialog(
            onConfirm = {
                showImportHintDialog = false
                importKeysLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { showImportHintDialog = false },
        )
    }
}

@Composable
fun KeyInfoItem(
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
fun KeyEditItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isVisible: Boolean,
    onVisibilityToggle: (() -> Unit)?,
    focusRequester: FocusRequester?,
    txtHide: String = CommonTexts.BUTTON_HIDE.get(),
    txtShow: String = CommonTexts.BUTTON_SHOW.get(),
) {
    // 统一处理：所有密钥都可折叠
    Column(modifier = Modifier.fillMaxWidth()) {
        // 标题行（始终显示，列表高度）
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
                    color = Color(0xFF007AFF),
                )
                Icon(
                    if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isVisible) txtHide else txtShow,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF007AFF),
                )
            }
        }

        // 输入框区域
        if (!isVisible) {
            // 折叠状态：单行，显示隐藏内容（列表高度）
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(),
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
            // 展开状态：多行输入框，显示明文
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

    // 自动聚焦
    LaunchedEffect(isVisible) {
        if (isVisible && focusRequester != null) {
            kotlinx.coroutines.delay(50)
            focusRequester.requestFocus()
        }
    }
}

@Composable
fun KeyActionItem(
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
            tint = Color(0xFF007AFF),
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
fun GenerateKeyPairConfirmDialog(
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
        containerColor = Color.White,
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
fun ImportKeysHintDialog(
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
        containerColor = Color.White,
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
