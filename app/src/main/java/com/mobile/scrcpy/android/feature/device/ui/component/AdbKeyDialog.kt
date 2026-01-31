/**
 * ADB 密钥管理对话框
 * 
 * 主对话框组件，协调密钥信息显示、编辑和操作功能
 * 
 * 拆分结构：
 * - adbkey/KeyList.kt: 密钥列表和编辑组件
 * - adbkey/KeyActions.kt: 密钥操作按钮
 * - adbkey/KeyValidation.kt: 密钥验证对话框
 */
package com.mobile.scrcpy.android.feature.device.ui.component

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.app.ScreenRemoteApp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.common.util.FilePickerHelper
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.feature.device.ui.component.adbkey.GenerateKeyPairConfirmDialog
import com.mobile.scrcpy.android.feature.device.ui.component.adbkey.ImportKeysHintDialog
import com.mobile.scrcpy.android.feature.device.ui.component.adbkey.KeyActionItem
import com.mobile.scrcpy.android.feature.device.ui.component.adbkey.KeyEditItem
import com.mobile.scrcpy.android.feature.device.ui.component.adbkey.KeyInfoItem
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
    val txtClose = rememberText(CommonTexts.BUTTON_CLOSE) // TODO

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
        FilePickerHelper.rememberExportFileLauncher(
            mimeType = "application/octet-stream",
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
        FilePickerHelper.rememberExportFileLauncher(
            mimeType = "application/octet-stream",
        ) { uri ->
            uri?.let { privateKeyUri ->
                pendingPrivateKeyUri = privateKeyUri
                exportPublicKeyLauncher.launch("adbkey.pub")
            }
        }

    // 文件选择器 - 导入多个文件（提示用户长按多选）
    val importKeysLauncher =
        FilePickerHelper.rememberImportMultipleFilesLauncher { uris ->
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
