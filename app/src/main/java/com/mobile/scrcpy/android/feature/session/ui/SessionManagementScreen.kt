package com.mobile.scrcpy.android.feature.session.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper
import com.mobile.scrcpy.android.core.common.util.compat.putIfAbsentCompat
import com.mobile.scrcpy.android.core.common.util.compat.readAtMostBytesCompat
import com.mobile.scrcpy.android.core.common.util.formatHostPort
import com.mobile.scrcpy.android.core.data.repository.SessionData
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbBridge
import com.mobile.scrcpy.android.infrastructure.adb.shell.AdbShellManager
import dadb.helper.RemoteAppIconBatchRequest
import dadb.helper.RemoteAppListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.mobile.scrcpy.android.core.designsystem.component.IOSAlertDialog as AlertDialog

@Composable
fun SessionManagementScreen(
    sessionData: SessionData,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()
    var selectedSection by remember(sessionData.id) {
        mutableStateOf(SessionManagementSection.DeviceInfo)
    }
    var drawerOpen by remember(sessionData.id) { mutableStateOf(false) }
    var refreshTick by remember(sessionData.id) { mutableIntStateOf(0) }
    var appOptionsActionTick by remember(sessionData.id) { mutableIntStateOf(0) }
    var progressDialog by remember(sessionData.id) { mutableStateOf<ManagementProgressDialogState?>(null) }
    var resultDialog by remember(sessionData.id) { mutableStateOf<ManagementResultDialogState?>(null) }
    var exitConfirmOpen by remember(sessionData.id) { mutableStateOf(false) }
    var rebootDialogOpen by remember(sessionData.id) { mutableStateOf(false) }
    var activationDialogOpen by remember(sessionData.id) { mutableStateOf(false) }
    var standbyDialogOpen by remember(sessionData.id) { mutableStateOf(false) }
    var dpiDialogState by remember(sessionData.id) { mutableStateOf<ManagementValueInputDialogState?>(null) }
    var fixedPortDialogState by remember(sessionData.id) { mutableStateOf<ManagementValueInputDialogState?>(null) }
    var resolutionDialogState by remember(sessionData.id) { mutableStateOf<ResolutionDialogState?>(null) }
    var animationDialogState by remember(sessionData.id) { mutableStateOf<AnimationScaleDialogState?>(null) }
    var screenshotResult by remember(sessionData.id) { mutableStateOf<ScreenshotPreviewState?>(null) }
    var fileAddMenuOpenTick by remember(sessionData.id) { mutableIntStateOf(0) }
    var appAddMenuOpenTick by remember(sessionData.id) { mutableIntStateOf(0) }
    var fileSelectionMode by remember(sessionData.id) { mutableStateOf(false) }
    var commandInput by remember(sessionData.id) { mutableStateOf("") }
    var commandHistory by remember(sessionData.id) { mutableStateOf<List<ManagementCommandRecord>>(emptyList()) }
    var commandExecuting by remember(sessionData.id) { mutableStateOf(false) }
    var commandPresetDialogOpen by remember(sessionData.id) { mutableStateOf(false) }
    val commandTerminalSession = remember(sessionData.id) { ManagementTerminalSession(scope) }

    DisposableEffect(commandTerminalSession) {
        onDispose {
            commandTerminalSession.close()
        }
    }

    val snapshot by produceState(
        initialValue = DeviceDashboardSnapshot.loading(sessionData),
        key1 = sessionData.id,
        key2 = refreshTick,
    ) {
        value = loadDeviceDashboardSnapshot(sessionData)
    }

    val onRefresh =
        remember(sessionData.id, selectedSection) {
            when {
                selectedSection == SessionManagementSection.Apps -> {
                    { appOptionsActionTick += 1 }
                }

                selectedSection.supportsRefresh -> {
                    { refreshTick += 1 }
                }

                else -> {
                    null
                }
            }
        }

    DisposableEffect(activity) {
        activity?.window?.let { window ->
            ApiCompatHelper.setFullScreen(window, false)
        }
        onDispose {
            activity?.window?.let { window ->
                ApiCompatHelper.setFullScreen(window, false)
            }
        }
    }

    fun launchAction(
        title: String,
        message: String,
        block: suspend () -> Result<String>,
    ) {
        progressDialog = ManagementProgressDialogState(title = title, message = message)
        scope.launch {
            val result = block()
            progressDialog = null
            resultDialog =
                ManagementResultDialogState(
                    title = title,
                    message =
                        result.getOrNull()?.ifBlank { "$title 已完成。" }
                            ?: (result.exceptionOrNull()?.message ?: "$title 失败"),
                    isSuccess = result.isSuccess,
                )
            refreshTick++
        }
    }

    fun runManagementCommand(command: String) {
        val normalizedCommand = command.trim()
        if (normalizedCommand.isBlank() || commandExecuting) {
            return
        }

        commandInput = normalizedCommand
        commandExecuting = true
        scope.launch {
            val record = executeManagementShellCommand(normalizedCommand)
            commandHistory = (listOf(record) + commandHistory).take(SESSION_MANAGEMENT_COMMAND_HISTORY_MAX_SIZE)
            commandExecuting = false
        }
    }

    BackHandler {
        when {
            drawerOpen -> drawerOpen = false
            progressDialog != null -> progressDialog = null
            resultDialog != null -> resultDialog = null
            screenshotResult != null -> screenshotResult = null
            rebootDialogOpen -> rebootDialogOpen = false
            activationDialogOpen -> activationDialogOpen = false
            standbyDialogOpen -> standbyDialogOpen = false
            dpiDialogState != null -> dpiDialogState = null
            fixedPortDialogState != null -> fixedPortDialogState = null
            resolutionDialogState != null -> resolutionDialogState = null
            animationDialogState != null -> animationDialogState = null
            else -> exitConfirmOpen = true
        }
    }

    val topActionIcon =
        when (selectedSection) {
            SessionManagementSection.Apps -> Icons.Default.Tune
            SessionManagementSection.Command -> Icons.Default.Apps
            else -> Icons.Default.Refresh
        }
    val topActionContentDescription =
        when (selectedSection) {
            SessionManagementSection.Apps -> "应用选项"
            SessionManagementSection.Command -> "快捷命令"
            else -> "刷新"
        }
    val topActionCallback: (() -> Unit)? =
        when (selectedSection) {
            SessionManagementSection.Command -> {
                { commandPresetDialogOpen = true }
            }

            else -> {
                onRefresh
            }
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                SessionManagementDetailPane(
                    modifier = Modifier.fillMaxSize(),
                    sessionData = sessionData,
                    selectedSection = selectedSection,
                    snapshot = snapshot,
                    refreshToken = refreshTick,
                    appOptionsActionTick = appOptionsActionTick,
                    fileAddMenuOpenTick = fileAddMenuOpenTick,
                    appAddMenuOpenTick = appAddMenuOpenTick,
                    commandInput = commandInput,
                    commandHistory = commandHistory,
                    commandExecuting = commandExecuting,
                    commandPresetDialogOpen = commandPresetDialogOpen,
                    commandTerminalSession = commandTerminalSession,
                    onFileSelectionModeChanged = { fileSelectionMode = it },
                    onCommandInputChange = { commandInput = it },
                    onCommandExecute = ::runManagementCommand,
                    onCommandHistoryClear = { commandHistory = emptyList() },
                    onCommandPresetDialogChange = { commandPresetDialogOpen = it },
                    onUtilityAction = { action ->
                        when (action) {
                            UtilityAction.FixedPort -> {
                                fixedPortDialogState =
                                    ManagementValueInputDialogState(
                                        title = "固定端口",
                                        label = "端口号",
                                        initialValue = "5555",
                                        confirmText = "应用",
                                    )
                            }

                            UtilityAction.Screenshot -> {
                                progressDialog =
                                    ManagementProgressDialogState(
                                        title = "屏幕截图",
                                        message = "正在从目标设备获取截图并写入本机缓存目录。",
                                    )
                                scope.launch {
                                    val result = captureDeviceScreenshot(context)
                                    progressDialog = null
                                    result.fold(
                                        onSuccess = { file ->
                                            screenshotResult = ScreenshotPreviewState(file = file)
                                        },
                                        onFailure = { error ->
                                            resultDialog =
                                                ManagementResultDialogState(
                                                    title = "屏幕截图",
                                                    message = error.message ?: "截图失败",
                                                    isSuccess = false,
                                                )
                                        },
                                    )
                                }
                            }

                            UtilityAction.AdvancedReboot -> {
                                rebootDialogOpen = true
                            }

                            UtilityAction.ActivateApp -> {
                                activationDialogOpen = true
                            }

                            UtilityAction.ModifyDpi -> {
                                dpiDialogState =
                                    ManagementValueInputDialogState(
                                        title = "修改 DPI",
                                        label = "DPI 数值",
                                        initialValue = snapshot.currentDpiValue.orEmpty(),
                                        confirmText = "应用",
                                    )
                            }

                            UtilityAction.ModifyResolution -> {
                                resolutionDialogState =
                                    ResolutionDialogState(
                                        width = snapshot.currentResolutionWidth.orEmpty(),
                                        height = snapshot.currentResolutionHeight.orEmpty(),
                                    )
                            }

                            UtilityAction.AnimationScale -> {
                                animationDialogState =
                                    AnimationScaleDialogState(
                                        windowScale = "1.0",
                                        transitionScale = "1.0",
                                        durationScale = "1.0",
                                    )
                            }

                            UtilityAction.SleepStandby -> {
                                standbyDialogOpen = true
                            }

                            UtilityAction.TombstoneMode -> {
                                resultDialog =
                                    ManagementResultDialogState(
                                        title = "墓碑模式",
                                        message = "该功能暂未实现，当前保持禁用。",
                                        isSuccess = false,
                                    )
                            }
                        }
                    },
                )

                SessionManagementTopRow(
                    title = selectedSection.title,
                    onOpenMenu = { drawerOpen = true },
                    onRefresh = topActionCallback,
                    actionIcon = topActionIcon,
                    actionContentDescription = topActionContentDescription,
                )
            }
        }

        if (selectedSection == SessionManagementSection.Files && !drawerOpen && !fileSelectionMode) {
            SessionManagementAddFab(
                modifier = Modifier.align(Alignment.BottomEnd),
                contentDescription = "新增",
                onClick = { fileAddMenuOpenTick += 1 },
            )
        }

        if (selectedSection == SessionManagementSection.Apps && !drawerOpen) {
            SessionManagementAddFab(
                modifier = Modifier.align(Alignment.BottomEnd),
                contentDescription = "安装应用",
                onClick = { appAddMenuOpenTick += 1 },
            )
        }

        if (drawerOpen) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.22f)),
            )
        }

        if (drawerOpen) {
            SessionManagementDrawer(
                sessionData = sessionData,
                selectedSection = selectedSection,
                onDismiss = { drawerOpen = false },
                onSectionSelected = {
                    selectedSection = it
                    drawerOpen = false
                },
                onExit = {
                    drawerOpen = false
                    onBack()
                },
            )
        }
    }

    if (snapshot.isLoading && progressDialog == null) {
        SessionManagementProgressDialog(
            title = "管理功能",
            message = "正在获取设备信息",
        )
    }

    progressDialog?.let { dialog ->
        SessionManagementProgressDialog(
            title = dialog.title,
            message = dialog.message,
        )
    }

    resultDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { resultDialog = null },
            title = { Text(dialog.title) },
            text = { Text(dialog.message) },
            confirmButton = {
                TextButton(onClick = { resultDialog = null }) {
                    Text("确定")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    if (rebootDialogOpen) {
        SessionManagementRebootDialog(
            onDismiss = { rebootDialogOpen = false },
            onAction = { mode ->
                rebootDialogOpen = false
                launchAction(
                    title = "高级重启",
                    message = "正在执行 ${mode.label}。",
                ) {
                    runShellAction(mode.command, successMessage = "${mode.label}指令已发送。")
                }
            },
        )
    }

    if (exitConfirmOpen) {
        SessionManagementExitConfirmDialog(
            onDismiss = { exitConfirmOpen = false },
            onConfirm = {
                exitConfirmOpen = false
                onBack()
            },
        )
    }

    if (activationDialogOpen) {
        SessionManagementActivationDialog(
            refreshToken = refreshTick,
            onDismiss = { activationDialogOpen = false },
            onAction = { target ->
                activationDialogOpen = false
                launchAction(
                    title = "激活应用",
                    message = "正在执行 ${target.label} 激活命令。",
                ) {
                    runShellAction(
                        command = target.command,
                        successMessage = "${target.label} 激活命令已发送。",
                    )
                }
            },
            onUnavailable = { label ->
                activationDialogOpen = false
                resultDialog =
                    ManagementResultDialogState(
                        title = "激活应用",
                        message = "$label 当前未安装或暂不支持激活。",
                        isSuccess = false,
                    )
            },
        )
    }

    if (standbyDialogOpen) {
        SessionManagementStandbyDialog(
            onDismiss = { standbyDialogOpen = false },
            onAction = { action ->
                standbyDialogOpen = false
                launchAction(
                    title = "熄屏待机",
                    message = "正在执行${action.label}。",
                ) {
                    runShellAction(action.command, successMessage = "${action.label}指令已发送。")
                }
            },
        )
    }

    dpiDialogState?.let { dialog ->
        SessionManagementValueInputDialog(
            state = dialog,
            onDismiss = { dpiDialogState = null },
            onConfirm = { value ->
                dpiDialogState = null
                launchAction(
                    title = "修改 DPI",
                    message = "正在应用 DPI $value。",
                ) {
                    runShellAction("wm density $value", successMessage = "DPI 已修改为 $value。")
                }
            },
        )
    }

    fixedPortDialogState?.let { dialog ->
        SessionManagementValueInputDialog(
            state = dialog,
            onDismiss = { fixedPortDialogState = null },
            onConfirm = { value ->
                val port = value.toIntOrNull()
                if (port == null || port !in 1024..65535) {
                    resultDialog =
                        ManagementResultDialogState(
                            title = "固定端口",
                            message = "端口号无效，必须是 1024-65535 之间的数字。",
                            isSuccess = false,
                        )
                    fixedPortDialogState = null
                    return@SessionManagementValueInputDialog
                }

                fixedPortDialogState = null

                // 立即显示 Toast 提示
                Toast
                    .makeText(
                        context,
                        "正在设置端口 $port，ADB 服务将重启...",
                        Toast.LENGTH_LONG,
                    ).show()

                // 发送端口设置命令，不等待响应（因为会断开连接）
                scope.launch {
                    val connection = AdbBridge.getConnection()
                    if (connection == null) {
                        Toast
                            .makeText(
                                context,
                                "当前没有可用的 ADB 连接",
                                Toast.LENGTH_SHORT,
                            ).show()
                        return@launch
                    }

                    connection.restartTcpip(port)
                }
            },
        )
    }

    resolutionDialogState?.let { dialog ->
        SessionManagementResolutionDialog(
            state = dialog,
            onDismiss = { resolutionDialogState = null },
            onConfirm = { width, height ->
                resolutionDialogState = null
                launchAction(
                    title = "修改分辨率",
                    message = "正在应用 ${width}x$height。",
                ) {
                    runShellAction("wm size ${width}x$height", successMessage = "分辨率已修改为 ${width}x$height。")
                }
            },
        )
    }

    animationDialogState?.let { dialog ->
        SessionManagementAnimationDialog(
            state = dialog,
            onDismiss = { animationDialogState = null },
            onConfirm = { windowScale, transitionScale, durationScale ->
                animationDialogState = null
                launchAction(
                    title = "动画调整",
                    message = "正在应用动画缩放参数。",
                ) {
                    runShellAction(
                        command =
                            "settings put global window_animation_scale $windowScale && " +
                                "settings put global transition_animation_scale $transitionScale && " +
                                "settings put global animator_duration_scale $durationScale",
                        successMessage = "动画缩放参数已更新。",
                    )
                }
            },
        )
    }

    screenshotResult?.let { state ->
        SessionManagementScreenshotDialog(
            state = state,
            onDismiss = { screenshotResult = null },
            onOpen = {
                openImagePreview(context, state.file)
            },
            onSave = {
                scope.launch {
                    val result = saveImageToGallery(context, state.file)
                    if (result.isSuccess) {
                        screenshotResult = null
                        Toast.makeText(context, "截图已保存到相册", Toast.LENGTH_SHORT).show()
                    } else {
                        resultDialog =
                            ManagementResultDialogState(
                                title = "保存截图",
                                message = result.exceptionOrNull()?.message ?: "保存到相册失败",
                                isSuccess = false,
                            )
                    }
                }
            },
        )
    }
}

private val SessionManagementFabInset = 20.dp
private val SessionManagementFabIconSize = 18.dp
private val SessionManagementFabAccent = Color(0xFFFF6E95)
private val SessionManagementTopBarHeight = 52.dp
private val SessionManagementTopBarSideWidth = 52.dp
private val SessionManagementTopBarActionSize = 40.dp
private val SessionManagementTopBarHorizontalInset = 4.dp
private val SessionManagementDrawerWidth = 248.dp
private val SessionManagementDrawerEdgeCornerRadius = 22.dp
private val SessionManagementCardCornerRadius = 20.dp
private val SessionManagementPanelCornerRadius = 16.dp
private val SessionManagementOptionCornerRadius = 14.dp
private val SessionManagementDrawerPadding = 12.dp
private val SessionManagementDrawerHeaderPadding = 18.dp
private val SessionManagementDrawerSectionSpacing = 12.dp
private val SessionManagementDrawerItemSpacing = 6.dp
private val SessionManagementSurfaceElevation = 1.dp
private val SessionManagementContentHorizontalPadding = 20.dp
private val SessionManagementProcessContentHorizontalPadding = 8.dp
private val SessionManagementContentBottomPadding = 20.dp
private val SessionManagementContentTopPadding = 110.dp
private const val SessionManagementProcessListLimit = 40
private val SessionManagementProgressSpacing = 14.dp
private val SessionManagementProgressIndicatorSize = 22.dp
private val SessionManagementAppRowSpacing = 12.dp
private val SessionManagementAppRowVerticalPadding = 10.dp
private val SessionManagementAppRowMetaSpacing = 2.dp
private val SessionManagementAppBadgeSpacing = 6.dp
private val SessionManagementAppAvatarSize = 42.dp
private val SessionManagementAppAvatarImageSize = 28.dp
private val SessionManagementAppAvatarFallbackIconSize = 22.dp
private val SessionManagementDisabledAppBadgeAccent = Color(0xFFFF8A65)
private val SessionManagementSystemAppBadgeAccent = Color(0xFF7BA7FF)
private val SessionManagementSystemAppAvatarAccent = Color(0xFF69A7FF)
private val SessionManagementUserAppAvatarAccent = Color(0xFF62C97B)
private val SessionManagementInfoCardHorizontalPadding = 14.dp
private val SessionManagementInfoCardVerticalPadding = 16.dp
private val SessionManagementAppOptionsMenuWidth = 264.dp
private val SessionManagementAppOptionsMenuOffset = DpOffset(x = (-6).dp, y = (-56).dp)
private val SessionManagementActionRowCornerRadius = 12.dp
private val SessionManagementActionRowHorizontalPadding = 8.dp
private val SessionManagementActionRowVerticalPadding = 12.dp
private val SessionManagementSectionTitleHorizontalPadding = 16.dp
private val SessionManagementSectionTitleVerticalPadding = 8.dp
private val SessionManagementFileDialogSpacing = 12.dp
private val SessionManagementFileDialogInfoSpacing = 10.dp
private val SessionManagementFileDialogButtonSpacing = 4.dp
private val SessionManagementFileDialogHeaderIconSize = 22.dp
private val SessionManagementFileDialogLoadingIndicatorSize = 20.dp
private val SessionManagementFileActionCardPadding = 8.dp
private val SessionManagementFileActionCardSpacing = 6.dp
private val SessionManagementFileMenuRowCornerRadius = 16.dp
private val SessionManagementFileMenuRowHorizontalPadding = 12.dp
private val SessionManagementFileMenuRowVerticalPadding = 12.dp
private val SessionManagementFileMenuRowIconSize = 20.dp
private val SessionManagementTextEditorHorizontalPadding = 16.dp
private val SessionManagementTextEditorVerticalPadding = 12.dp
private val SessionManagementTextEditorTopActionSpacing = 4.dp
private val SessionManagementImagePreviewHeight = 280.dp
private val SessionManagementVideoPreviewHeight = 220.dp
private val SessionManagementBinaryPreviewHeight = 260.dp

@Composable
private fun SessionManagementAddFab(
    modifier: Modifier = Modifier,
    contentDescription: String,
    onClick: () -> Unit,
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier =
            modifier
                .navigationBarsPadding()
                .padding(end = SessionManagementFabInset, bottom = SessionManagementFabInset),
        containerColor = SessionManagementFabAccent,
        contentColor = Color.White,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = contentDescription,
            modifier = Modifier.size(SessionManagementFabIconSize),
        )
    }
}

@Composable
private fun SessionManagementTopRow(
    title: String,
    onOpenMenu: () -> Unit,
    onRefresh: (() -> Unit)?,
    actionIcon: ImageVector = Icons.Default.Refresh,
    actionContentDescription: String = "刷新",
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .zIndex(1f),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
        tonalElevation = SessionManagementSurfaceElevation,
    ) {
        Column(
            modifier = Modifier.statusBarsPadding(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(SessionManagementTopBarHeight)
                        .padding(
                            start = SessionManagementTopBarHorizontalInset,
                            end = SessionManagementTopBarHorizontalInset,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.width(SessionManagementTopBarSideWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = onOpenMenu,
                        modifier = Modifier.size(SessionManagementTopBarActionSize),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "打开菜单",
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box(
                    modifier = Modifier.width(SessionManagementTopBarSideWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    if (onRefresh != null) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(SessionManagementTopBarActionSize),
                        ) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = actionContentDescription,
                            )
                        }
                    }
                }
            }
            AppDivider()
        }
    }
}

@Composable
private fun SessionManagementDrawer(
    sessionData: SessionData,
    selectedSection: SessionManagementSection,
    onDismiss: () -> Unit,
    onSectionSelected: (SessionManagementSection) -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        onClick = onDismiss,
                        indication = null,
                        interactionSource =
                            remember {
                                androidx.compose.foundation.interaction
                                    .MutableInteractionSource()
                            },
                    ),
        )

        Surface(
            modifier =
                Modifier
                    .width(SessionManagementDrawerWidth)
                    .fillMaxHeight(),
            shape =
                RoundedCornerShape(
                    topEnd = SessionManagementDrawerEdgeCornerRadius,
                    bottomEnd = SessionManagementDrawerEdgeCornerRadius,
                ),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(
                            horizontal = SessionManagementDrawerPadding,
                            vertical = SessionManagementDrawerPadding,
                        ),
            ) {
                Surface(
                    shape = RoundedCornerShape(SessionManagementCardCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = SessionManagementSurfaceElevation,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = SessionManagementDrawerHeaderPadding,
                                    vertical = SessionManagementDrawerHeaderPadding,
                                ),
                        verticalArrangement = Arrangement.spacedBy(SessionManagementDrawerSectionSpacing),
                    ) {
                        Text(
                            text = "当前设备",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = sessionData.name.ifBlank { sessionData.host },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text =
                                if (sessionData.isUsbConnection()) {
                                    sessionData.getUsbSerialNumber().orEmpty().ifBlank { sessionData.host }
                                } else {
                                    formatHostPort(sessionData.host, sessionData.port)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(top = SessionManagementDrawerSectionSpacing, start = 2.dp, end = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(SessionManagementDrawerItemSpacing),
                ) {
                    SessionManagementSection.entries.forEach { section ->
                        SessionManagementDrawerItem(
                            section = section,
                            selected = selectedSection == section,
                            onClick = { onSectionSelected(section) },
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(SessionManagementDrawerItemSpacing),
                ) {
                    SessionManagementDrawerActionItem(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        title = "退出",
                        onClick = onExit,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionManagementDetailPane(
    modifier: Modifier = Modifier,
    sessionData: SessionData,
    selectedSection: SessionManagementSection,
    snapshot: DeviceDashboardSnapshot,
    refreshToken: Int,
    appOptionsActionTick: Int,
    fileAddMenuOpenTick: Int,
    appAddMenuOpenTick: Int,
    commandInput: String,
    commandHistory: List<ManagementCommandRecord>,
    commandExecuting: Boolean,
    commandPresetDialogOpen: Boolean,
    commandTerminalSession: ManagementTerminalSession,
    onFileSelectionModeChanged: (Boolean) -> Unit,
    onCommandInputChange: (String) -> Unit,
    onCommandExecute: (String) -> Unit,
    onCommandHistoryClear: () -> Unit,
    onCommandPresetDialogChange: (Boolean) -> Unit,
    onUtilityAction: (UtilityAction) -> Unit,
) {
    val horizontalPadding =
        if (selectedSection == SessionManagementSection.Process) {
            SessionManagementProcessContentHorizontalPadding
        } else {
            SessionManagementContentHorizontalPadding
        }

    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        contentPadding =
            PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = SessionManagementContentTopPadding,
                bottom = SessionManagementContentBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (selectedSection) {
            SessionManagementSection.DeviceInfo -> {
                item {
                    SessionManagementHomeSnapshot(snapshot = snapshot)
                }
            }

            SessionManagementSection.Utility -> {
                item {
                    SessionManagementUtilityList(
                        sessionData = sessionData,
                        snapshot = snapshot,
                        onAction = onUtilityAction,
                    )
                }
            }

            SessionManagementSection.Files -> {
                item {
                    SessionManagementFileBrowser(
                        modifier = Modifier.fillParentMaxHeight(),
                        refreshToken = refreshToken,
                        externalAddMenuRequestTick = fileAddMenuOpenTick,
                        onSelectionModeChanged = onFileSelectionModeChanged,
                    )
                }
            }

            SessionManagementSection.Apps -> {
                item {
                    SessionManagementAppsPage(
                        modifier = Modifier.fillParentMaxHeight(),
                        refreshToken = refreshToken,
                        topActionTick = appOptionsActionTick,
                        addMenuRequestTick = appAddMenuOpenTick,
                    )
                }
            }

            SessionManagementSection.Process -> {
                item {
                    SessionManagementProcessPage(
                        snapshot = snapshot,
                        refreshToken = refreshToken,
                    )
                }
            }

            SessionManagementSection.Command -> {
                item {
                    SessionManagementCommandPage(
                        modifier = Modifier.fillParentMaxHeight(),
                        terminalSession = commandTerminalSession,
                        commandInput = commandInput,
                        history = commandHistory,
                        isExecuting = commandExecuting,
                        onCommandInputChange = onCommandInputChange,
                        onExecuteCommand = onCommandExecute,
                        onClearHistory = onCommandHistoryClear,
                        showPresetDialog = commandPresetDialogOpen,
                        onShowPresetDialogChange = onCommandPresetDialogChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionManagementUtilityList(
    sessionData: SessionData,
    snapshot: DeviceDashboardSnapshot,
    onAction: (UtilityAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        utilityItems(
            isTcpipMode = !sessionData.isUsbConnection(),
            snapshot = snapshot,
        ).forEach { item ->
            SessionManagementUtilityCard(
                item = item,
                onClick = { onAction(item.action) },
            )
        }
    }
}

@Composable
private fun SessionManagementUtilityCard(
    item: UtilityCardItem,
    onClick: () -> Unit,
) {
    val cardTint = MaterialTheme.colorScheme.surface
    val iconTintBackground =
        if (item.available) {
            item.accent.copy(alpha = 0.16f)
        } else {
            item.accent.copy(alpha = 0.08f)
        }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardTint,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = item.available, onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = iconTintBackground,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (item.available) item.accent else item.accent.copy(alpha = 0.48f),
                    modifier =
                        Modifier
                            .padding(10.dp)
                            .size(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (item.available) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (item.available) 1f else 0.72f),
                )
            }

            when {
                item.statusChecked != null -> {
                    SessionManagementUtilityCheckBadge(
                        checked = item.statusChecked,
                        accent = item.accent,
                    )
                }

                item.statusText != null -> {
                    SessionManagementUtilityBadge(
                        text = item.statusText,
                        accent = item.accent,
                        available = item.available,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionManagementUtilityBadge(
    text: String,
    accent: Color = Color.Unspecified,
    available: Boolean = true,
) {
    val resolvedAccent =
        if (accent == Color.Unspecified) {
            MaterialTheme.colorScheme.outlineVariant
        } else {
            accent
        }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (available) {
                    resolvedAccent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SessionManagementUtilityCheckBadge(
    checked: Boolean,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp,
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.Check else Icons.Default.Close,
            contentDescription = if (checked) "已开启" else "未开启",
            tint = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .size(16.dp),
        )
    }
}

@Composable
internal fun SessionManagementAppRow(
    entry: AppInventoryEntry,
    packageNameOnlyMode: Boolean,
    presentationVersion: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val presentation by produceState(
        initialValue =
            RemoteAppPresentation(
                title = entry.appTitle,
                icon = SessionManagementAppCache.cachedIcon(entry.packageName),
            ),
        entry.packageName,
        entry.apkPath,
        presentationVersion,
        packageNameOnlyMode,
    ) {
        value = loadCachedAppPresentation(context, entry, packageNameOnlyMode)
    }
    val appTitle = presentation.title
    val iconBitmap = presentation.icon

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SessionManagementPanelCornerRadius))
                .clickable(onClick = onClick)
                .padding(vertical = SessionManagementAppRowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(SessionManagementAppRowSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionManagementAppAvatar(
            packageName = entry.packageName,
            appTitle = appTitle,
            isSystemApp = entry.isSystemApp,
            iconBitmap = iconBitmap,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SessionManagementAppRowMetaSpacing),
        ) {
            Text(
                text = appTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(SessionManagementAppBadgeSpacing),
        ) {
            if (!entry.isEnabled) {
                SessionManagementUtilityBadge(
                    text = "已禁用",
                    accent = SessionManagementDisabledAppBadgeAccent,
                )
            }
            if (entry.isSystemApp) {
                SessionManagementUtilityBadge(
                    text = "系统",
                    accent = SessionManagementSystemAppBadgeAccent,
                )
            }
        }
    }
}

@Composable
private fun SessionManagementAppAvatar(
    packageName: String,
    appTitle: String,
    isSystemApp: Boolean,
    iconBitmap: Bitmap?,
) {
    val accent = if (isSystemApp) SessionManagementSystemAppAvatarAccent else SessionManagementUserAppAvatarAccent
    val initial = appTitle.firstOrNull()?.uppercaseChar()

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.16f),
    ) {
        Box(
            modifier = Modifier.size(SessionManagementAppAvatarSize),
            contentAlignment = Alignment.Center,
        ) {
            when {
                iconBitmap != null -> {
                    Image(
                        bitmap = iconBitmap.asImageBitmap(),
                        contentDescription = packageName,
                        modifier = Modifier.size(SessionManagementAppAvatarImageSize),
                    )
                }

                initial != null && initial.code > 32 -> {
                    Text(
                        text = initial.toString(),
                        color = accent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(SessionManagementAppAvatarFallbackIconSize),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SessionManagementAppActionDialog(
    entry: AppInventoryEntry,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onLaunch: () -> Unit,
    onToggleEnabled: () -> Unit,
    onUninstall: () -> Unit,
    onClearData: () -> Unit,
    onDownloadApk: () -> Unit,
) {
    val actionLabel = if (entry.isEnabled) "停用" else "启用"
    val iconBitmap = SessionManagementAppCache.cachedIcon(entry.packageName)
    val appTitle = SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle)

    AlertDialog(
        onDismissRequest = onDismiss,
        widthRatio = 0.9f,
        title = {
            SessionManagementAppDialogHeader(
                appTitle = appTitle,
                packageName = entry.packageName,
                isSystemApp = entry.isSystemApp,
                isEnabled = entry.isEnabled,
                iconBitmap = iconBitmap,
            )
        },
        text = {
            SessionManagementDialogCard {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SessionManagementActionRow(icon = Icons.Default.Info, label = "应用详情", onClick = onDetails)
                    SessionManagementActionRow(
                        icon = Icons.Default.PlayArrow,
                        label = "在设备上启动",
                        onClick = onLaunch,
                    )
                    SessionManagementActionRow(
                        icon = Icons.Default.VerifiedUser,
                        label = actionLabel,
                        onClick = onToggleEnabled,
                    )
                    SessionManagementActionRow(
                        icon = Icons.Default.DeleteOutline,
                        label = "卸载",
                        onClick = onUninstall,
                    )
                    SessionManagementActionRow(icon = Icons.Default.Build, label = "清除数据", onClick = onClearData)
                    SessionManagementActionRow(
                        icon = Icons.Default.Download,
                        label = "下载安装包",
                        onClick = onDownloadApk,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SessionManagementAppDialogHeader(
    appTitle: String,
    packageName: String,
    isSystemApp: Boolean,
    isEnabled: Boolean,
    iconBitmap: Bitmap?,
    packageNameMaxLines: Int = 2,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionManagementAppAvatar(
            packageName = packageName,
            appTitle = appTitle,
            isSystemApp = isSystemApp,
            iconBitmap = iconBitmap,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = appTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = packageNameMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(SessionManagementAppBadgeSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isEnabled) {
                    SessionManagementUtilityBadge(
                        text = "已禁用",
                        accent = SessionManagementDisabledAppBadgeAccent,
                    )
                }
                if (isSystemApp) {
                    SessionManagementUtilityBadge(
                        text = "系统",
                        accent = SessionManagementSystemAppBadgeAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionManagementInfoLoadingCard(labels: List<String>) {
    SessionManagementDialogCard {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SessionManagementInfoCardHorizontalPadding,
                        vertical = SessionManagementInfoCardVerticalPadding,
                    ),
            verticalArrangement = Arrangement.Top,
        ) {
            labels.forEachIndexed { index, label ->
                SessionManagementInfoPlaceholderRow(
                    label = label,
                    labelWidth = SessionManagementAppDetailLabelWidth,
                    rowMinHeight = SessionManagementAppDetailRowMinHeight,
                )
                if (index != labels.lastIndex) {
                    AppDivider(modifier = Modifier.padding(start = SessionManagementAppDetailDividerInset))
                }
            }
        }
    }
}

@Composable
private fun SessionManagementAppDetailContent(detail: AppDetailSnapshot) {
    when {
        detail.isLoading -> {
            SessionManagementInfoLoadingCard(
                labels =
                    listOf(
                        "包名",
                        "安装包大小",
                        "版本名",
                        "系统应用",
                        "兼容SDK版本",
                        "目标SDK版本",
                        "首次安装时间",
                        "上次更新时间",
                    ),
            )
        }

        detail.errorMessage != null -> {
            SessionManagementNoteCard(
                title = "应用详情读取失败",
                text = detail.errorMessage,
            )
        }

        else -> {
            val detailItems =
                listOf(
                    AppDetailItem(
                        label = "包名",
                        value = detail.packageName,
                        useSmallText = true,
                    ),
                    AppDetailItem(
                        label = "安装包大小",
                        value = detail.apkSize,
                    ),
                    AppDetailItem(
                        label = "版本名",
                        value = detail.versionName,
                    ),
                    AppDetailItem(
                        label = "系统应用",
                        value = if (detail.isSystemApp) "是" else "否",
                    ),
                    AppDetailItem(
                        label = "兼容SDK版本",
                        value = detail.minSdk,
                    ),
                    AppDetailItem(
                        label = "目标SDK版本",
                        value = detail.targetSdk,
                    ),
                    AppDetailItem(
                        label = "首次安装时间",
                        value = detail.firstInstallTime,
                        useSmallText = true,
                    ),
                    AppDetailItem(
                        label = "上次更新时间",
                        value = detail.lastUpdateTime,
                        useSmallText = true,
                    ),
                )
            SessionManagementDialogCard {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = SessionManagementInfoCardHorizontalPadding,
                                vertical = SessionManagementInfoCardVerticalPadding,
                            ),
                    verticalArrangement = Arrangement.Top,
                ) {
                    detailItems.forEachIndexed { index, item ->
                        SessionManagementInfoRow(
                            label = item.label,
                            value = item.value,
                            labelWidth = SessionManagementAppDetailLabelWidth,
                            rowMinHeight = SessionManagementAppDetailRowMinHeight,
                            valueTextStyle =
                                if (item.useSmallText) {
                                    MaterialTheme.typography.bodySmall
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                            valueMaxLines = 1,
                        )
                        if (index != detailItems.lastIndex) {
                            AppDivider(modifier = Modifier.padding(start = SessionManagementAppDetailDividerInset))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SessionManagementAppOptionsMenu(
    expanded: Boolean,
    selectedFilters: Set<AppListFilter>,
    selectedSort: AppListSort,
    packageNameOnlyMode: Boolean,
    onDismiss: () -> Unit,
    onRefreshList: () -> Unit,
    onSortSelected: (AppListSort) -> Unit,
    onPackageNameOnlyModeChanged: (Boolean) -> Unit,
    onToggleFilter: (AppListFilter) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(SessionManagementAppOptionsMenuWidth),
        offset = SessionManagementAppOptionsMenuOffset,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DropdownMenuItem(
            text = { Text("刷新应用列表") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                )
            },
            onClick = onRefreshList,
        )
        HorizontalDivider()
        SessionManagementAppOptionsSectionTitle("排序方式")
        AppListSort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (selectedSort == option) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                        )
                    }
                },
                onClick = { onSortSelected(option) },
            )
        }
        HorizontalDivider()
        SessionManagementAppOptionsSectionTitle("加载方式")
        DropdownMenuItem(
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("只加载包名")
                    Text(
                        text = "停止远程解析应用名和图标，仅使用已有缓存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = packageNameOnlyMode,
                    onCheckedChange = null,
                )
            },
            onClick = { onPackageNameOnlyModeChanged(!packageNameOnlyMode) },
        )
        HorizontalDivider()
        SessionManagementAppOptionsSectionTitle("显示筛选")
        AppListFilter.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    Checkbox(
                        checked = option in selectedFilters,
                        onCheckedChange = null,
                    )
                },
                onClick = { onToggleFilter(option) },
            )
        }
    }
}

@Composable
private fun SessionManagementAppOptionsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier.padding(
                horizontal = SessionManagementSectionTitleHorizontalPadding,
                vertical = SessionManagementSectionTitleVerticalPadding,
            ),
    )
}

@Composable
private fun SessionManagementActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SessionManagementActionRowCornerRadius))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(
                    horizontal = SessionManagementActionRowHorizontalPadding,
                    vertical = SessionManagementActionRowVerticalPadding,
                ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.outline
                },
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
        )
    }
}

@Composable
internal fun SessionManagementAppDetailDialog(
    entry: AppInventoryEntry,
    onDismiss: () -> Unit,
) {
    val detail by produceState(
        initialValue = SessionManagementAppCache.cachedAppDetail(entry.packageName) ?: AppDetailSnapshot.loading(entry),
        key1 = entry.packageName,
    ) {
        value = loadAppDetailSnapshot(entry)
    }
    val iconBitmap = SessionManagementAppCache.cachedIcon(entry.packageName)

    AlertDialog(
        onDismissRequest = onDismiss,
        widthRatio = 0.92f,
        title = {
            SessionManagementAppDialogHeader(
                appTitle = detail.appTitle,
                packageName = detail.packageName,
                isSystemApp = detail.isSystemApp,
                isEnabled = entry.isEnabled,
                iconBitmap = iconBitmap,
                packageNameMaxLines = 1,
            )
        },
        text = { SessionManagementAppDetailContent(detail) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
internal fun SessionManagementAppUninstallDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    val detail by produceState(
        initialValue = SessionManagementAppCache.cachedAppDetail(packageName) ?: AppDetailSnapshot.loading(packageName),
        key1 = packageName,
    ) {
        val appEntry =
            SessionManagementAppCache
                .snapshot()
                ?.apps
                ?.firstOrNull { it.packageName == packageName }
                ?: AppInventoryEntry(
                    packageName = packageName,
                    appTitle = SessionManagementAppCache.appTitle(packageName, guessAppTitle(packageName)),
                    isSystemApp = false,
                    apkPath = "",
                    isEnabled = true,
                )
        value = loadAppDetailSnapshot(appEntry)
    }
    var keepData by remember(packageName) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (detail.isSystemApp) "该应用为系统应用，请谨慎卸载" else "确认卸载 $packageName",
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { keepData = !keepData }
                            .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SessionManagementUtilityBadge(
                        text = if (keepData) "保留" else "不保留",
                        accent = Color(0xFF7BA7FF),
                        available = keepData,
                    )
                    Text("尝试保留应用数据")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(keepData) }) {
                Text("卸载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SessionManagementProcessPage(
    snapshot: DeviceDashboardSnapshot,
    refreshToken: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val expandedPackages = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    var actionProgress by remember { mutableStateOf<String?>(null) }
    var actionResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        SessionManagementAppCache.prepareForProcess(context)
    }

    val processSnapshot by produceState(
        initialValue = ProcessListSnapshot.loading(),
        key1 = refreshToken,
    ) {
        value = loadProcessListSnapshot()
    }

    val totalMemoryBytes = parseDisplayBytes(snapshot.memoryTotal)
    val availableMemoryBytes = parseDisplayBytes(snapshot.memoryAvailable)
    val usedMemoryBytes =
        if (totalMemoryBytes != null && availableMemoryBytes != null) {
            (totalMemoryBytes - availableMemoryBytes).coerceAtLeast(0L)
        } else {
            null
        }
    val progress =
        if (totalMemoryBytes != null && totalMemoryBytes > 0 && usedMemoryBytes != null) {
            usedMemoryBytes.toFloat() / totalMemoryBytes.toFloat()
        } else {
            0f
        }
    val processEntries = processSnapshot.entries
    val topMemoryEntry = processEntries.maxByOrNull { it.totalMemoryBytes }
    val appProcessCount = processEntries.sumOf { 1 + it.children.size }

    fun stopProcess(entry: ProcessEntry) {
        actionProgress = "正在结束 ${entry.appTitle}"
        scope.launch {
            val result =
                runCatching {
                    val connection = AdbBridge.getConnection() ?: error("当前没有可用的 ADB 连接。")
                    connection
                        .executeShell("am force-stop ${entry.packageName}", retryOnFailure = false)
                        .getOrThrow()
                    "已尝试结束 ${entry.packageName} 的运行进程。"
                }
            actionProgress = null
            actionResult = result.getOrNull() ?: (result.exceptionOrNull()?.message ?: "结束进程失败。")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "设备内存",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = snapshot.memoryTotal.ifBlank { "--" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(999.dp)),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SessionManagementProcessStatTile(
                        label = "已用",
                        value = usedMemoryBytes?.let(::formatBytes) ?: "--",
                        accent = Color(0xFFFFA94D),
                        modifier = Modifier.weight(1f),
                    )
                    SessionManagementProcessStatTile(
                        label = "可用",
                        value = snapshot.memoryAvailable.ifBlank { "--" },
                        accent = Color(0xFF4CB782),
                        modifier = Modifier.weight(1f),
                    )
                    SessionManagementProcessStatTile(
                        label = "应用进程",
                        value = if (processEntries.isEmpty()) "--" else appProcessCount.toString(),
                        accent = Color(0xFF64B5F6),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        when {
            processSnapshot.isLoading -> {
                SessionManagementNoteCard(
                    title = "正在读取进程列表",
                    text = "当前通过 ADB 加载正在运行的应用进程和内存占用。",
                )
            }

            processSnapshot.errorMessage != null -> {
                SessionManagementNoteCard(
                    title = "进程列表读取失败",
                    text = processSnapshot.errorMessage ?: "进程列表读取失败。",
                )
            }

            else -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "运行中的应用",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text =
                                        topMemoryEntry?.let {
                                            "最高占用 ${it.appTitle} · ${it.memory}"
                                        } ?: "按内存占用排序",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            SessionManagementUtilityBadge(
                                text = "${processEntries.size} 项",
                                accent = Color(0xFF64B5F6),
                                available = true,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            processEntries.forEachIndexed { index, entry ->
                                val expanded = expandedPackages[entry.packageName] == true
                                SessionManagementProcessRow(
                                    entry = entry,
                                    rank = index + 1,
                                    expanded = expanded,
                                    onToggleExpanded = {
                                        expandedPackages[entry.packageName] = !expanded
                                    },
                                    onStop = { stopProcess(entry) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    actionProgress?.let { message ->
        SessionManagementProgressDialog(
            title = "进程管理",
            message = message,
        )
    }

    actionResult?.let { message ->
        AlertDialog(
            onDismissRequest = { actionResult = null },
            title = { Text("进程管理") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { actionResult = null }) {
                    Text("确定")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun SessionManagementProcessStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.12f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SessionManagementProcessRow(
    entry: ProcessEntry,
    rank: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val hasChildren = entry.children.isNotEmpty()
    val isSystemApp = entry.packageName.startsWith("com.android") || entry.packageName.startsWith("android")
    val presentation by produceState(
        initialValue =
            RemoteAppPresentation(
                title = entry.appTitle,
                icon = SessionManagementAppCache.cachedIcon(entry.packageName),
            ),
        entry.packageName,
        entry.appTitle,
        entry.totalMemoryBytes,
    ) {
        value =
            loadCachedAppPresentation(
                context = context,
                entry =
                    AppInventoryEntry(
                        packageName = entry.packageName,
                        appTitle = entry.appTitle,
                        isSystemApp = isSystemApp,
                        apkPath = "",
                        isEnabled = true,
                    ),
                packageNameOnlyMode = false,
            )
    }
    val displayTitle = presentation.title
    val iconBitmap = presentation.icon

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.5.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SessionManagementAppAvatar(
                    packageName = entry.packageName,
                    appTitle = displayTitle.ifBlank { rank.toString() },
                    isSystemApp = isSystemApp,
                    iconBitmap = iconBitmap,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = entry.memory,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )

                if (hasChildren) {
                    IconButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "收起子进程" else "展开子进程",
                        )
                    }
                } else {
                    Box(modifier = Modifier.size(36.dp))
                }

                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "结束进程",
                        tint = Color(0xFF4EA3F1),
                    )
                }
            }

            if (expanded && hasChildren) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                ) {
                    entry.children.forEach { child ->
                        SessionManagementProcessChildRow(
                            entry = entry,
                            appTitle = displayTitle,
                            child = child,
                            iconBitmap = iconBitmap,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionManagementProcessChildRow(
    entry: ProcessEntry,
    appTitle: String,
    child: ProcessChildEntry,
    iconBitmap: Bitmap?,
) {
    val isSystemApp = entry.packageName.startsWith("com.android") || entry.packageName.startsWith("android")

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionManagementAppAvatar(
            packageName = entry.packageName,
            appTitle = appTitle,
            isSystemApp = isSystemApp,
            iconBitmap = iconBitmap,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = appTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = child.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = child.memory,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )

        Box(modifier = Modifier.size(72.dp))
    }
}

private data class ManagementProgressDialogState(
    val title: String,
    val message: String,
)

private data class ManagementResultDialogState(
    val title: String,
    val message: String,
    val isSuccess: Boolean,
)

private data class ManagementValueInputDialogState(
    val title: String,
    val label: String,
    val initialValue: String,
    val confirmText: String,
)

private data class ResolutionDialogState(
    val width: String,
    val height: String,
)

private data class AnimationScaleDialogState(
    val windowScale: String,
    val transitionScale: String,
    val durationScale: String,
)

private data class ScreenshotPreviewState(
    val file: File,
)

private data class ActivationTarget(
    val label: String,
    val packageName: String,
    val command: String,
)

private val SessionManagementAppDetailLabelWidth = 92.dp
private val SessionManagementAppDetailRowMinHeight = 38.dp
private val SessionManagementAppDetailDividerInset = 110.dp

private data class AppDetailItem(
    val label: String,
    val value: String,
    val useSmallText: Boolean = false,
)

private enum class StandbyAction(
    val label: String,
    val command: String,
) {
    Sleep("息屏", "input keyevent 223"),
    Wake("亮屏", "input keyevent 224"),
}

private enum class RebootMode(
    val label: String,
    val command: String,
) {
    Normal("正常重启", "reboot"),
    Recovery("Recovery", "reboot recovery"),
    Fastboot("FastBoot", "reboot bootloader"),
}

@Composable
internal fun SessionManagementProgressDialog(
    title: String,
    message: String,
) {
    AlertDialog(
        onDismissRequest = {},
        showActionBar = false,
        widthRatio = 0.72f,
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        title = { Text(title) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SessionManagementProgressSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SessionManagementProgressIndicatorSize),
                    strokeWidth = 2.dp,
                )
                Text(message)
            }
        },
        confirmButton = {},
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SessionManagementExitConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认退出管理页？") },
        text = { Text("返回键会离开当前设备管理页面。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("退出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SessionManagementRebootDialog(
    onDismiss: () -> Unit,
    onAction: (RebootMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("高级重启") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RebootMode.entries.forEach { mode ->
                    Surface(
                        shape = RoundedCornerShape(SessionManagementOptionCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(SessionManagementOptionCornerRadius))
                                    .clickable { onAction(mode) }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SessionManagementValueInputDialog(
    state: ManagementValueInputDialogState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(state) { mutableStateOf(state.initialValue) }
    val isValid = value.trim().toFloatOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(state.label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = isValid,
            ) {
                Text(state.confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SessionManagementResolutionDialog(
    state: ResolutionDialogState,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var width by remember(state) { mutableStateOf(state.width) }
    var height by remember(state) { mutableStateOf(state.height) }
    val isValid = width.trim().toIntOrNull() != null && height.trim().toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改分辨率") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = width,
                    onValueChange = { width = it },
                    label = { Text("宽度") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text("高度") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(width.trim(), height.trim()) },
                enabled = isValid,
            ) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SessionManagementScreenshotDialog(
    state: ScreenshotPreviewState,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("截图已完成") },
        text = {
            Text("文件已保存到本机缓存目录：${state.file.absolutePath}")
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                TextButton(onClick = onOpen) {
                    Text("打开")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SessionManagementAnimationDialog(
    state: AnimationScaleDialogState,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var windowScale by remember(state) { mutableStateOf(state.windowScale) }
    var transitionScale by remember(state) { mutableStateOf(state.transitionScale) }
    var durationScale by remember(state) { mutableStateOf(state.durationScale) }
    val isValid =
        windowScale.trim().toFloatOrNull() != null &&
            transitionScale.trim().toFloatOrNull() != null &&
            durationScale.trim().toFloatOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("动画调整") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = windowScale,
                    onValueChange = { windowScale = it },
                    label = { Text("窗口动画缩放倍数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = transitionScale,
                    onValueChange = { transitionScale = it },
                    label = { Text("过渡动画缩放倍数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = durationScale,
                    onValueChange = { durationScale = it },
                    label = { Text("动画时长缩放倍数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(windowScale.trim(), transitionScale.trim(), durationScale.trim()) },
                enabled = isValid,
            ) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SessionManagementStandbyDialog(
    onDismiss: () -> Unit,
    onAction: (StandbyAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("熄屏待机") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StandbyAction.entries.forEach { action ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onAction(action) }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SessionManagementActivationDialog(
    refreshToken: Int,
    onDismiss: () -> Unit,
    onAction: (ActivationTarget) -> Unit,
    onUnavailable: (String) -> Unit,
) {
    val context = LocalContext.current
    val appInventory by produceState(
        initialValue = AppInventorySnapshot.loading(),
        key1 = refreshToken,
    ) {
        value = loadAppInventorySnapshot(context, includeSystemApps = false)
    }
    val targets = supportedActivationTargets(appInventory.packages.toSet())
    val inventoryByPackage =
        remember(appInventory.apps) {
            appInventory.apps.associateBy { it.packageName }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("激活应用") },
        text = {
            when {
                appInventory.isLoading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("正在加载可激活应用列表。")
                    }
                }

                appInventory.errorMessage != null -> {
                    Text(appInventory.errorMessage ?: "读取应用列表失败。")
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        targets.forEach { target ->
                            SessionManagementActivationTargetRow(
                                target = target,
                                inventoryEntry = inventoryByPackage[target.packageName],
                                onAction = onAction,
                                onUnavailable = onUnavailable,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SessionManagementActivationTargetRow(
    target: ActivationTarget,
    inventoryEntry: AppInventoryEntry?,
    onAction: (ActivationTarget) -> Unit,
    onUnavailable: (String) -> Unit,
) {
    val context = LocalContext.current
    val installed = target.command.isNotBlank() && inventoryEntry != null
    val presentation by produceState(
        initialValue =
            RemoteAppPresentation(
                title =
                    inventoryEntry?.let { SessionManagementAppCache.appTitle(it.packageName, it.appTitle) }
                        ?: target.label,
                icon = inventoryEntry?.let { SessionManagementAppCache.cachedIcon(it.packageName) },
            ),
        key1 = inventoryEntry?.packageName,
        key2 = inventoryEntry?.apkPath,
    ) {
        if (inventoryEntry != null) {
            value = loadCachedAppPresentation(context, inventoryEntry, packageNameOnlyMode = false)
        }
    }
    val title = if (installed) presentation.title else target.label

    Surface(
        shape = RoundedCornerShape(14.dp),
        color =
            if (installed) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (installed) {
                            onAction(target)
                        } else {
                            onUnavailable(target.label)
                        }
                    }.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SessionManagementAppAvatar(
                    packageName = target.packageName,
                    appTitle = title,
                    isSystemApp = inventoryEntry?.isSystemApp == true,
                    iconBitmap = if (installed) presentation.icon else null,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = target.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SessionManagementUtilityBadge(
                text = if (installed) "已安装" else "未安装",
                accent = if (installed) Color(0xFF17C3E6) else Color.Unspecified,
                available = installed,
            )
        }
    }
}

internal suspend fun runShellAction(
    command: String,
    successMessage: String,
): Result<String> {
    val connection =
        AdbBridge.getConnection()
            ?: return Result.failure(IllegalStateException("当前没有可用的 ADB 连接。"))

    return AdbShellManager
        .execute(connection = connection, command = command, retryOnFailure = false)
        .map { output ->
            output.trim().ifBlank { successMessage }
        }
}

private suspend fun captureDeviceScreenshot(context: Context): Result<File> {
    val connection =
        AdbBridge.getConnection()
            ?: return Result.failure(IllegalStateException("当前没有可用的 ADB 连接。"))

    return withContext(Dispatchers.IO) {
        runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val localDir = File(context.cacheDir, "session-management/screenshots").apply { mkdirs() }
            val localFile = File(localDir, "device_$timestamp.png")
            val remotePath = "/data/local/tmp/scrcpy_mobile_screenshot_$timestamp.png"

            connection.executeShell("rm -f $remotePath", retryOnFailure = false)
            connection.executeShell("screencap -p $remotePath", retryOnFailure = false).getOrThrow()
            connection.pullFile(remotePath, localFile.absolutePath).getOrThrow()
            connection.executeShell("rm -f $remotePath", retryOnFailure = false)

            if (localFile.length() <= 0L) {
                error("截图文件为空。")
            }
            localFile
        }
    }
}

private fun openImagePreview(
    context: Context,
    file: File,
) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/png")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "无法打开截图预览", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun saveImageToGallery(
    context: Context,
    file: File,
): Result<String> =
    withContext(Dispatchers.IO) {
        runCatching {
            val fileName = file.nameWithoutExtension.ifBlank { "screenshot_${System.currentTimeMillis()}" } + ".png"
            val resolver = context.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScrcpyMobile")
                    }
                }

            val uri =
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法创建相册文件")

            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("无法写入相册文件")

            "截图已保存到相册。"
        }
    }

internal suspend fun prepareRemoteFileForLocalOpen(
    context: Context,
    entry: RemoteFileEntry,
): Result<File> {
    val connection =
        AdbBridge.getConnection()
            ?: return Result.failure(IllegalStateException("当前没有可用的 ADB 连接。"))

    return withContext(Dispatchers.IO) {
        runCatching {
            val localFile = getPreparedLocalFile(context, entry)
            connection.pullFile(entry.fullPath, localFile.absolutePath).getOrThrow()
            localFile
        }
    }
}

internal suspend fun loadRemoteTextEditorState(
    context: Context,
    entry: RemoteFileEntry,
): Result<RemoteTextEditorState> =
    prepareRemoteFileForLocalOpen(context, entry).mapCatching { localFile ->
        if (!isEditableTextFile(entry.name) && !isLikelyTextContent(localFile)) {
            error("当前文件不像文本文件，请改用专门预览或外部打开。")
        }
        if (localFile.length() > 512 * 1024L) {
            error("简易编辑器暂只支持 512 KB 以内文本文件。")
        }
        val content = localFile.readText(Charsets.UTF_8)
        RemoteTextEditorState(
            entry = entry,
            localFile = localFile,
            content = content,
        )
    }

internal suspend fun saveRemoteTextFile(
    state: RemoteTextEditorState,
    content: String,
): Result<String> {
    val connection =
        AdbBridge.getConnection()
            ?: return Result.failure(IllegalStateException("当前没有可用的 ADB 连接。"))

    return withContext(Dispatchers.IO) {
        runCatching {
            state.localFile.writeText(content, Charsets.UTF_8)
            connection.pushFile(state.localFile.absolutePath, state.entry.fullPath).getOrThrow()
            "文件已保存并回写到设备。"
        }
    }
}

internal fun openLocalFileExternal(
    context: Context,
    file: File,
): Result<String> =
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = resolveMimeType(file.name)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(Intent.createChooser(intent, "打开文件"))
        "已调用外部程序打开本机临时文件。"
    }

internal suspend fun pushPreparedLocalFileToDevice(
    context: Context,
    entry: RemoteFileEntry,
): Result<String> {
    val connection =
        AdbBridge.getConnection()
            ?: return Result.failure(IllegalStateException("当前没有可用的 ADB 连接。"))

    return withContext(Dispatchers.IO) {
        runCatching {
            val localFile = getPreparedLocalFile(context, entry)
            require(localFile.exists()) { "当前没有可回写的本机副本，请先打开或预览该文件。" }
            connection.pushFile(localFile.absolutePath, entry.fullPath).getOrThrow()
            "本机副本已回写到设备。"
        }
    }
}

internal fun getPreparedLocalFile(
    context: Context,
    entry: RemoteFileEntry,
): File {
    val tempDir = File(context.cacheDir, "session-management/files").apply { mkdirs() }
    return File(
        tempDir,
        "${sha256(entry.fullPath.toByteArray()).take(12)}_${entry.name}",
    )
}

internal fun readBinaryPreview(
    file: File,
    maxBytes: Int = 512,
): String {
    val bytes =
        runCatching { file.inputStream().use { input -> readAtMostBytesCompat(input, maxBytes) } }.getOrNull()
            ?: return "无法读取二进制预览。"
    if (bytes.isEmpty()) return "空文件"

    return bytes
        .toList()
        .chunked(16)
        .mapIndexed { index, chunk ->
            val address = (index * 16).toString(16).padStart(4, '0')
            val hex = chunk.joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
            "$address  $hex"
        }.joinToString(separator = "\n")
}

private suspend fun loadRemoteFileDetailSnapshot(entry: RemoteFileEntry): RemoteFileDetailSnapshot {
    val connection =
        AdbBridge.getConnection()
            ?: return RemoteFileDetailSnapshot.loading(entry).copy(
                isLoading = false,
                errorMessage = "当前没有可用的 ADB 连接，无法读取文件详情。",
            )

    suspend fun shell(command: String): String =
        connection
            .executeShell(command, retryOnFailure = false)
            .getOrNull()
            ?.trim()
            .orEmpty()

    return runCatching {
        val statOutput =
            shell(
                "stat -c '%A|%U|%G|%s|%y' ${quoteShellArg(entry.fullPath)} 2>/dev/null",
            )

        if (statOutput.contains("|")) {
            val parts = statOutput.split("|", limit = 5)
            val sizeBytes = parts.getOrNull(3)?.trim()?.toLongOrNull()
            val modified =
                parts
                    .getOrNull(4)
                    ?.substringBefore(".")
                    ?.trim()
                    .orEmpty()

            RemoteFileDetailSnapshot(
                isLoading = false,
                name = entry.name,
                fullPath = entry.fullPath,
                typeLabel = if (entry.isDirectory) "文件夹" else "文件",
                permissions = parts.getOrNull(0)?.trim().orEmpty(),
                owner = parts.getOrNull(1)?.trim().orEmpty(),
                group = parts.getOrNull(2)?.trim().orEmpty(),
                sizeLabel = sizeBytes?.let(::formatFileSize) ?: "--",
                modifiedTime = modified,
            )
        } else {
            val lsOutput = shell("ls -ld ${quoteShellArg(entry.fullPath)}")
            val parsed = parseLsDetailLine(lsOutput, entry)
            parsed ?: error("无法解析文件详情")
        }
    }.getOrElse { error ->
        RemoteFileDetailSnapshot.loading(entry).copy(
            isLoading = false,
            errorMessage = error.message ?: "读取文件详情失败。",
        )
    }
}

private fun parseLsDetailLine(
    line: String,
    entry: RemoteFileEntry,
): RemoteFileDetailSnapshot? {
    val tokens = line.trim().split(Regex("\\s+"))
    if (tokens.size < 7) return null

    val permissions = tokens.getOrNull(0).orEmpty()
    val owner = tokens.getOrNull(2).orEmpty()
    val group = tokens.getOrNull(3).orEmpty()
    val sizeBytes = tokens.getOrNull(4)?.toLongOrNull()
    val modified =
        when {
            tokens.size >= 7 -> "${tokens[5]} ${tokens[6]}"
            else -> "--"
        }

    return RemoteFileDetailSnapshot(
        isLoading = false,
        name = entry.name,
        fullPath = entry.fullPath,
        typeLabel = if (entry.isDirectory) "文件夹" else "文件",
        permissions = permissions,
        owner = owner,
        group = group,
        sizeLabel = sizeBytes?.let(::formatFileSize) ?: "--",
        modifiedTime = modified,
    )
}

private fun formatFileModifiedTime(value: String): String =
    when {
        value.length >= 16 -> value.take(16)
        else -> value
    }

private fun resolveMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
    if (extension.isBlank()) return "*/*"

    return when (extension) {
        "txt", "log", "md", "json", "xml", "html", "htm", "css", "js", "kt", "java", "py", "sh", "yaml", "yml",
        "ini", "conf", "properties",
        -> "text/plain"

        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }
}

private fun isEditableTextFile(fileName: String): Boolean {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return extension in
        setOf(
            "txt",
            "log",
            "md",
            "markdown",
            "json",
            "json5",
            "xml",
            "html",
            "htm",
            "css",
            "js",
            "ts",
            "tsx",
            "jsx",
            "kt",
            "kts",
            "java",
            "groovy",
            "gradle",
            "py",
            "sh",
            "bash",
            "zsh",
            "yaml",
            "yml",
            "ini",
            "conf",
            "config",
            "properties",
            "prop",
            "toml",
            "csv",
            "tsv",
            "sql",
            "c",
            "h",
            "cpp",
            "hpp",
            "rs",
            "go",
            "php",
            "rb",
            "swift",
            "dart",
            "lua",
            "smali",
        )
}

internal enum class RemoteFileKind {
    Text,
    Image,
    Video,
    Audio,
    Binary,
}

internal fun classifyRemoteFileKind(fileName: String): RemoteFileKind {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return when (extension) {
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic" -> RemoteFileKind.Image
        "mp4", "mkv", "webm", "mov", "3gp", "avi" -> RemoteFileKind.Video
        "mp3", "wav", "ogg", "m4a", "flac", "aac" -> RemoteFileKind.Audio
        else -> if (isEditableTextFile(fileName)) RemoteFileKind.Text else RemoteFileKind.Binary
    }
}

private fun isLikelyTextContent(file: File): Boolean {
    val bytes =
        runCatching { file.inputStream().use { input -> readAtMostBytesCompat(input, 2048) } }.getOrNull()
            ?: return false
    if (bytes.isEmpty()) return true

    val controlCount =
        bytes.count { byte ->
            val value = byte.toInt() and 0xFF
            value == 0 || (value < 0x09) || (value in 0x0E..0x1F)
        }
    return controlCount <= (bytes.size / 20).coerceAtLeast(1)
}

@Composable
internal fun SessionManagementFileRow(
    entry: RemoteFileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                    } else {
                        Color.Transparent
                    },
                ).combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress,
                ).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color =
                if (entry.isDirectory) {
                    Color(0xFFFFD56A).copy(alpha = 0.18f)
                } else {
                    Color(0xFF8EC5FF).copy(alpha = 0.18f)
                },
        ) {
            Icon(
                imageVector =
                    if (entry.isDirectory) {
                        Icons.Default.Folder
                    } else {
                        Icons.AutoMirrored.Filled.InsertDriveFile
                    },
                contentDescription = null,
                tint = if (entry.isDirectory) Color(0xFFFFC24D) else Color(0xFF5A9EFF),
                modifier =
                    Modifier
                        .padding(10.dp)
                        .size(22.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (entry.isDirectory) "文件夹" else "文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Default.Check else Icons.Default.Add,
                contentDescription = if (selected) "已选中" else "未选中",
                tint = if (selected) Color(0xFFFF6E95) else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun SessionManagementFileListSkeleton() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        repeat(6) { index ->
            SessionManagementFilePlaceholderRow(isDirectory = index % 3 != 1)
            if (index != 5) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 64.dp, end = 16.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            ).height(1.dp),
                )
            }
        }
    }
}

@Composable
private fun SessionManagementFilePlaceholderRow(isDirectory: Boolean) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color =
                if (isDirectory) {
                    Color(0xFFFFD56A).copy(alpha = 0.16f)
                } else {
                    Color(0xFF8EC5FF).copy(alpha = 0.16f)
                },
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(10.dp)
                        .size(22.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.56f)
                            .height(18.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(72.dp)
                            .height(14.dp),
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(44.dp)
                        .height(14.dp),
            )
        }
    }
}

@Composable
internal fun SessionManagementBottomIconAction(
    icon: ImageVector,
    label: String,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(12.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionManagementSelectionDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { option ->
                    SessionManagementActionRow(
                        icon = if (option == selectedOption) Icons.Default.Check else Icons.Default.FilterList,
                        label = option,
                        onClick = { onSelect(option) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
internal fun SessionManagementTextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue) }
    val trimmed = value.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotBlank(),
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
internal fun SessionManagementFileAddDialog(
    onDismiss: () -> Unit,
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit,
    onUpload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SessionManagementActionRow(
                    icon = Icons.Default.Folder,
                    label = "新建文件夹",
                    onClick = onCreateFolder,
                )
                SessionManagementActionRow(
                    icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                    label = "新建文件",
                    onClick = onCreateFile,
                )
                SessionManagementActionRow(
                    icon = Icons.Default.Download,
                    label = "从本地上传",
                    onClick = onUpload,
                )
                SessionManagementActionRow(
                    icon = Icons.Default.ContentCopy,
                    label = "粘贴",
                    enabled = false,
                    onClick = {},
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
internal fun SessionManagementAppAddDialog(
    onDismiss: () -> Unit,
    onPickApk: () -> Unit,
    onPickInstalledApp: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安装应用") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SessionManagementActionRow(
                    icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                    label = "选择apk文件",
                    onClick = onPickApk,
                )
                SessionManagementActionRow(
                    icon = Icons.Default.Apps,
                    label = "选择已安装应用",
                    onClick = onPickInstalledApp,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
internal fun SessionManagementFileDeleteDialog(
    targets: List<RemoteFileEntry>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除 ${targets.size} 项？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("删除后不可恢复，请确认继续。")
                Text(
                    text = targets.joinToString(separator = "\n") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
internal fun SessionManagementFileDetailDialog(
    entry: RemoteFileEntry,
    onDismiss: () -> Unit,
) {
    val detail by produceState(
        initialValue = RemoteFileDetailSnapshot.loading(entry),
        key1 = entry.fullPath,
    ) {
        value = loadRemoteFileDetailSnapshot(entry)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SessionManagementFileDialogSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector =
                        if (entry.isDirectory) {
                            Icons.Default.Folder
                        } else {
                            Icons.AutoMirrored.Filled.InsertDriveFile
                        },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SessionManagementFileDialogHeaderIconSize),
                )
                Text(detail.name)
            }
        },
        text = {
            when {
                detail.isLoading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SessionManagementFileDialogSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(SessionManagementFileDialogLoadingIndicatorSize),
                            strokeWidth = 2.dp,
                        )
                        Text("正在读取文件详情")
                    }
                }

                detail.errorMessage != null -> {
                    detail.errorMessage?.let { Text(it) }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(SessionManagementFileDialogInfoSpacing)) {
                        SessionManagementInfoRow(label = "所在路径", value = detail.fullPath)
                        SessionManagementInfoRow(label = "类型", value = detail.typeLabel)
                        SessionManagementInfoRow(label = "权限", value = detail.permissions)
                        SessionManagementInfoRow(label = "所有者", value = detail.owner)
                        SessionManagementInfoRow(label = "用户组", value = detail.group)
                        SessionManagementInfoRow(label = "大小", value = detail.sizeLabel)
                        SessionManagementInfoRow(label = "最后修改时间", value = detail.modifiedTime)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
internal fun SessionManagementFileActionDialog(
    entry: RemoteFileEntry,
    fileKind: RemoteFileKind,
    canEdit: Boolean,
    canPushBack: Boolean,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
    onEdit: () -> Unit,
    onDetails: () -> Unit,
) {
    val previewLabel =
        when (fileKind) {
            RemoteFileKind.Image -> "图片预览"
            RemoteFileKind.Video -> "视频预览"
            RemoteFileKind.Audio -> "音频预览"
            RemoteFileKind.Binary -> "二进制预览"
            RemoteFileKind.Text -> null
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            SessionManagementFileActionCard {
                previewLabel?.let {
                    SessionManagementFileMenuRow(
                        icon = Icons.Default.Info,
                        label = it,
                        onClick = onPreview,
                    )
                }
                SessionManagementFileMenuRow(
                    icon = Icons.Default.PlayArrow,
                    label = "外部打开",
                    onClick = onOpenExternal,
                )
                SessionManagementFileMenuRow(
                    icon = Icons.Default.Upload,
                    label = "回写设备",
                    enabled = canPushBack,
                    onClick = onPushBack,
                )
                SessionManagementFileMenuRow(
                    icon = Icons.Default.Edit,
                    label = "内置编辑器",
                    enabled = canEdit,
                    onClick = onEdit,
                )
                SessionManagementFileMenuRow(
                    icon = Icons.Default.Info,
                    label = "详情",
                    onClick = onDetails,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
internal fun SessionManagementTextEditorDialog(
    state: RemoteTextEditorState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onOpenExternal: () -> Unit,
) {
    var content by remember(state.entry.fullPath, state.localFile.absolutePath) { mutableStateOf(state.content) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = SessionManagementTextEditorHorizontalPadding,
                            vertical = SessionManagementTextEditorVerticalPadding,
                        ),
                verticalArrangement = Arrangement.spacedBy(SessionManagementFileDialogSpacing),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "编辑 ${state.entry.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(SessionManagementTextEditorTopActionSpacing)) {
                        TextButton(onClick = onOpenExternal) {
                            Text("外部打开")
                        }
                        TextButton(onClick = { onSave(content) }) {
                            Text("保存")
                        }
                        TextButton(onClick = onDismiss) {
                            Text("关闭")
                        }
                    }
                }

                Text(
                    text = state.entry.fullPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    label = { Text("文件内容") },
                    singleLine = false,
                    maxLines = Int.MAX_VALUE,
                )
            }
        }
    }
}

@Composable
private fun SessionManagementFileActionCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(SessionManagementCardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SessionManagementFileActionCardPadding,
                        vertical = SessionManagementFileActionCardPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(SessionManagementFileActionCardSpacing),
        ) {
            content()
        }
    }
}

@Composable
private fun SessionManagementFilePreviewDialog(
    title: String,
    path: String,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SessionManagementFileDialogSpacing)) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                content()
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(SessionManagementFileDialogButtonSpacing)) {
                TextButton(onClick = onOpenExternal) { Text("外部打开") }
                TextButton(onClick = onPushBack) { Text("回写设备") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SessionManagementFileMenuRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(SessionManagementFileMenuRowCornerRadius),
        color =
            if (enabled) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
            },
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SessionManagementFileMenuRowCornerRadius))
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(
                        horizontal = SessionManagementFileMenuRowHorizontalPadding,
                        vertical = SessionManagementFileMenuRowVerticalPadding,
                    ),
            horizontalArrangement = Arrangement.spacedBy(SessionManagementFileDialogSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                modifier = Modifier.size(SessionManagementFileMenuRowIconSize),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
            )
        }
    }
}

@Composable
internal fun SessionManagementImagePreviewDialog(
    state: RemotePreparedFileState,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
) {
    val bitmap =
        remember(
            state.localFile.absolutePath,
        ) { android.graphics.BitmapFactory.decodeFile(state.localFile.absolutePath) }

    SessionManagementFilePreviewDialog(
        title = "图片预览",
        path = state.entry.fullPath,
        onDismiss = onDismiss,
        onOpenExternal = onOpenExternal,
        onPushBack = onPushBack,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = state.entry.name,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(SessionManagementImagePreviewHeight),
            )
        } ?: Text("无法解码图片。")
    }
}

@Composable
internal fun SessionManagementVideoPreviewDialog(
    state: RemotePreparedFileState,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
) {
    SessionManagementFilePreviewDialog(
        title = "视频预览",
        path = state.entry.fullPath,
        onDismiss = onDismiss,
        onOpenExternal = onOpenExternal,
        onPushBack = onPushBack,
    ) {
        AndroidView(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(SessionManagementVideoPreviewHeight),
            factory = { context ->
                VideoView(context).apply {
                    setVideoPath(state.localFile.absolutePath)
                    setMediaController(MediaController(context).also { it.setAnchorView(this) })
                    setOnPreparedListener { start() }
                }
            },
        )
    }
}

@Composable
internal fun SessionManagementAudioPreviewDialog(
    state: RemotePreparedFileState,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
) {
    val mediaPlayer =
        remember(state.localFile.absolutePath) {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(state.localFile.absolutePath)
                    prepare()
                }
            }.getOrNull()
        }
    var isPlaying by remember(state.localFile.absolutePath) { mutableStateOf(false) }

    DisposableEffect(mediaPlayer) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    SessionManagementFilePreviewDialog(
        title = "音频预览",
        path = state.entry.fullPath,
        onDismiss = onDismiss,
        onOpenExternal = onOpenExternal,
        onPushBack = onPushBack,
    ) {
        Text(
            text = "本机副本：${state.localFile.name}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            it.pause()
                            isPlaying = false
                        } else {
                            it.start()
                            isPlaying = true
                        }
                    }
                },
            ) {
                Text(if (isPlaying) "暂停" else "播放")
            }
            TextButton(
                onClick = {
                    mediaPlayer?.pause()
                    mediaPlayer?.seekTo(0)
                    isPlaying = false
                },
            ) {
                Text("停止")
            }
        }
    }
}

@Composable
internal fun SessionManagementBinaryPreviewDialog(
    state: RemoteBinaryPreviewState,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
) {
    SessionManagementFilePreviewDialog(
        title = "二进制预览",
        path = state.entry.fullPath,
        onDismiss = onDismiss,
        onOpenExternal = onOpenExternal,
        onPushBack = onPushBack,
    ) {
        OutlinedTextField(
            value = state.preview,
            onValueChange = {},
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(SessionManagementBinaryPreviewHeight),
            readOnly = true,
            singleLine = false,
            maxLines = Int.MAX_VALUE,
            label = { Text("Hex 预览") },
        )
    }
}

@Composable
internal fun SessionManagementOverwriteConfirmDialog(
    state: RemoteOverwriteConfirmState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.title) },
        text = { Text(state.message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("覆盖")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SessionManagementDrawerItem(
    section: SessionManagementSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = section.title,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun SessionManagementDrawerActionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SessionManagementHomeSnapshot(snapshot: DeviceDashboardSnapshot) {
    if (snapshot.errorMessage != null) {
        SessionManagementNoteCard(
            title = "设备信息读取失败",
            text = snapshot.errorMessage,
        )
        return
    }

    if (snapshot.isLoading) {
        SessionManagementHomeLoadingSkeleton()
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SessionManagementInfoGroup(
            title = "系统与硬件",
            items =
                listOf(
                    "品牌型号" to snapshot.brandModelLabel,
                    "SOC" to snapshot.socModel,
                    "安卓版本" to snapshot.androidVersionLabel,
                    "开机时长" to snapshot.uptime,
                    "基带版本" to snapshot.basebandVersion,
                    "产品代号" to snapshot.productCodeName,
                    "安全补丁" to snapshot.securityPatch,
                    "序列号" to snapshot.serialNumber,
                    "处理器" to snapshot.cpuSummary,
                    "ABI" to snapshot.abi,
                    "主板" to snapshot.board,
                ),
        )

        SessionManagementInfoGroup(
            title = "显示与能耗",
            items =
                listOf(
                    "显示" to formatDisplaySummary(snapshot.resolution, snapshot.refreshRate),
                    "屏幕" to formatScreenMetricsSummary(snapshot.dpi, snapshot.ppi, snapshot.screenSize),
                    "刷新率列表" to snapshot.supportedRefreshRates,
                    "电池" to formatBatterySummary(snapshot.batteryHealth, snapshot.voltage, snapshot.currentNow),
                    "状态" to
                        formatBatteryStatusSummary(snapshot.batteryStatus, snapshot.batteryLevel, snapshot.temperature),
                    "循环次数" to snapshot.batteryCycleCount,
                ),
        )

        SessionManagementInfoGroup(
            title = "存储与内存",
            items =
                listOf(
                    "存储空间" to snapshot.storageSummary,
                    "运行内存" to formatMemorySummary(snapshot.memoryAvailable, snapshot.memoryTotal),
                ),
        )

        SessionManagementInfoGroup(
            title = "网络",
            items =
                listOf(
                    "蜂窝网络" to formatMobileBandSummary(snapshot.mobileNetworkType, snapshot.mobileBand),
                    "运营商" to snapshot.carrierNames,
                    "PCI" to snapshot.mobilePci,
                    "EARFCN" to snapshot.mobileEarfcn,
                    "RSRP" to snapshot.rsrp,
                    "RSRQ" to snapshot.rsrq,
                    "SINR" to snapshot.sinr,
                    "无线SSID" to snapshot.wifiSsid,
                    "WLAN IP" to snapshot.wifiIpAddress,
                    "Wi‑Fi 信息" to formatWifiSummary(snapshot.wifiFrequency, snapshot.wifiLinkSpeed),
                    "BSSID" to snapshot.wifiBssid,
                ),
        )

        if (snapshot.fingerprint.isNotBlank()) {
            SessionManagementInfoGroup(
                title = "系统标识",
                items = listOf("Fingerprint" to snapshot.fingerprint),
            )
        }
    }
}

@Composable
private fun SessionManagementHomeLoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SessionManagementInfoLoadingGroup(
            title = "系统与硬件",
            labels =
                listOf(
                    "品牌型号",
                    "SOC",
                    "安卓版本",
                    "开机时长",
                    "基带版本",
                    "产品代号",
                    "安全补丁",
                    "序列号",
                    "处理器",
                    "ABI",
                    "主板",
                ),
        )
        SessionManagementInfoLoadingGroup(
            title = "显示与能耗",
            labels = listOf("显示", "屏幕", "刷新率列表", "电池", "状态", "循环次数"),
        )
        SessionManagementInfoLoadingGroup(
            title = "存储与内存",
            labels = listOf("存储空间", "运行内存"),
        )
        SessionManagementInfoLoadingGroup(
            title = "网络",
            labels =
                listOf(
                    "蜂窝网络",
                    "运营商",
                    "PCI",
                    "EARFCN",
                    "RSRP",
                    "RSRQ",
                    "SINR",
                    "无线SSID",
                    "WLAN IP",
                    "Wi-Fi 信息",
                    "BSSID",
                ),
        )
    }
}

@Composable
private fun SessionManagementInfoLoadingGroup(
    title: String,
    labels: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(text = title)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                labels.forEachIndexed { index, label ->
                    SessionManagementInfoPlaceholderRow(label = label)
                    if (index != labels.lastIndex) {
                        AppDivider(modifier = Modifier.padding(start = 104.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionManagementInfoPlaceholderRow(
    label: String,
    labelWidth: Dp = 88.dp,
    rowMinHeight: Dp = 18.dp,
) {
    SessionManagementInfoRowLayout(
        label = label,
        labelWidth = labelWidth,
        rowHeight = rowMinHeight,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp),
            )
        }
    }
}

@Composable
private fun SessionManagementInfoGroup(
    title: String,
    items: List<Pair<String, String>>,
) {
    val visibleItems = items.filter { it.second.isNotBlank() }
    if (visibleItems.isEmpty()) {
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(text = title)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                visibleItems.forEachIndexed { index, (label, value) ->
                    SessionManagementInfoRow(
                        label = label,
                        value = value,
                        valueMaxLines = 1,
                        valueColor = MaterialTheme.colorScheme.onSurface,
                    )
                    if (index != visibleItems.lastIndex) {
                        AppDivider(modifier = Modifier.padding(start = 104.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionManagementInfoRow(
    label: String,
    value: String,
    labelWidth: Dp = 88.dp,
    rowMinHeight: Dp = 0.dp,
    valueTextStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    valueMaxLines: Int = 2,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    SessionManagementInfoRowLayout(
        label = label,
        labelWidth = labelWidth,
        rowHeight = rowMinHeight,
    ) {
        Text(
            text = value,
            style = valueTextStyle,
            color = valueColor,
            modifier = Modifier.weight(1f),
            maxLines = valueMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SessionManagementInfoRowLayout(
    label: String,
    labelWidth: Dp,
    rowHeight: Dp,
    valueContent: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (rowHeight > 0.dp) {
                        Modifier.height(rowHeight)
                    } else {
                        Modifier
                    },
                ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(labelWidth),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        valueContent()
    }
}

@Composable
private fun SessionManagementTag(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun SessionManagementNoteCard(
    title: String,
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionManagementSectionCard(section: SessionManagementSection) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier =
                            Modifier
                                .padding(12.dp)
                                .size(20.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = section.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "核心能力",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                section.capabilities.forEach { capability ->
                    SessionManagementBullet(text = capability)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "典型入口",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    section.entryPoints.forEach { entry ->
                        Box(modifier = Modifier.wrapContentWidth()) {
                            SessionManagementTag(text = entry)
                        }
                    }
                }
            }

            section.note?.let { note ->
                SessionManagementNoteCard(
                    title = "补充说明",
                    text = note,
                )
            }
        }
    }
}

@Composable
private fun sessionManagementDialogCardColor(): Color =
    if (
        MaterialTheme.colorScheme.surface.let { color ->
            0.299f * color.red + 0.587f * color.green + 0.114f * color.blue < 0.5f
        }
    ) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    } else {
        Color(0xFFF6F6F8)
    }

@Composable
private fun SessionManagementDialogCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(SessionManagementCardCornerRadius),
        color = sessionManagementDialogCardColor(),
    ) {
        content()
    }
}

@Composable
private fun SessionManagementBullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 7.dp)
                    .size(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(999.dp),
                    ),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private enum class SessionManagementSection(
    val title: String,
    val icon: ImageVector,
    val summary: String,
    val capabilities: List<String>,
    val entryPoints: List<String>,
    val note: String? = null,
    val supportsRefresh: Boolean = false,
) {
    DeviceInfo(
        title = "设备信息",
        icon = Icons.Default.Info,
        summary = "集中展示硬件、系统和运行状态，作为所有管理操作的上下文基础。",
        capabilities =
            listOf(
                "读取型号、品牌、Android 版本和 SDK。",
                "展示 CPU、内存、存储等硬件指标。",
                "展示分辨率、DPI、电池状态和网络信息。",
                "可扩展为刷新和快照缓存机制。",
            ),
        entryPoints = listOf("系统参数", "硬件信息", "屏幕状态", "网络电池"),
        supportsRefresh = true,
    ),
    Utility(
        title = "实用工具",
        icon = Icons.Default.Build,
        summary = "提供高频 ADB 工具集合，优先承接截图里的“实用工具”入口。",
        capabilities =
            listOf(
                "支持无线调试、配对码、刷新设备信息等快捷动作。",
                "支持修改 DPI、分辨率、旋转等常用显示调整。",
                "支持重启到系统、Recovery、Fastboot 等设备工具。",
                "适合作为常用一键工具集合的落点。",
            ),
        entryPoints = listOf("无线调试", "显示调整", "重启工具", "快捷动作"),
        supportsRefresh = true,
    ),
    Files(
        title = "文件管理",
        icon = Icons.Default.Folder,
        summary = "承接目录浏览和双向文件传输，形成独立于投屏的设备管理能力。",
        capabilities =
            listOf(
                "提供可视化文件浏览器。",
                "支持复制、粘贴、删除和重命名。",
                "支持电脑到手机、手机到电脑双向传输。",
                "支持文件夹和批量操作。",
            ),
        entryPoints = listOf("浏览器", "上传", "下载", "批量操作"),
        supportsRefresh = true,
    ),
    Apps(
        title = "应用管理",
        icon = Icons.Default.Apps,
        summary = "收拢 APK 安装、卸载、启停、冻结与高权限工具激活能力。",
        capabilities =
            listOf(
                "支持批量安装和卸载 APK。",
                "支持启用、停用、冻结系统和用户应用。",
                "支持清除应用数据、缓存和导出 APK。",
                "支持一键激活 Shizuku、黑域、冰箱、安装狮等工具。",
            ),
        entryPoints = listOf("安装卸载", "启停冻结", "清理导出", "权限工具"),
        supportsRefresh = true,
    ),
    Process(
        title = "进程管理",
        icon = Icons.Default.Usb,
        summary = "按应用聚合展示设备上正在运行的 app 进程，例如电话服务、通话设置、联系人等。",
        capabilities =
            listOf(
                "展示设备内存、已用内存和可用内存。",
                "按应用汇总主进程和 :service 子进程内存。",
                "支持展开查看同一应用下的多个运行进程。",
                "支持结束指定应用的运行进程。",
            ),
        entryPoints = listOf("运行进程", "内存占用", "子进程", "结束应用"),
        supportsRefresh = true,
    ),
    Command(
        title = "运行命令",
        icon = Icons.Default.Code,
        summary = "承接自定义 Shell、预置命令和 Logcat 实时调试入口。",
        capabilities =
            listOf(
                "内置命令控制台，回显执行结果。",
                "支持常用 Shell 命令一键执行，如查看系统信息、修改 DPI。",
                "支持 Logcat 实时查看和过滤。",
                "支持保留命令历史和故障定位线索。",
            ),
        entryPoints = listOf("Shell", "预置命令", "执行历史", "Logcat"),
    ),
}

private enum class UtilityAction {
    FixedPort,
    Screenshot,
    AdvancedReboot,
    ActivateApp,
    ModifyDpi,
    ModifyResolution,
    AnimationScale,
    SleepStandby,
    TombstoneMode,
}

private data class UtilityCardItem(
    val action: UtilityAction,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val available: Boolean = true,
    val statusText: String? = null,
    val statusChecked: Boolean? = null,
)

private fun utilityItems(
    isTcpipMode: Boolean,
    snapshot: DeviceDashboardSnapshot,
): List<UtilityCardItem> =
    listOf(
        UtilityCardItem(
            action = UtilityAction.FixedPort,
            title = "固定无线调试端口",
            subtitle = "设置 ADB TCP 端口号，设备将重启 ADB 服务",
            icon = Icons.Default.Wifi,
            accent = Color(0xFF12B7A2),
        ),
        UtilityCardItem(
            action = UtilityAction.Screenshot,
            title = "屏幕截图",
            subtitle = "截图到控制端本机缓存，可打开预览或保存到相册",
            icon = Icons.Default.PhotoCamera,
            accent = Color(0xFF6A5CFF),
        ),
        UtilityCardItem(
            action = UtilityAction.AdvancedReboot,
            title = "高级重启",
            subtitle = "正常重启、Recovery、FastBoot",
            icon = Icons.Default.RestartAlt,
            accent = Color(0xFF57B657),
        ),
        UtilityCardItem(
            action = UtilityAction.ActivateApp,
            title = "激活应用",
            subtitle = "点击后加载可激活应用列表",
            icon = Icons.Default.VerifiedUser,
            accent = Color(0xFF17C3E6),
        ),
        UtilityCardItem(
            action = UtilityAction.ModifyDpi,
            title = "修改DPI",
            subtitle = snapshot.currentDpiLabel?.let { "当前 $it，点击输入新数值" } ?: "输入新的屏幕密度",
            icon = Icons.Default.CropFree,
            accent = Color(0xFFF0C230),
        ),
        UtilityCardItem(
            action = UtilityAction.ModifyResolution,
            title = "修改分辨率",
            subtitle = snapshot.resolution.ifBlank { "调整屏幕分辨率大小" },
            icon = Icons.Default.CropFree,
            accent = Color(0xFF96D24D),
        ),
        UtilityCardItem(
            action = UtilityAction.AnimationScale,
            title = "动画调整",
            subtitle = "统一设置窗口、过渡和时长动画倍率",
            icon = Icons.Default.Tune,
            accent = Color(0xFFFF6A3D),
        ),
        UtilityCardItem(
            action = UtilityAction.SleepStandby,
            title = "熄屏待机",
            subtitle = "提供息屏、亮屏两个操作",
            icon = Icons.Default.Lightbulb,
            accent = Color(0xFF9E9E9E),
        ),
        UtilityCardItem(
            action = UtilityAction.TombstoneMode,
            title = "墓碑模式",
            subtitle = "暂未实现，当前保持禁用",
            icon = Icons.Default.Refresh,
            accent = Color(0xFF5B4BDB),
            available = false,
            statusText = "禁用",
        ),
    )

internal data class RemoteFileEntry(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val detail: String,
)

internal data class RemoteTextEditorState(
    val entry: RemoteFileEntry,
    val localFile: File,
    val content: String,
)

internal data class RemotePreparedFileState(
    val entry: RemoteFileEntry,
    val localFile: File,
)

internal data class RemoteBinaryPreviewState(
    val entry: RemoteFileEntry,
    val localFile: File,
    val preview: String,
)

private data class RemoteFileDetailSnapshot(
    val isLoading: Boolean,
    val name: String,
    val fullPath: String,
    val typeLabel: String,
    val permissions: String,
    val owner: String,
    val group: String,
    val sizeLabel: String,
    val modifiedTime: String,
    val errorMessage: String? = null,
) {
    companion object {
        fun loading(entry: RemoteFileEntry): RemoteFileDetailSnapshot =
            RemoteFileDetailSnapshot(
                isLoading = true,
                name = entry.name,
                fullPath = entry.fullPath,
                typeLabel = if (entry.isDirectory) "文件夹" else "文件",
                permissions = "--",
                owner = "--",
                group = "--",
                sizeLabel = "--",
                modifiedTime = "--",
            )
    }
}

internal sealed interface RemoteOverwriteConfirmState {
    val title: String
    val message: String

    data class PushBack(
        val entry: RemoteFileEntry,
    ) : RemoteOverwriteConfirmState {
        override val title: String = "确认回写设备？"
        override val message: String = "会用本机副本直接覆盖设备上的 ${entry.fullPath}"
    }
}

internal data class FileBrowserSnapshot(
    val isLoading: Boolean,
    val currentPath: String,
    val entries: List<RemoteFileEntry>,
    val errorMessage: String? = null,
) {
    companion object {
        fun loading(path: String): FileBrowserSnapshot =
            FileBrowserSnapshot(
                isLoading = true,
                currentPath = path,
                entries = emptyList(),
            )
    }
}

internal data class AppInventoryEntry(
    val packageName: String,
    val appTitle: String,
    val isSystemApp: Boolean,
    val apkPath: String,
    val isEnabled: Boolean,
    val versionCode: Long = 0L,
    val lastUpdateTime: Long = 0L,
)

internal data class AppInventorySnapshot(
    val isLoading: Boolean,
    val apps: List<AppInventoryEntry>,
    val shizukuInstalled: Boolean,
    val errorMessage: String? = null,
) {
    val packages: List<String>
        get() = apps.map { it.packageName }

    companion object {
        fun loading(): AppInventorySnapshot =
            AppInventorySnapshot(
                isLoading = true,
                apps = emptyList(),
                shizukuInstalled = false,
            )
    }
}

internal enum class AppListFilter(
    val label: String,
) {
    ShowSystemApps("显示系统应用"),
    ShowUserApps("显示第三方应用"),
    ShowEnabledApps("显示启用应用"),
    ShowDisabledApps("显示禁用应用"),
    ;

    companion object {
        val defaultSelection: Set<AppListFilter> =
            setOf(
                ShowUserApps,
                ShowEnabledApps,
            )
    }
}

internal enum class AppListSort(
    val label: String,
) {
    Title("按应用名排序"),
    Package("按包名排序"),
    EnabledState("按启用状态排序"),
}

@Serializable
private data class AppIconIndexSnapshot(
    val hashes: Map<String, String> = emptyMap(),
    val titles: Map<String, String> = emptyMap(),
)

internal fun resolveAppListTitle(
    entry: AppInventoryEntry,
    packageNameOnlyMode: Boolean,
): String {
    if (packageNameOnlyMode) {
        return entry.packageName
    }
    val cachedTitle = SessionManagementAppCache.cachedAppTitle(entry.packageName)
    return when {
        !cachedTitle.isNullOrBlank() -> cachedTitle
        else -> entry.appTitle
    }
}

internal object SessionManagementAppCache {
    private var processPrepared = false
    private var snapshot: AppInventorySnapshot? = null
    private val detailCache = mutableMapOf<String, AppDetailSnapshot>()
    private val iconCache = mutableMapOf<String, Bitmap?>()
    private val iconGenerationCache = mutableMapOf<String, Int>()
    private val iconHashCache = mutableMapOf<String, String>()
    private val titleCache = mutableMapOf<String, String>()
    private var iconHelperUnavailableReason: String? = null
    private var iconHelperDiagnosticsCaptured = false
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }

    fun prepareForProcess(context: Context) {
        synchronized(this) {
            if (processPrepared) return
            processPrepared = true
            clearSnapshot()
            detailCache.clear()
            titleCache.clear()
            iconHelperUnavailableReason = null
            iconHelperDiagnosticsCaptured = false
            loadIconIndex(context)
        }
    }

    fun snapshot(): AppInventorySnapshot? =
        synchronized(this) {
            snapshot
        }

    fun updateSnapshot(value: AppInventorySnapshot) {
        synchronized(this) {
            snapshot = value
            value.apps.forEach { entry ->
                titleCache.putIfAbsentCompat(entry.packageName, entry.appTitle)
            }
        }
    }

    fun clearSnapshot() {
        synchronized(this) {
            snapshot = null
        }
    }

    fun cachedAppDetail(packageName: String): AppDetailSnapshot? =
        synchronized(this) {
            detailCache[packageName]
        }

    fun updateAppDetail(snapshot: AppDetailSnapshot) {
        synchronized(this) {
            detailCache[snapshot.packageName] = snapshot
            if (snapshot.appTitle.isNotBlank()) {
                titleCache[snapshot.packageName] = snapshot.appTitle
            }
        }
    }

    fun appTitle(
        packageName: String,
        fallback: String,
    ): String =
        synchronized(this) {
            titleCache[packageName] ?: fallback
        }

    fun cachedAppTitle(packageName: String): String? =
        synchronized(this) {
            titleCache[packageName]
        }

    fun updateAppTitle(
        packageName: String,
        title: String,
    ) {
        synchronized(this) {
            if (title.isNotBlank() && titleCache[packageName] != title) {
                titleCache[packageName] = title
            }
        }
    }

    fun cachedIcon(packageName: String): Bitmap? =
        synchronized(this) {
            iconCache[packageName]
        }

    fun hasIcon(packageName: String): Boolean =
        synchronized(this) {
            iconCache.containsKey(packageName)
        }

    fun updateIcon(
        packageName: String,
        icon: Bitmap?,
        generation: Int = 0,
    ) {
        synchronized(this) {
            iconCache[packageName] = icon
            iconGenerationCache[packageName] = generation
        }
    }

    fun iconGeneration(packageName: String): Int? =
        synchronized(this) {
            iconGenerationCache[packageName]
        }

    fun clearIcons() {
        synchronized(this) {
            iconCache.clear()
            iconGenerationCache.clear()
        }
    }

    fun markIconHelperUnavailable(reason: String) {
        synchronized(this) {
            if (iconHelperUnavailableReason == null) {
                iconHelperUnavailableReason = reason
            }
        }
    }

    fun iconHelperUnavailableReason(): String? =
        synchronized(this) {
            iconHelperUnavailableReason
        }

    fun shouldCaptureIconHelperDiagnostics(): Boolean =
        synchronized(this) {
            !iconHelperDiagnosticsCaptured
        }

    fun markIconHelperDiagnosticsCaptured() {
        synchronized(this) {
            iconHelperDiagnosticsCaptured = true
        }
    }

    fun cachedIconHash(packageName: String): String? =
        synchronized(this) {
            iconHashCache[packageName]
        }

    fun updateIconHash(
        context: Context,
        packageName: String,
        hash: String,
    ) {
        synchronized(this) {
            iconHashCache[packageName] = hash
            writeIconIndex(context)
        }
    }

    private fun loadIconIndex(context: Context) {
        val file = getIconIndexFile(context)
        if (!file.exists()) return
        val snapshot =
            runCatching {
                json.decodeFromString<AppIconIndexSnapshot>(file.readText())
            }.getOrNull() ?: return
        iconHashCache.clear()
        iconHashCache.putAll(snapshot.hashes)
        titleCache.putAll(snapshot.titles)
    }

    private fun writeIconIndex(context: Context) {
        val file = getIconIndexFile(context)
        file.parentFile?.mkdirs()
        file.writeText(
            json.encodeToString(
                AppIconIndexSnapshot(
                    hashes = iconHashCache.toSortedMap(),
                    titles = titleCache.toSortedMap(),
                ),
            ),
        )
    }

    fun updateIconMetadataBatch(
        hashes: Map<String, String>,
        titles: Map<String, String> = emptyMap(),
        persist: Boolean = true,
        context: Context? = null,
    ) {
        synchronized(this) {
            var changed = false
            hashes.forEach { (packageName, hash) ->
                if (iconHashCache[packageName] != hash) {
                    iconHashCache[packageName] = hash
                    changed = true
                }
            }
            titles.forEach { (packageName, title) ->
                if (title.isNotBlank() && titleCache[packageName] != title) {
                    titleCache[packageName] = title
                    changed = true
                }
            }
            if (changed && persist) {
                requireNotNull(context) { "context is required when persist=true" }
                writeIconIndex(context)
            }
        }
    }

    fun persistIconMetadata(context: Context) {
        synchronized(this) {
            writeIconIndex(context)
        }
    }
}

private data class ProcessEntry(
    val packageName: String,
    val appTitle: String,
    val pid: String,
    val totalMemoryBytes: Long,
    val memory: String,
    val children: List<ProcessChildEntry>,
)

private data class ProcessChildEntry(
    val name: String,
    val pid: String,
    val memoryBytes: Long,
    val memory: String,
)

private data class RawProcessEntry(
    val name: String,
    val pid: String,
    val memoryBytes: Long,
)

private data class ProcessListSnapshot(
    val isLoading: Boolean,
    val entries: List<ProcessEntry>,
    val errorMessage: String? = null,
) {
    companion object {
        fun loading(): ProcessListSnapshot =
            ProcessListSnapshot(
                isLoading = true,
                entries = emptyList(),
            )
    }
}

internal data class AppDetailSnapshot(
    val isLoading: Boolean,
    val appTitle: String,
    val packageName: String,
    val apkSize: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val minSdk: String,
    val targetSdk: String,
    val firstInstallTime: String,
    val lastUpdateTime: String,
    val errorMessage: String? = null,
) {
    companion object {
        fun loading(packageName: String): AppDetailSnapshot =
            AppDetailSnapshot(
                isLoading = true,
                appTitle = guessAppTitle(packageName),
                packageName = packageName,
                apkSize = "",
                versionName = "",
                isSystemApp = false,
                minSdk = "",
                targetSdk = "",
                firstInstallTime = "",
                lastUpdateTime = "",
            )

        fun loading(entry: AppInventoryEntry): AppDetailSnapshot =
            AppDetailSnapshot(
                isLoading = true,
                appTitle = resolveAppListTitle(entry, packageNameOnlyMode = false),
                packageName = entry.packageName,
                apkSize = "",
                versionName = "",
                isSystemApp = entry.isSystemApp,
                minSdk = "",
                targetSdk = "",
                firstInstallTime = "",
                lastUpdateTime = "",
            )
    }
}

private data class DeviceDashboardSnapshot(
    val isLoading: Boolean,
    val model: String,
    val manufacturer: String,
    val socModel: String,
    val androidVersion: String,
    val uptime: String,
    val basebandVersion: String,
    val productCodeName: String,
    val securityPatch: String,
    val serialNumber: String,
    val connectionTypeLabel: String,
    val resolution: String,
    val dpi: String,
    val ppi: String,
    val screenSize: String,
    val refreshRate: String,
    val storageSummary: String,
    val memoryTotal: String,
    val memoryAvailable: String,
    val batteryLevel: String,
    val batteryStatus: String,
    val batteryHealth: String,
    val voltage: String,
    val currentNow: String,
    val temperature: String,
    val cpuSummary: String,
    val abi: String,
    val board: String,
    val fingerprint: String,
    val mobileNetworkType: String,
    val carrierNames: String,
    val mobileBand: String,
    val mobilePci: String,
    val mobileEarfcn: String,
    val rsrp: String,
    val rsrq: String,
    val sinr: String,
    val wifiSsid: String,
    val wifiBssid: String,
    val wifiIpAddress: String,
    val wifiFrequency: String,
    val wifiLinkSpeed: String,
    val supportedRefreshRates: String,
    val batteryCycleCount: String,
    val wirelessDebugPort: Int? = null,
    val errorMessage: String? = null,
) {
    val brandModelLabel: String
        get() =
            listOf(manufacturer, model)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { model.ifBlank { "未知设备" } }

    val androidVersionLabel: String
        get() = androidVersion.ifBlank { "Android" }

    val heroSummary: String
        get() =
            when {
                errorMessage != null -> "设备已连接，但设备信息读取失败。"
                isLoading -> "设备已连接，正在同步首页参数信息。"
                else -> "当前设备为 $model，系统 $androidVersion，屏幕 $resolution，数据存储 $storageSummary。"
            }

    val currentDpiValue: String?
        get() = dpi.substringAfterLast(": ", dpi).trim().takeIf { it.isNotBlank() }

    val currentDpiLabel: String?
        get() = currentDpiValue?.let { "$it DPI" }

    val currentResolutionWidth: String?
        get() =
            resolution
                .substringAfterLast(": ", resolution)
                .substringBefore("x")
                .trim()
                .takeIf { it.isNotBlank() }

    val currentResolutionHeight: String?
        get() = resolution.substringAfterLast("x", "").trim().takeIf { it.isNotBlank() }

    companion object {
        fun loading(sessionData: SessionData): DeviceDashboardSnapshot =
            DeviceDashboardSnapshot(
                isLoading = true,
                model = sessionData.name.ifBlank { "设备" },
                manufacturer = "",
                socModel = "",
                androidVersion = "Android",
                uptime = "",
                basebandVersion = "",
                productCodeName = "",
                securityPatch = "",
                serialNumber = sessionData.getUsbSerialNumber().orEmpty(),
                connectionTypeLabel = if (sessionData.isUsbConnection()) "USB / OTG" else "WiFi / TCP",
                resolution = "",
                dpi = "",
                ppi = "",
                screenSize = "",
                refreshRate = "",
                storageSummary = "",
                memoryTotal = "",
                memoryAvailable = "",
                batteryLevel = "",
                batteryStatus = "",
                batteryHealth = "",
                voltage = "",
                currentNow = "",
                temperature = "",
                cpuSummary = "",
                abi = "",
                board = "",
                fingerprint = "",
                mobileNetworkType = "",
                carrierNames = "",
                mobileBand = "",
                mobilePci = "",
                mobileEarfcn = "",
                rsrp = "",
                rsrq = "",
                sinr = "",
                wifiSsid = "",
                wifiBssid = "",
                wifiIpAddress = "",
                wifiFrequency = "",
                wifiLinkSpeed = "",
                supportedRefreshRates = "",
                batteryCycleCount = "",
                wirelessDebugPort = null,
            )
    }
}

private data class SignalMetrics(
    val rsrp: String,
    val rsrq: String,
    val sinr: String,
)

private data class CellularIdentityMetrics(
    val band: String,
    val pci: String,
    val earfcn: String,
)

private data class WifiMetrics(
    val ssid: String,
    val bssid: String,
    val frequency: String,
    val linkSpeed: String,
)

private suspend fun loadDeviceDashboardSnapshot(sessionData: SessionData): DeviceDashboardSnapshot {
    val connection = AdbBridge.getConnection()
    if (connection == null) {
        return DeviceDashboardSnapshot.loading(sessionData).copy(
            isLoading = false,
            errorMessage = "当前没有可用的 ADB 连接，无法读取首页参数。",
        )
    }

    suspend fun shell(command: String): String =
        connection
            .executeShell(command, retryOnFailure = false)
            .getOrNull()
            ?.trim()
            .orEmpty()

    return runCatching {
        coroutineScope {
            val modelDeferred = async { shell("getprop ro.product.model") }
            val manufacturerDeferred = async { shell("getprop ro.product.manufacturer") }
            val socModelDeferred = async { shell("getprop ro.soc.model") }
            val androidVersionDeferred = async { shell("getprop ro.build.version.release") }
            val uptimeDeferred = async { shell("cat /proc/uptime | awk '{print \$1}'") }
            val basebandDeferred = async { shell("getprop gsm.version.baseband") }
            val productCodeNameDeferred = async { shell("getprop ro.product.device") }
            val securityPatchDeferred = async { shell("getprop ro.build.version.security_patch") }
            val serialDeferred = async { shell("getprop ro.serialno") }
            val resolutionDeferred = async { shell("wm size | grep -E 'Physical|Override' | head -n 1") }
            val dpiDeferred = async { shell("wm density | grep -E 'Physical|Override' | head -n 1") }
            val displayMetricsDeferred =
                async { shell("dumpsys display | grep -E 'xDpi=|yDpi=|density .* dpi' | head -n 4") }
            val displayRefreshDeferred = async { shell("dumpsys display | grep -m 1 'DisplayDeviceInfo{'") }
            val wifiIpDeferred = async { shell("ip addr show wlan0 | grep -m 1 'inet '") }
            val mobileNetworkTypeDeferred = async { shell("getprop gsm.network.type") }
            val carrierNamesDeferred = async { shell("getprop gsm.operator.alpha") }
            val signalStrengthDeferred = async { shell("dumpsys telephony.registry | grep -m 1 'mSignalStrength='") }
            val cellIdentityDeferred = async { shell("dumpsys telephony.registry | grep -m 1 'mCellIdentity='") }
            val wifiInfoDeferred = async { shell("dumpsys wifi | grep -m 1 'mWifiInfo SSID:'") }
            val memoryDeferred = async { shell("cat /proc/meminfo | grep -E 'MemTotal|MemAvailable'") }
            val dataDfDeferred = async { shell("df /data | tail -n 1") }
            val batteryCycleDeferred =
                async {
                    shell(
                        """
                        for path in /sys/class/power_supply/battery/cycle_count /sys/class/power_supply/bq_bms/cycle_count
                        do
                            if [ -r "${'$'}path" ]; then
                                value=${'$'}(cat "${'$'}path" 2>/dev/null)
                                if [ -n "${'$'}value" ]; then
                                    echo "${'$'}value"
                                    break
                                fi
                            fi
                        done
                        """.trimIndent(),
                    )
                }
            val batteryDeferred =
                async {
                    shell(
                        "dumpsys battery | grep -E 'level:|status:|health:|voltage:|temperature:|current now:|current average:'",
                    )
                }
            val voltageNowDeferred = async { shell("cat /sys/class/power_supply/battery/voltage_now 2>/dev/null") }
            val batteryCurrentNowDeferred = async { shell("cmd battery get -f current_now 2>/dev/null") }
            val batteryCurrentAverageDeferred = async { shell("cmd battery get -f current_average 2>/dev/null") }
            val currentNowDeferred =
                async {
                    shell(
                        """
                        for path in \
                            /sys/class/power_supply/battery/current_now \
                            /sys/class/power_supply/battery/current_avg \
                            /sys/class/power_supply/bms/current_now \
                            /sys/class/power_supply/main/current_now \
                            /sys/class/power_supply/battery/constant_charge_current \
                            /sys/class/power_supply/usb/current_max
                        do
                            if [ -r "${'$'}path" ]; then
                                value=${'$'}(cat "${'$'}path" 2>/dev/null)
                                if [ -n "${'$'}value" ]; then
                                    echo "${'$'}value"
                                    break
                                fi
                            fi
                        done
                        """.trimIndent(),
                    )
                }
            val abiDeferred = async { shell("getprop ro.product.cpu.abilist") }
            val boardDeferred = async { shell("getprop ro.product.board") }
            val fingerprintDeferred = async { shell("getprop ro.build.fingerprint") }
            val wirelessPortDeferred = async { shell("getprop service.adb.tcp.port") }
            val cpuCountDeferred = async { shell("cat /proc/cpuinfo | grep -c processor") }
            val cpuFreqDeferred =
                async { shell("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq 2>/dev/null") }

            val memoryMap = parseKeyValueBlock(memoryDeferred.await())
            val batteryMap = parseKeyValueBlock(batteryDeferred.await())
            val cpuCount = cpuCountDeferred.await()
            val cpuFreqKhz = cpuFreqDeferred.await()
            val resolutionValue = formatWmValue(resolutionDeferred.await(), "Physical size:")
            val dpiValue = formatWmValue(dpiDeferred.await(), "Physical density:")
            val densityDpi = parseDensityDpi(dpiValue)
            val physicalDpiPair =
                parsePhysicalDpiPair(displayMetricsDeferred.await())
                    ?: densityDpi?.let { it.toDouble() to it.toDouble() }
            val signalMetrics = parseSignalMetrics(signalStrengthDeferred.await())
            val cellIdentityMetrics = parseCellularIdentityMetrics(cellIdentityDeferred.await())
            val wifiMetrics = parseWifiMetrics(wifiInfoDeferred.await())

            DeviceDashboardSnapshot(
                isLoading = false,
                model = modelDeferred.await().ifBlank { sessionData.name.ifBlank { "未知设备" } },
                manufacturer = manufacturerDeferred.await(),
                socModel = formatSocModel(socModelDeferred.await(), boardDeferred.await()),
                androidVersion = androidVersionDeferred.await().ifBlank { "Android" },
                uptime = formatUptime(uptimeDeferred.await()),
                basebandVersion = formatBasebandVersion(basebandDeferred.await()),
                productCodeName = productCodeNameDeferred.await(),
                securityPatch = securityPatchDeferred.await(),
                serialNumber = serialDeferred.await(),
                connectionTypeLabel = if (sessionData.isUsbConnection()) "USB / OTG" else "WiFi / TCP",
                resolution = resolutionValue,
                dpi = dpiValue,
                ppi = formatPpi(physicalDpiPair),
                screenSize = formatScreenSize(resolutionValue, physicalDpiPair),
                refreshRate = formatRefreshRate(displayRefreshDeferred.await()),
                storageSummary = formatStorageSummary(dataDfDeferred.await()),
                memoryTotal = formatMemValue(memoryMap["MemTotal"]),
                memoryAvailable = formatMemValue(memoryMap["MemAvailable"]),
                batteryLevel = batteryMap["level"]?.let { "$it %" }.orEmpty(),
                batteryStatus = mapBatteryStatus(batteryMap["status"]),
                batteryHealth = mapBatteryHealth(batteryMap["health"]),
                voltage = formatBatteryVoltage(batteryMap["voltage"], voltageNowDeferred.await()),
                currentNow =
                    formatBatteryCurrent(
                        dumpsysCurrentNow = batteryMap["current now"],
                        dumpsysCurrentAverage = batteryMap["current average"],
                        batteryCurrentNow = batteryCurrentNowDeferred.await(),
                        batteryCurrentAverage = batteryCurrentAverageDeferred.await(),
                        sysfsCurrent = currentNowDeferred.await(),
                    ),
                temperature = batteryMap["temperature"]?.let { formatBatteryTemperature(it) }.orEmpty(),
                cpuSummary = formatCpuSummary(cpuCount, cpuFreqKhz),
                abi = abiDeferred.await(),
                board = boardDeferred.await(),
                fingerprint = fingerprintDeferred.await(),
                mobileNetworkType = formatNetworkPropertyList(mobileNetworkTypeDeferred.await()),
                carrierNames = formatCarrierNames(carrierNamesDeferred.await()),
                mobileBand = cellIdentityMetrics.band,
                mobilePci = cellIdentityMetrics.pci,
                mobileEarfcn = cellIdentityMetrics.earfcn,
                rsrp = signalMetrics.rsrp,
                rsrq = signalMetrics.rsrq,
                sinr = signalMetrics.sinr,
                wifiSsid = wifiMetrics.ssid,
                wifiBssid = wifiMetrics.bssid,
                wifiIpAddress = formatWifiIpAddress(wifiIpDeferred.await()),
                wifiFrequency = wifiMetrics.frequency,
                wifiLinkSpeed = wifiMetrics.linkSpeed,
                supportedRefreshRates = formatSupportedRefreshRates(displayRefreshDeferred.await()),
                batteryCycleCount = formatBatteryCycleCount(batteryCycleDeferred.await()),
                wirelessDebugPort = parseAdbTcpPort(wirelessPortDeferred.await()),
            )
        }
    }.getOrElse { error ->
        DeviceDashboardSnapshot.loading(sessionData).copy(
            isLoading = false,
            errorMessage = error.message ?: "读取设备首页参数失败。",
        )
    }
}

internal suspend fun loadAppInventorySnapshot(
    context: Context,
    includeSystemApps: Boolean,
    forceRefresh: Boolean = false,
): AppInventorySnapshot {
    if (!forceRefresh) {
        SessionManagementAppCache.snapshot()?.let { cached ->
            return if (includeSystemApps) {
                cached
            } else {
                cached.copy(
                    apps = cached.apps.filterNot { it.isSystemApp },
                    shizukuInstalled = cached.apps.any { it.packageName == "moe.shizuku.privileged.api" },
                )
            }
        }
    }

    val connection = AdbBridge.getConnection()
    if (connection == null) {
        return AppInventorySnapshot.loading().copy(
            isLoading = false,
            errorMessage = "当前没有可用的 ADB 连接，无法读取应用列表。",
        )
    }

    return runCatching {
        loadAppInventorySnapshotWithShell(
            connection = connection,
            includeSystemApps = includeSystemApps,
        )
    }.getOrElse { error ->
        AppInventorySnapshot.loading().copy(
            isLoading = false,
            errorMessage = error.message ?: "读取应用列表失败。",
        )
    }
}

private suspend fun loadAppInventorySnapshotWithShell(
    connection: com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection,
    includeSystemApps: Boolean,
): AppInventorySnapshot =
    runCatching {
        if (!includeSystemApps) {
            val output =
                connection
                    .executeShell("pm list packages -3 -e -f", retryOnFailure = false)
                    .getOrThrow()

            val apps =
                output
                    .lineSequence()
                    .mapNotNull { parseAppInventoryLine(it, emptySet(), emptySet()) }
                    .sortedWith(
                        compareBy<AppInventoryEntry> { it.packageName.lowercase(Locale.getDefault()) },
                    ).toList()

            val snapshot =
                AppInventorySnapshot(
                    isLoading = false,
                    apps = apps,
                    shizukuInstalled = apps.any { it.packageName == "moe.shizuku.privileged.api" },
                )
            SessionManagementAppCache.updateSnapshot(snapshot)
            return@runCatching snapshot
        }

        val output =
            connection
                .executeShell("pm list packages -f", retryOnFailure = false)
                .getOrThrow()
        val userPackagesOutput =
            connection
                .executeShell("pm list packages -3 -f", retryOnFailure = false)
                .getOrNull()
                .orEmpty()
        val userPackages =
            userPackagesOutput
                .lineSequence()
                .mapNotNull { parseAppInventoryLine(it, emptySet(), emptySet()) }
                .map { it.packageName }
                .toSet()

        val disabledOutput =
            connection
                .executeShell("pm list packages -d", retryOnFailure = false)
                .getOrNull()
                .orEmpty()
        val disabledPackages =
            disabledOutput
                .lineSequence()
                .map { it.trim().removePrefix("package:").trim() }
                .filter { it.isNotBlank() }
                .toSet()

        val apps =
            output
                .lineSequence()
                .mapNotNull { parseAppInventoryLine(it, disabledPackages, userPackages) }
                .sortedWith(
                    compareBy<AppInventoryEntry> { it.packageName.lowercase(Locale.getDefault()) },
                ).toList()

        val fullSnapshot =
            AppInventorySnapshot(
                isLoading = false,
                apps = apps,
                shizukuInstalled = apps.any { it.packageName == "moe.shizuku.privileged.api" },
            )
        SessionManagementAppCache.updateSnapshot(fullSnapshot)

        if (includeSystemApps) {
            fullSnapshot
        } else {
            fullSnapshot.copy(
                apps = fullSnapshot.apps.filterNot { it.isSystemApp },
                shizukuInstalled = fullSnapshot.shizukuInstalled,
            )
        }
    }.getOrThrow()

private fun remoteAppListItemToInventoryEntry(item: RemoteAppListItem): AppInventoryEntry =
    AppInventoryEntry(
        packageName = item.packageName,
        appTitle = item.label.ifBlank { guessAppTitle(item.packageName) },
        isSystemApp = item.systemApp,
        apkPath = item.sourceDir,
        isEnabled = item.enabled,
        versionCode = item.versionCode,
        lastUpdateTime = item.lastUpdateTime,
    )

private fun supportedActivationTargets(installedPackages: Set<String>): List<ActivationTarget> =
    listOfNotNull(
        activationTargetIfInstalled(
            label = "Shizuku",
            packageName = "moe.shizuku.privileged.api",
            command = "sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh",
            installedPackages = installedPackages,
        ),
        activationTargetIfInstalled(
            label = "黑域",
            packageName = "me.piebridge.brevent",
            command = "sh /data/data/me.piebridge.brevent/brevent.sh",
            installedPackages = installedPackages,
        ),
        activationTargetIfInstalled(
            label = "冰箱",
            packageName = "com.catchingnow.icebox",
            command = "sh /storage/emulated/0/Android/data/com.catchingnow.icebox/files/start.sh",
            installedPackages = installedPackages,
        ),
    ).ifEmpty {
        listOf(
            ActivationTarget(
                label = "Shizuku",
                packageName = "moe.shizuku.privileged.api",
                command = "",
            ),
            ActivationTarget(
                label = "黑域",
                packageName = "me.piebridge.brevent",
                command = "",
            ),
            ActivationTarget(
                label = "冰箱",
                packageName = "com.catchingnow.icebox",
                command = "",
            ),
        )
    }

private fun activationTargetIfInstalled(
    label: String,
    packageName: String,
    command: String,
    installedPackages: Set<String>,
): ActivationTarget =
    ActivationTarget(
        label = label,
        packageName = packageName,
        command = if (packageName in installedPackages) command else "",
    )

private fun parseAppInventoryLine(
    line: String,
    disabledPackages: Set<String> = emptySet(),
    userPackages: Set<String> = emptySet(),
): AppInventoryEntry? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("package:")) return null

    val body = trimmed.removePrefix("package:")
    val separatorIndex = body.lastIndexOf('=')
    if (separatorIndex <= 0 || separatorIndex >= body.lastIndex) {
        return null
    }

    val apkPath = body.substring(0, separatorIndex).trim()
    val packageName = body.substring(separatorIndex + 1).trim()
    if (packageName.isBlank()) return null

    return AppInventoryEntry(
        packageName = packageName,
        appTitle = guessAppTitle(packageName),
        isSystemApp =
            if (userPackages.isNotEmpty()) {
                packageName !in userPackages
            } else {
                isSystemApkPath(apkPath)
            },
        apkPath = apkPath,
        isEnabled = packageName !in disabledPackages,
    )
}

private fun guessAppTitle(packageName: String): String {
    val normalized = packageName.substringBefore(':')
    val exactPredefined =
        mapOf(
            "moe.shizuku.privileged.api" to "Shizuku",
            "me.piebridge.brevent" to "Brevent",
            "com.catchingnow.icebox" to "Ice Box",
        )
    exactPredefined[normalized]?.let { return it }

    val key = normalized.substringAfterLast('.').lowercase(Locale.getDefault())
    val predefined =
        mapOf(
            "android" to "Android 系统",
            "settings" to "设置",
            "systemui" to "系统 UI",
            "vending" to "Google Play Store",
            "documentsui" to "文档",
            "packageinstaller" to "安装程序",
            "launcher" to "桌面",
            "oneuihome" to "One UI 主屏幕",
            "permissioncontroller" to "权限控制器",
            "bluetooth" to "蓝牙",
            "phone" to "电话",
            "contacts" to "联系人",
            "camera" to "相机",
            "gallery" to "相册",
            "music" to "音乐",
            "video" to "视频",
        )

    predefined[key]?.let { return it }

    val genericSuffixes =
        setOf(
            "app",
            "apps",
            "api",
            "android",
            "cn",
            "com",
            "client",
            "core",
            "debug",
            "helper",
            "impl",
            "io",
            "main",
            "me",
            "mobile",
            "net",
            "org",
            "privileged",
            "release",
            "service",
            "services",
            "tv",
            "ui",
        )
    val meaningfulToken =
        normalized
            .split(Regex("[._-]+"))
            .asReversed()
            .firstOrNull { token ->
                val lower = token.lowercase(Locale.getDefault())
                token.isNotBlank() && token.length > 2 && lower !in genericSuffixes
            }

    val lastToken = normalized.substringAfterLast('.').ifBlank { normalized }
    val fallback =
        meaningfulToken
            ?: lastToken.takeIf { token ->
                token.lowercase(Locale.getDefault()) !in genericSuffixes && token.length > 2
            }
            ?: normalized
    return fallback.replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase(Locale.getDefault())
        } else {
            char.toString()
        }
    }
}

private fun isSystemApkPath(apkPath: String): Boolean =
    apkPath.startsWith("/system/") ||
        apkPath.startsWith("/product/") ||
        apkPath.startsWith("/vendor/") ||
        apkPath.startsWith("/apex/")

internal data class RemoteAppPresentation(
    val title: String,
    val icon: Bitmap?,
)

private const val APP_ICON_HELPER_ASSET_NAME = "dadb-icon-helper.jar"
private const val APP_LIST_HELPER_PAGE_SIZE = 200
private const val APP_ICON_HELPER_BATCH_SIZE = 50
private const val APP_ICON_HELPER_CONCURRENCY = 3

private data class AppIconChunkResult(
    val changedCount: Int,
    val updatedHashes: Map<String, String>,
    val updatedTitles: Map<String, String>,
    val updatedPackages: List<String>,
)

internal suspend fun loadCachedAppPresentation(
    context: Context,
    entry: AppInventoryEntry,
    packageNameOnlyMode: Boolean,
): RemoteAppPresentation =
    withContext(Dispatchers.IO) {
        val resolvedTitle =
            if (packageNameOnlyMode) {
                entry.packageName
            } else {
                SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle)
            }

        if (packageNameOnlyMode) {
            return@withContext RemoteAppPresentation(
                title = resolvedTitle,
                icon = SessionManagementAppCache.cachedIcon(entry.packageName),
            )
        }

        val cachedIcon = SessionManagementAppCache.cachedIcon(entry.packageName)
        if (cachedIcon != null) {
            return@withContext RemoteAppPresentation(
                title = resolvedTitle,
                icon = cachedIcon,
            )
        }

        val iconFile = getAppIconFile(context, entry.packageName)
        val bitmap =
            if (iconFile.exists()) {
                runCatching { android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath) }.getOrNull()
            } else {
                null
            }
        if (bitmap != null) {
            SessionManagementAppCache.updateIcon(entry.packageName, bitmap)
        }

        RemoteAppPresentation(
            title = resolvedTitle,
            icon = bitmap,
        )
    }

internal suspend fun prefetchAppIconsWithHelper(
    context: Context,
    entries: List<AppInventoryEntry>,
    helperJar: File,
    onChunkApplied: suspend (List<String>) -> Unit = {},
): Int =
    withContext(Dispatchers.IO) {
        val connection = AdbBridge.getConnection() ?: return@withContext 0
        val allUpdatedHashes = linkedMapOf<String, String>()
        val allUpdatedTitles = linkedMapOf<String, String>()
        entries
            .chunked(APP_ICON_HELPER_BATCH_SIZE)
            .chunked(APP_ICON_HELPER_CONCURRENCY)
            .sumOf { wave ->
                coroutineScope {
                    wave
                        .map { chunk ->
                            async {
                                prefetchAppIconChunkWithHelper(
                                    context = context,
                                    connection = connection,
                                    chunk = chunk,
                                    helperJar = helperJar,
                                )
                            }
                        }.awaitAll()
                        .sumOf { result ->
                            if (result.updatedHashes.isNotEmpty() || result.updatedTitles.isNotEmpty()) {
                                SessionManagementAppCache.updateIconMetadataBatch(
                                    hashes = result.updatedHashes,
                                    titles = result.updatedTitles,
                                    persist = false,
                                )
                                allUpdatedHashes.putAll(result.updatedHashes)
                                allUpdatedTitles.putAll(result.updatedTitles)
                                withContext(Dispatchers.Main) {
                                    onChunkApplied(result.updatedPackages)
                                }
                            }
                            result.changedCount
                        }
                }
            }.also {
                if (allUpdatedHashes.isNotEmpty() || allUpdatedTitles.isNotEmpty()) {
                    SessionManagementAppCache.persistIconMetadata(context)
                }
            }
    }

private suspend fun prefetchAppIconChunkWithHelper(
    context: Context,
    connection: com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection,
    chunk: List<AppInventoryEntry>,
    helperJar: File,
): AppIconChunkResult {
    val requests =
        chunk.map { entry ->
            val iconFile = getAppIconFile(context, entry.packageName)
            RemoteAppIconBatchRequest(
                packageName = entry.packageName,
                localHash =
                    SessionManagementAppCache
                        .cachedIconHash(entry.packageName)
                        ?.takeIf { iconFile.exists() },
            )
        }

    val result =
        connection
            .loadAppIconBatchWithHelper(
                requests = requests,
                localHelperJar = helperJar,
            ).getOrThrow()

    val updatedHashes = linkedMapOf<String, String>()
    val updatedTitles = linkedMapOf<String, String>()
    val updatedPackages = mutableListOf<String>()
    var changedCount = 0

    result.entries.forEach { changedIcon ->
        updatedHashes[changedIcon.packageName] = changedIcon.iconHash
        updatedTitles[changedIcon.packageName] = changedIcon.label
        updatedPackages += changedIcon.packageName

        val imageBytes = changedIcon.imageBytes
        if (imageBytes != null) {
            val iconFile = getAppIconFile(context, changedIcon.packageName)
            iconFile.parentFile?.mkdirs()
            iconFile.writeBytes(imageBytes)
            val bitmap =
                android.graphics.BitmapFactory.decodeByteArray(
                    imageBytes,
                    0,
                    imageBytes.size,
                )
            SessionManagementAppCache.updateIcon(changedIcon.packageName, bitmap)
            changedCount += 1
        } else {
            SessionManagementAppCache.updateIcon(
                changedIcon.packageName,
                SessionManagementAppCache.cachedIcon(changedIcon.packageName),
            )
        }
    }

    return AppIconChunkResult(
        changedCount = changedCount,
        updatedHashes = updatedHashes,
        updatedTitles = updatedTitles,
        updatedPackages = updatedPackages,
    )
}

private suspend fun loadRemoteAppPresentation(
    context: Context,
    entry: AppInventoryEntry,
    iconRefreshGeneration: Int,
): RemoteAppPresentation {
    val cachedTitle = SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle)
    val cachedGeneration = SessionManagementAppCache.iconGeneration(entry.packageName)
    if (SessionManagementAppCache.hasIcon(entry.packageName) && cachedGeneration == iconRefreshGeneration) {
        return RemoteAppPresentation(
            title = cachedTitle,
            icon = SessionManagementAppCache.cachedIcon(entry.packageName),
        )
    }

    return withContext(Dispatchers.IO) {
        val iconFile = getAppIconFile(context, entry.packageName)
        val shouldRefreshFromDevice =
            iconRefreshGeneration > 0 && cachedGeneration != iconRefreshGeneration
        val localHash =
            SessionManagementAppCache
                .cachedIconHash(entry.packageName)
                ?.takeIf { iconFile.exists() }
        val helperUnavailableReason = SessionManagementAppCache.iconHelperUnavailableReason()
        val presentation =
            if ((!shouldRefreshFromDevice || helperUnavailableReason != null) && iconFile.exists()) {
                RemoteAppPresentation(
                    title = cachedTitle,
                    icon = runCatching { android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath) }.getOrNull(),
                )
            } else if (helperUnavailableReason != null) {
                RemoteAppPresentation(
                    title = cachedTitle,
                    icon = null,
                )
            } else {
                fetchAndSaveAppPresentationWithHelper(context, entry, localHash)
                    ?: RemoteAppPresentation(title = cachedTitle, icon = null)
            }

        SessionManagementAppCache.updateIcon(
            packageName = entry.packageName,
            icon = presentation.icon,
            generation = iconRefreshGeneration,
        )
        SessionManagementAppCache.updateAppTitle(entry.packageName, presentation.title)

        presentation
    }
}

private fun getAppIconFile(
    context: Context,
    packageName: String,
): java.io.File {
    val iconDir =
        java.io.File(
            context.filesDir,
            com.mobile.scrcpy.android.core.common.constants.FilePathConstants.APP_ICONS_DIR,
        )
    if (!iconDir.exists()) {
        iconDir.mkdirs()
    }
    return java.io.File(iconDir, "${sanitizeAppIconFileName(packageName)}.webp")
}

private fun getIconIndexFile(context: Context): File =
    File(
        File(context.filesDir, com.mobile.scrcpy.android.core.common.constants.FilePathConstants.APP_ICONS_DIR),
        "index.json",
    )

internal suspend fun copyUriToTempApk(
    context: Context,
    uri: android.net.Uri,
): File =
    withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "session-management/install").apply { mkdirs() }
        val tempFile = File(tempDir, "picked-${System.currentTimeMillis()}.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("无法读取选择的 APK 文件。")
        tempFile
    }

private fun sanitizeAppIconFileName(packageName: String): String = packageName.replace(':', '_')

internal fun ensureLocalAppIconHelperJar(context: Context): File {
    val helperDir = File(context.filesDir, "dadb-helpers").apply { mkdirs() }
    val helperFile = File(helperDir, APP_ICON_HELPER_ASSET_NAME)
    context.assets.open(APP_ICON_HELPER_ASSET_NAME).use { input ->
        helperFile.outputStream().use { output -> input.copyTo(output) }
    }
    return helperFile
}

private suspend fun fetchAndSaveAppIcon(
    context: Context,
    packageName: String,
    apkPath: String,
): Bitmap? =
    fetchAndSaveAppPresentationWithHelper(
        context = context,
        entry =
            AppInventoryEntry(
                packageName = packageName,
                appTitle = SessionManagementAppCache.appTitle(packageName, guessAppTitle(packageName)),
                isSystemApp = false,
                apkPath = apkPath,
                isEnabled = true,
            ),
        localHash = SessionManagementAppCache.cachedIconHash(packageName),
    )?.icon

private suspend fun fetchAndSaveAppPresentationWithHelper(
    context: Context,
    entry: AppInventoryEntry,
    localHash: String?,
): RemoteAppPresentation? {
    val connection = AdbBridge.getConnection() ?: return null

    return runCatching {
        val helperJar = ensureLocalAppIconHelperJar(context)
        val helperResult =
            connection
                .loadAppIconWithHelper(
                    packageName = entry.packageName,
                    localHash = localHash,
                    localHelperJar = helperJar,
                ).getOrThrow()

        val resolvedTitle =
            helperResult.label
                .takeIf { it.isNotBlank() }
                ?: SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle)

        val iconFile = getAppIconFile(context, entry.packageName)
        val bitmap =
            if (helperResult.changed) {
                val bytes = helperResult.imageBytes ?: error("Helper returned changed icon without bytes")
                iconFile.parentFile?.mkdirs()
                iconFile.writeBytes(bytes)
                SessionManagementAppCache.updateIconHash(context, entry.packageName, helperResult.iconHash)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                if (!iconFile.exists()) {
                    null
                } else {
                    SessionManagementAppCache.updateIconHash(context, entry.packageName, helperResult.iconHash)
                    android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath)
                }
            }

        RemoteAppPresentation(
            title = resolvedTitle,
            icon = bitmap,
        )
    }.getOrElse { error ->
        if (SessionManagementAppCache.shouldCaptureIconHelperDiagnostics()) {
            runCatching {
                captureAppHelperDiagnostics(
                    context = context,
                    packageName = entry.packageName,
                )
            }
            SessionManagementAppCache.markIconHelperDiagnosticsCaptured()
        }
        if (error.message?.contains("RuntimeInit", ignoreCase = true) == true ||
            error.message?.contains("Killed", ignoreCase = true) == true
        ) {
            SessionManagementAppCache.markIconHelperUnavailable(error.message ?: "icon helper unavailable")
        }
        runCatching {
            com.mobile.scrcpy.android.core.common.manager.LogManager.w(
                com.mobile.scrcpy.android.core.common.LogTags.ADB_CONNECTION,
                "helper 获取应用图标失败 ${entry.packageName}: ${error.message}",
            )
        }
        null
    }
}

private suspend fun captureAppHelperDiagnostics(
    context: Context,
    packageName: String,
) {
    val connection = AdbBridge.getConnection() ?: return
    val helperJar = ensureLocalAppIconHelperJar(context)
    val probes =
        listOf(
            "ping" to emptyList(),
            "runtime" to emptyList(),
            "context" to emptyList(),
            "pm" to emptyList(),
            "apps" to emptyList(),
            "iconprobe" to listOf(packageName),
        )

    probes.forEach { (command, args) ->
        val result = connection.runAppHelperProbe(command, args, helperJar)
        result.fold(
            onSuccess = { probe ->
                com.mobile.scrcpy.android.core.common.manager.LogManager.e(
                    com.mobile.scrcpy.android.core.common.LogTags.ADB_CONNECTION,
                    "helper probe ${probe.command} exit=${probe.exitCode} stdout=${
                        probe.stdout.ifBlank {
                            "<empty>"
                        }
                    } stderr=${probe.stderr.ifBlank { "<empty>" }}",
                )
            },
            onFailure = { error ->
                com.mobile.scrcpy.android.core.common.manager.LogManager.e(
                    com.mobile.scrcpy.android.core.common.LogTags.ADB_CONNECTION,
                    "helper probe $command 执行失败: ${error.message}",
                    error,
                )
            },
        )
    }
}

private fun overwriteBitmapFileIfChanged(
    iconFile: File,
    bitmap: Bitmap,
) {
    iconFile.parentFile?.mkdirs()

    val newBytes =
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }

    if (iconFile.exists()) {
        val oldBytes = runCatching { iconFile.readBytes() }.getOrNull()
        if (oldBytes != null && sha256(oldBytes) == sha256(newBytes)) {
            return
        }
    }

    iconFile.writeBytes(newBytes)
}

private fun sha256(bytes: ByteArray): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

internal suspend fun loadFileBrowserSnapshot(path: String): FileBrowserSnapshot {
    val connection =
        AdbBridge.getConnection()
            ?: return FileBrowserSnapshot.loading(path).copy(
                isLoading = false,
                errorMessage = "当前没有可用的 ADB 连接，无法读取目录。",
            )

    suspend fun shell(command: String): String =
        connection
            .executeShell(command, retryOnFailure = false)
            .getOrNull()
            ?.trim()
            .orEmpty()

    return runCatching {
        coroutineScope {
            val escapedPath = path.replace("\"", "\\\"")
            val output =
                connection
                    .executeShell("ls -1Ap \"$escapedPath\"", retryOnFailure = false)
                    .getOrThrow()

            val entries =
                output
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it != "." && it != ".." }
                    .map { raw ->
                        async {
                            val isDirectory = raw.endsWith("/")
                            val cleanName = raw.removeSuffix("/")
                            val fullPath = joinRemotePath(path, cleanName)
                            val modifiedRaw =
                                shell(
                                    "stat -c '%y' ${quoteShellArg(fullPath)} 2>/dev/null",
                                )
                            val modified =
                                modifiedRaw
                                    .substringBefore(".")
                                    .trim()
                                    .takeIf { it.isNotBlank() }
                                    ?.let(::formatFileModifiedTime)
                                    ?: "--"

                            RemoteFileEntry(
                                name = cleanName,
                                fullPath = fullPath,
                                isDirectory = isDirectory,
                                detail = modified,
                            )
                        }
                    }.toList()
                    .awaitAll()
                    .sortedWith(compareByDescending<RemoteFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
                    .toList()

            FileBrowserSnapshot(
                isLoading = false,
                currentPath =
                    when {
                        path == "/" -> "/"
                        path.endsWith("/") -> path.dropLast(1)
                        else -> path
                    },
                entries = entries,
            )
        }
    }.getOrElse { error ->
        FileBrowserSnapshot.loading(path).copy(
            isLoading = false,
            errorMessage = error.message ?: "目录读取失败。",
        )
    }
}

private suspend fun loadProcessListSnapshot(): ProcessListSnapshot {
    val connection =
        AdbBridge.getConnection()
            ?: return ProcessListSnapshot.loading().copy(
                isLoading = false,
                errorMessage = "当前没有可用的 ADB 连接，无法读取进程列表。",
            )

    return runCatching {
        val output =
            connection
                .executeShell(
                    "ps -A -o PID,RSS,NAME 2>/dev/null || ps -A",
                    retryOnFailure = false,
                ).getOrThrow()

        val entries =
            output
                .lineSequence()
                .mapNotNull(::parseProcessLine)
                .filter { it.name.isAppProcessName() }
                .groupBy { it.name.substringBefore(':') }
                .map { (packageName, processes) ->
                    val sortedProcesses = processes.sortedByDescending { it.memoryBytes }
                    val children =
                        sortedProcesses.map { process ->
                            ProcessChildEntry(
                                name = process.name,
                                pid = process.pid,
                                memoryBytes = process.memoryBytes,
                                memory = formatProcessMemory(process.memoryBytes),
                            )
                        }
                    val totalMemoryBytes = processes.sumOf { it.memoryBytes }

                    ProcessEntry(
                        packageName = packageName,
                        appTitle = SessionManagementAppCache.appTitle(packageName, guessAppTitle(packageName)),
                        pid =
                            sortedProcesses
                                .firstOrNull { it.name == packageName }
                                ?.pid
                                ?: sortedProcesses.firstOrNull()?.pid.orEmpty(),
                        totalMemoryBytes = totalMemoryBytes,
                        memory = formatProcessMemory(totalMemoryBytes),
                        children = if (children.size > 1) children else emptyList(),
                    )
                }.sortedByDescending { it.totalMemoryBytes }
                .take(SessionManagementProcessListLimit)

        ProcessListSnapshot(
            isLoading = false,
            entries = entries,
            errorMessage = if (entries.isEmpty()) "未读取到正在运行的应用进程。" else null,
        )
    }.getOrElse { error ->
        ProcessListSnapshot.loading().copy(
            isLoading = false,
            errorMessage = error.message ?: "进程列表读取失败。",
        )
    }
}

private suspend fun loadAppDetailSnapshot(entry: AppInventoryEntry): AppDetailSnapshot {
    SessionManagementAppCache.cachedAppDetail(entry.packageName)?.let { cached ->
        return cached
    }

    val connection =
        AdbBridge.getConnection()
            ?: return AppDetailSnapshot.loading(entry).copy(
                isLoading = false,
                errorMessage = "当前没有可用的 ADB 连接，无法读取应用详情。",
            )

    suspend fun shell(command: String): String =
        connection
            .executeShell(command, retryOnFailure = false)
            .getOrNull()
            ?.trim()
            .orEmpty()

    return runCatching {
        coroutineScope {
            val pathDeferred = async { shell("pm path ${entry.packageName} | head -n 1") }
            val detailDeferred =
                async {
                    shell(
                        "dumpsys package ${entry.packageName} | grep -E 'versionName=|minSdk=|targetSdk=|firstInstallTime=|lastUpdateTime='",
                    )
                }

            val apkPath = pathDeferred.await().removePrefix("package:").trim()
            val detailMap = parseKeyValueEqualsBlock(detailDeferred.await())
            val apkSize =
                if (apkPath.isNotBlank()) {
                    shell("ls -l \"$apkPath\" | awk '{print \$5}'").toLongOrNull()?.let(::formatBytes).orEmpty()
                } else {
                    ""
                }

            AppDetailSnapshot(
                isLoading = false,
                appTitle = SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle),
                packageName = entry.packageName,
                apkSize = apkSize,
                versionName = detailMap["versionName"].orEmpty(),
                isSystemApp = entry.isSystemApp,
                minSdk = detailMap["minSdk"].orEmpty(),
                targetSdk = detailMap["targetSdk"].orEmpty(),
                firstInstallTime = detailMap["firstInstallTime"].orEmpty(),
                lastUpdateTime = detailMap["lastUpdateTime"].orEmpty(),
            ).also(SessionManagementAppCache::updateAppDetail)
        }
    }.getOrElse { error ->
        AppDetailSnapshot.loading(entry).copy(
            isLoading = false,
            errorMessage = error.message ?: "应用详情读取失败。",
        )
    }
}

internal suspend fun exportPackageApk(
    context: Context,
    packageName: String,
): Result<String> {
    val connection =
        AdbBridge.getConnection()
            ?: return Result.failure(IllegalStateException("当前没有可用的 ADB 连接。"))

    return withContext(Dispatchers.IO) {
        runCatching {
            val remotePath =
                connection
                    .executeShell("pm path $packageName | head -n 1", retryOnFailure = false)
                    .getOrThrow()
                    .removePrefix("package:")
                    .trim()
                    .ifBlank { error("未找到安装包路径") }

            val exportDir = File(context.cacheDir, "session-management/apks").apply { mkdirs() }
            val localFile = File(exportDir, "${packageName.substringAfterLast('.')}.apk")
            connection.pullFile(remotePath, localFile.absolutePath).getOrThrow()
            "安装包已导出到 ${localFile.absolutePath}"
        }
    }
}

internal fun navigateFileBrowserUp(path: String): String =
    when (path) {
        "/" -> "/sdcard"
        "/sdcard" -> "/"
        else -> parentRemotePath(path)
    }

internal fun parentRemotePath(path: String): String = path.substringBeforeLast("/", "/").ifBlank { "/" }

internal fun joinRemotePath(
    parent: String,
    child: String,
): String =
    when (parent) {
        "/" -> "/${child.trimStart('/')}"
        else -> "${parent.trimEnd('/')}/${child.trimStart('/')}"
    }

internal fun quoteShellArg(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

private fun parseKeyValueBlock(text: String): Map<String, String> =
    text
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                parts[0].trim() to parts[1].trim()
            } else {
                null
            }
        }.toMap()

private fun parseKeyValueEqualsBlock(text: String): Map<String, String> =
    Regex("""([A-Za-z][A-Za-z0-9_]+)=([^=\n]+?)(?=\s+[A-Za-z][A-Za-z0-9_]+=|$)""")
        .findAll(text)
        .associate { match ->
            match.groupValues[1].trim() to match.groupValues[2].trim()
        }

private fun formatWmValue(
    raw: String,
    prefix: String,
): String =
    raw
        .removePrefix(prefix)
        .substringAfter(": ", missingDelimiterValue = raw.removePrefix(prefix))
        .trim()

private fun parseResolution(resolution: String): Pair<Int, Int>? {
    val cleaned = resolution.substringAfterLast(": ", resolution).trim()
    val width = cleaned.substringBefore("x").trim().toIntOrNull() ?: return null
    val height = cleaned.substringAfter("x", "").trim().toIntOrNull() ?: return null
    return width to height
}

private fun parseDensityDpi(dpi: String): Int? = dpi.substringAfterLast(": ", dpi).trim().toIntOrNull()

private fun parsePhysicalDpiPair(raw: String): Pair<Double, Double>? {
    val equalsPattern = Regex("""xDpi\s*=\s*([0-9]+(?:\.[0-9]+)?)\D+yDpi\s*=\s*([0-9]+(?:\.[0-9]+)?)""")
    equalsPattern.find(raw)?.let { match ->
        val x = match.groupValues[1].toDoubleOrNull()
        val y = match.groupValues[2].toDoubleOrNull()
        if (x != null && y != null) return x to y
    }

    val tuplePattern = Regex("""\(([0-9]+(?:\.[0-9]+)?) x ([0-9]+(?:\.[0-9]+)?)\)\s*dpi""")
    tuplePattern.find(raw)?.let { match ->
        val x = match.groupValues[1].toDoubleOrNull()
        val y = match.groupValues[2].toDoubleOrNull()
        if (x != null && y != null) return x to y
    }

    return null
}

private fun formatPpi(physicalDpiPair: Pair<Double, Double>?): String {
    val (xDpi, yDpi) = physicalDpiPair ?: return ""
    val average = (xDpi + yDpi) / 2.0
    return String.format(Locale.US, "%.0f PPI", average)
}

private fun formatScreenSize(
    resolution: String,
    physicalDpiPair: Pair<Double, Double>?,
): String {
    val (width, height) = parseResolution(resolution) ?: return ""
    val (xDpi, yDpi) = physicalDpiPair ?: return ""
    if (xDpi <= 0.0 || yDpi <= 0.0) return ""
    val widthInches = width / xDpi
    val heightInches = height / yDpi
    val diagonalInches = kotlin.math.sqrt((widthInches * widthInches) + (heightInches * heightInches))
    return String.format(Locale.US, "%.2f 英寸", diagonalInches)
}

private fun formatNetworkPropertyList(raw: String): String =
    raw
        .split(",")
        .map { it.trim() }
        .filter { value ->
            value.isNotBlank() &&
                value != "Unknown" &&
                value != "unknown" &&
                value != "N/A"
        }.distinct()
        .joinToString(separator = ", ")

private fun parseSignalMetrics(raw: String): SignalMetrics {
    if (raw.isBlank()) {
        return SignalMetrics("", "", "")
    }

    fun extract(pattern: String): String =
        Regex(pattern, RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeUnless { it == "2147483647" }
            .orEmpty()

    val nrRsrp = extract("""ssRsrp\s*=\s*(-?\d+)""")
    val nrRsrq = extract("""ssRsrq\s*=\s*(-?\d+)""")
    val nrSinr = extract("""ssSinr\s*=\s*(-?\d+)""")
    if (nrRsrp.isNotBlank() || nrRsrq.isNotBlank() || nrSinr.isNotBlank()) {
        return SignalMetrics(
            rsrp = formatSignalMetric(nrRsrp, "dBm"),
            rsrq = formatSignalMetric(nrRsrq, "dB"),
            sinr = formatSinrMetric(nrSinr),
        )
    }

    return SignalMetrics(
        rsrp = formatSignalMetric(extract("""rsrp\s*=\s*(-?\d+)"""), "dBm"),
        rsrq = formatSignalMetric(extract("""rsrq\s*=\s*(-?\d+)"""), "dB"),
        sinr = formatSinrMetric(extract("""rssnr\s*=\s*(-?\d+)""")),
    )
}

private fun parseCellularIdentityMetrics(raw: String): CellularIdentityMetrics {
    if (raw.isBlank()) {
        return CellularIdentityMetrics("", "", "")
    }

    fun extract(pattern: String): String =
        Regex(pattern, RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

    return CellularIdentityMetrics(
        band =
            extract("""mBands\s*=\s*\[([^\]]+)\]""")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(", "),
        pci = extract("""mPci\s*=\s*([*\d]+)"""),
        earfcn =
            extract("""mNrArfcn\s*=\s*([*\d]+)""")
                .ifBlank {
                    extract("""mEarfcn\s*=\s*([*\d]+)""")
                },
    )
}

private fun parseWifiMetrics(raw: String): WifiMetrics {
    if (raw.isBlank()) {
        return WifiMetrics("", "", "", "")
    }

    fun extract(pattern: String): String =
        Regex(pattern, RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

    val ssid =
        Regex("""SSID:\s*"?(.*?)(?:"?\s*,|\s+BSSID:|$)""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

    return WifiMetrics(
        ssid =
            when {
                ssid.equals("<unknown ssid>", ignoreCase = true) -> ""
                ssid.equals("<none>", ignoreCase = true) -> ""
                ssid.equals("null", ignoreCase = true) -> ""
                else -> ssid
            },
        bssid = extract("""BSSID:\s*([^,]+)"""),
        frequency = extract("""Frequency:\s*([0-9]+)\s*MHz""").let { if (it.isNotBlank()) "$it MHz" else "" },
        linkSpeed = extract("""Link speed:\s*([0-9]+)\s*Mbps""").let { if (it.isNotBlank()) "$it Mbps" else "" },
    )
}

private fun formatCarrierNames(raw: String): String =
    raw
        .split(",")
        .map { it.trim() }
        .filter { value ->
            value.isNotBlank() &&
                value != "unknown" &&
                value != "Unknown" &&
                value != "(unknown)"
        }.distinct()
        .joinToString(separator = ", ")

private fun formatMobileBandSummary(
    networkType: String,
    band: String,
): String =
    when {
        band.isBlank() -> {
            ""
        }

        networkType.contains("NR", ignoreCase = true) -> {
            val normalized =
                band
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(", ") { "n$it" }
            "$networkType $normalized"
        }

        band.startsWith("B", ignoreCase = true) -> {
            "$networkType $band".trim()
        }

        networkType.isBlank() -> {
            band
        }

        else -> {
            val normalized =
                band
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(", ") { "B$it" }
            "$networkType $normalized"
        }
    }

private fun formatWifiSummary(
    frequency: String,
    linkSpeed: String,
): String =
    listOf(frequency, linkSpeed)
        .filter { it.isNotBlank() }
        .joinToString(" / ")

private fun formatWifiSsid(raw: String): String {
    val value =
        Regex("""SSID:\s*"?(.*?)(?:"?\s*,|\s+BSSID:|$)""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
    return when {
        value.isBlank() -> ""
        value.equals("<unknown ssid>", ignoreCase = true) -> ""
        value.equals("<none>", ignoreCase = true) -> ""
        value.equals("null", ignoreCase = true) -> ""
        else -> value
    }
}

private fun formatRefreshRate(raw: String): String {
    val rate =
        Regex("""renderFrameRate\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?: return ""

    return String.format(Locale.US, "%.0f Hz", rate)
}

private fun formatSupportedRefreshRates(raw: String): String {
    val directRates =
        Regex("""supportedRefreshRates\s*\[([^\]]+)\]""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.split(",")
            ?.mapNotNull { it.trim().toDoubleOrNull() }
            ?.map { String.format(Locale.US, "%.0f", it) }
            ?.distinct()
            .orEmpty()

    if (directRates.isNotEmpty()) {
        return directRates.joinToString("/") + " Hz"
    }

    val modeRates =
        Regex("""fps=([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
            .findAll(raw)
            .mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }
            .map { String.format(Locale.US, "%.0f", it) }
            .distinct()
            .toList()

    return if (modeRates.isEmpty()) "" else modeRates.joinToString("/") + " Hz"
}

private fun formatDisplaySummary(
    resolution: String,
    refreshRate: String,
): String =
    listOf(resolution, refreshRate)
        .filter { it.isNotBlank() }
        .joinToString(" / ")

private fun formatScreenMetricsSummary(
    dpi: String,
    ppi: String,
    screenSize: String,
): String =
    listOf(formatDpiLabel(dpi), ppi, screenSize)
        .filter { it.isNotBlank() }
        .joinToString("/")

private fun formatDpiLabel(raw: String): String =
    when {
        raw.isBlank() -> ""
        raw.contains("dpi", ignoreCase = true) -> raw
        else -> "$raw DPI"
    }

private fun formatBatterySummary(
    health: String,
    voltage: String,
    current: String,
): String =
    listOf(voltage, current, health)
        .filter { it.isNotBlank() }
        .joinToString(" / ")

private fun formatBatteryStatusSummary(
    status: String,
    level: String,
    temperature: String,
): String =
    listOf(level, status, temperature)
        .filter { it.isNotBlank() }
        .joinToString(" / ")

private fun formatSocModel(
    socModel: String,
    board: String,
): String =
    socModel
        .ifBlank { board }
        .trim()

private fun formatBasebandVersion(raw: String): String =
    raw
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" / ")

private fun formatWifiIpAddress(raw: String): String =
    Regex("""inet\s+([0-9.]+)\/""", RegexOption.IGNORE_CASE)
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()

private fun formatBatteryCycleCount(raw: String): String = raw.trim().takeIf { it.isNotBlank() && it != "0" } ?: ""

private fun formatStorageSummary(dfLine: String): String {
    val parts = dfLine.trim().split(Regex("\\s+"))
    if (parts.size < 4) return ""
    val totalKb = parts.getOrNull(1)?.toLongOrNull() ?: return ""
    val availableKb = parts.getOrNull(3)?.toLongOrNull() ?: return ""
    val availableBytes = availableKb * 1024
    val totalBytes = totalKb * 1024
    return "${
        formatBytes(
            availableBytes,
        )
    } / ${formatBytes(totalBytes)}${formatAvailablePercent(availableBytes, totalBytes)}"
}

private fun formatMemorySummary(
    available: String,
    total: String,
): String =
    when {
        available.isNotBlank() && total.isNotBlank() -> {
            val availableBytes = parseDisplayBytes(available)
            val totalBytes = parseDisplayBytes(total)
            "$available / $total${formatAvailablePercent(availableBytes, totalBytes)}"
        }

        available.isNotBlank() -> {
            available
        }

        else -> {
            total
        }
    }

private fun formatAvailablePercent(
    availableBytes: Long?,
    totalBytes: Long?,
): String {
    if (availableBytes == null || totalBytes == null || totalBytes <= 0L) {
        return ""
    }

    val percent = (availableBytes.toDouble() / totalBytes.toDouble()) * 100.0
    return String.format(Locale.US, " (%.0f%%)", percent)
}

private fun formatSignalMetric(
    value: String,
    unit: String,
): String = value.takeIf { it.isNotBlank() }?.let { "$it $unit" }.orEmpty()

private fun formatSinrMetric(value: String): String {
    val raw = value.toIntOrNull() ?: return ""
    val display =
        if (kotlin.math.abs(raw) >= 100) {
            String.format(Locale.US, "%.1f", raw / 10.0)
        } else {
            raw.toString()
        }
    return "$display dB"
}

private fun formatUptime(raw: String): String {
    val seconds =
        raw
            .substringBefore(" ")
            .trim()
            .toDoubleOrNull()
            ?.toLong() ?: return ""
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60

    return when {
        days > 0 -> "${days}天 ${hours}小时"
        hours > 0 -> "${hours}小时 ${minutes}分钟"
        else -> "${minutes}分钟"
    }
}

private fun formatMemValue(raw: String?): String {
    val kb = raw?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull() ?: return ""
    return formatBytes(kb * 1024)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f G", gb)
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return String.format("%.2f G", gb)
}

private fun parseDisplayBytes(text: String): Long? {
    val value = text.substringBefore(" ").toDoubleOrNull() ?: return null
    return when {
        text.contains("GB", ignoreCase = true) -> (value * 1024 * 1024 * 1024).toLong()
        text.contains("MB", ignoreCase = true) -> (value * 1024 * 1024).toLong()
        else -> null
    }
}

private fun parseProcessLine(line: String): RawProcessEntry? {
    val tokens = line.trim().split(Regex("\\s+"))
    if (tokens.size < 3 || tokens.first().equals("PID", ignoreCase = true)) return null

    val compactPid = tokens.getOrNull(0)?.takeIf { it.all(Char::isDigit) }
    val compactRssKb = tokens.getOrNull(1)?.toLongOrNull()
    val compactName = tokens.drop(2).joinToString(" ").trim()
    if (compactPid != null && compactRssKb != null && compactName.isNotBlank()) {
        return RawProcessEntry(
            name = compactName,
            pid = compactPid,
            memoryBytes = compactRssKb * 1024,
        )
    }

    val defaultPid = tokens.getOrNull(1)?.takeIf { it.all(Char::isDigit) } ?: return null
    val defaultRssKb = tokens.getOrNull(4)?.toLongOrNull() ?: return null
    val defaultName = tokens.lastOrNull()?.trim().orEmpty()
    if (defaultName.isBlank()) return null

    return RawProcessEntry(
        name = defaultName,
        pid = defaultPid,
        memoryBytes = defaultRssKb * 1024,
    )
}

private fun String.isAppProcessName(): Boolean {
    val basePackage = substringBefore(':')
    return basePackage.contains('.') &&
        !basePackage.contains('/') &&
        !basePackage.startsWith("[") &&
        basePackage.any { it.isLetter() }
}

private fun formatProcessMemory(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    if (mb < 1024) {
        return String.format(Locale.US, "%.1f MB", mb)
    }
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

private fun parseCpuPercentValue(raw: String): Float =
    raw
        .trim()
        .removeSuffix("%")
        .toFloatOrNull()
        ?: 0f

private fun formatCpuPercent(value: Float): String =
    when {
        value <= 0f -> "0%"
        value < 10f -> String.format(Locale.US, "%.1f%%", value)
        else -> String.format(Locale.US, "%.0f%%", value)
    }

private fun String.shortProcessName(): String {
    val normalized = trim()
    if (normalized.isBlank()) return "--"

    val packageLike =
        normalized
            .substringAfterLast('/')
            .substringBefore(' ')
            .ifBlank { normalized }

    return packageLike
        .substringAfterLast(':')
        .takeIf { it.length >= 3 && packageLike.contains(':') }
        ?.let { "${packageLike.substringBefore(':').substringAfterLast('.')}:$it" }
        ?: packageLike.substringAfterLast('.').ifBlank { packageLike }
}

private fun mapBatteryStatus(raw: String?): String =
    when (raw) {
        "2" -> "充电中"
        "3" -> "放电中"
        "4" -> "未充电"
        "5" -> "已充满"
        else -> raw.orEmpty()
    }

private fun mapBatteryHealth(raw: String?): String =
    when (raw) {
        "2" -> "良好"
        "3" -> "过热"
        "4" -> "损坏"
        "5" -> "过压"
        "7" -> "过冷"
        else -> raw.orEmpty()
    }

private fun formatBatteryTemperature(raw: String): String {
    val temp = raw.toFloatOrNull() ?: return raw
    return String.format("%.1f °C", temp / 10f)
}

private fun formatCurrentNow(raw: String): String {
    val value = raw.toLongOrNull() ?: return raw
    val absolute = kotlin.math.abs(value).toDouble()
    val ma =
        when {
            absolute >= 1_000_000_000.0 -> absolute / 1_000_000.0
            absolute >= 10_000.0 -> absolute / 1000.0
            else -> absolute
        }
    return String.format("%.0f mA", ma)
}

private fun formatBatteryCurrent(
    dumpsysCurrentNow: String?,
    dumpsysCurrentAverage: String?,
    batteryCurrentNow: String,
    batteryCurrentAverage: String,
    sysfsCurrent: String,
): String {
    val dumpsysNowValue = dumpsysCurrentNow?.trim()?.toLongOrNull()
    if (dumpsysNowValue != null && dumpsysNowValue != 0L) {
        return formatCurrentNow(dumpsysNowValue.toString())
    }

    val dumpsysAverageValue = dumpsysCurrentAverage?.trim()?.toLongOrNull()
    if (dumpsysAverageValue != null && dumpsysAverageValue != 0L) {
        return formatCurrentNow(dumpsysAverageValue.toString())
    }

    val nowValue = batteryCurrentNow.trim().toLongOrNull()
    if (nowValue != null && nowValue != 0L) {
        return formatCurrentNow(nowValue.toString())
    }

    val averageValue = batteryCurrentAverage.trim().toLongOrNull()
    if (averageValue != null && averageValue != 0L) {
        return formatCurrentNow(averageValue.toString())
    }

    return formatCurrentNow(sysfsCurrent)
}

private fun formatBatteryVoltage(
    dumpsysVoltage: String?,
    sysfsVoltageNow: String,
): String {
    val dumpsysValue = dumpsysVoltage?.toLongOrNull()
    if (dumpsysValue != null && dumpsysValue > 0) {
        return "$dumpsysValue mV"
    }

    val sysfsValue = sysfsVoltageNow.trim().toLongOrNull() ?: return dumpsysVoltage.orEmpty()
    if (sysfsValue <= 0) return dumpsysVoltage.orEmpty()

    val mv =
        if (sysfsValue >= 100_000) {
            sysfsValue / 1000.0
        } else {
            sysfsValue.toDouble()
        }
    return String.format("%.0f mV", mv)
}

private fun parseAdbTcpPort(raw: String): Int? {
    val port = raw.trim().toIntOrNull() ?: return null
    return port.takeIf { it > 0 }
}

private fun formatCpuSummary(
    cpuCountRaw: String,
    cpuFreqRaw: String,
): String {
    val count = cpuCountRaw.toIntOrNull()
    val freqKhz = cpuFreqRaw.toLongOrNull()
    val freqText =
        if (freqKhz != null && freqKhz > 0) {
            String.format("%.2f GHz", freqKhz / 1_000_000.0)
        } else {
            ""
        }
    return when {
        count != null && freqText.isNotBlank() -> "$count 核 / $freqText"
        count != null -> "$count 核"
        else -> freqText
    }
}
