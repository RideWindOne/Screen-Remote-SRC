package com.screen.remote.android.feature.session.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.common.AppColors
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.util.compat.readAtMostBytesCompat
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.core.designsystem.component.SectionTitle
import com.screen.remote.android.infrastructure.adb.connection.AdbBridge
import com.screen.remote.android.infrastructure.adb.shell.AdbShellManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.screen.remote.android.core.designsystem.component.IOSAlertDialog as AlertDialog

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
                        result.getOrNull()?.ifBlank { ManagementTexts.text("$title 已完成。", "$title completed.") }
                            ?: (result.exceptionOrNull()?.message ?: ManagementTexts.text("$title 失败", "$title failed")),
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
            SessionManagementSection.Apps -> ManagementTexts.text("应用选项", "App options")
            SessionManagementSection.Command -> ManagementTexts.text("快捷命令", "Quick commands")
            else -> ManagementTexts.text("刷新", "Refresh")
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
                                        title = ManagementTexts.text("固定端口", "Fixed port"),
                                        label = ManagementTexts.text("端口号", "Port"),
                                        initialValue = "5555",
                                        confirmText = ManagementTexts.text("应用", "Apply"),
                                    )
                            }

                            UtilityAction.Screenshot -> {
                                progressDialog =
                                    ManagementProgressDialogState(
                                        title = ManagementTexts.text("屏幕截图", "Screenshot"),
                                        message = ManagementTexts.text("正在从目标设备获取截图并写入本机缓存目录。", "Capturing a screenshot from the device."),
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
                                                    title = ManagementTexts.text("屏幕截图", "Screenshot"),
                                                    message = error.message ?: ManagementTexts.text("截图失败", "Screenshot failed"),
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
                                        title = ManagementTexts.text("修改 DPI", "Change DPI"),
                                        label = ManagementTexts.text("DPI 数值", "DPI value"),
                                        initialValue = snapshot.currentDpiValue.orEmpty(),
                                        confirmText = ManagementTexts.text("应用", "Apply"),
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
                                        title = ManagementTexts.text("墓碑模式", "Tombstone mode"),
                                        message = ManagementTexts.text("该功能暂未实现，当前保持禁用。", "This feature isn't available yet."),
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
                contentDescription = ManagementTexts.text("新增", "Add"),
                onClick = { fileAddMenuOpenTick += 1 },
            )
        }

        if (selectedSection == SessionManagementSection.Apps && !drawerOpen) {
            SessionManagementAddFab(
                modifier = Modifier.align(Alignment.BottomEnd),
                contentDescription = ManagementTexts.text("安装应用", "Install app"),
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
            title = ManagementTexts.text("管理功能", "Management"),
            message = ManagementTexts.text("正在获取设备信息", "Loading device info"),
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
                    Text(ManagementTexts.text("确定", "OK"))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    if (rebootDialogOpen) {
        SessionManagementRebootDialog(
            onDismiss = { rebootDialogOpen = false },
            onAction = { mode ->
                rebootDialogOpen = false
                launchAction(
                    title = ManagementTexts.text("高级重启", "Advanced reboot"),
                    message = ManagementTexts.text("正在执行 ${mode.label}。", "Running ${mode.label}."),
                ) {
                    runShellAction(mode.command, successMessage = ManagementTexts.text("${mode.label}指令已发送。", "${mode.label} command sent."))
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
                    title = ManagementTexts.text("激活应用", "Activate app"),
                    message = ManagementTexts.text("正在执行 ${target.label} 激活命令。", "Running activation for ${target.label}."),
                ) {
                    runShellAction(
                        command = target.command,
                        successMessage = ManagementTexts.text("${target.label} 激活命令已发送。", "Activation command sent for ${target.label}."),
                    )
                }
            },
            onUnavailable = { label ->
                activationDialogOpen = false
                resultDialog =
                    ManagementResultDialogState(
                        title = ManagementTexts.text("激活应用", "Activate app"),
                        message = ManagementTexts.text("$label 当前未安装或暂不支持激活。", "$label isn't installed or can't be activated here."),
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
                    title = ManagementTexts.text("熄屏待机", "Screen standby"),
                    message = ManagementTexts.text("正在执行${action.label}。", "Running ${action.label}."),
                ) {
                    runShellAction(action.command, successMessage = ManagementTexts.text("${action.label}指令已发送。", "${action.label} command sent."))
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
                    title = ManagementTexts.text("修改 DPI", "Change DPI"),
                    message = ManagementTexts.text("正在应用 DPI $value。", "Applying DPI $value."),
                ) {
                    runShellAction("wm density $value", successMessage = ManagementTexts.text("DPI 已修改为 $value。", "DPI changed to $value."))
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
                            title = ManagementTexts.text("固定端口", "Fixed port"),
                            message = ManagementTexts.text("端口号无效，必须是 1024-65535 之间的数字。", "Port must be a number between 1024 and 65535."),
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
                        ManagementTexts.text("正在设置端口 $port，ADB 服务将重启...", "Setting port $port. ADB will restart..."),
                        Toast.LENGTH_LONG,
                    ).show()

                // 发送端口设置命令，不等待响应（因为会断开连接）
                scope.launch {
                    val connection = AdbBridge.getConnection()
                    if (connection == null) {
                        Toast
                            .makeText(
                                context,
                                ManagementTexts.text("当前没有可用的 ADB 连接", "No ADB connection is available"),
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
                    title = ManagementTexts.text("修改分辨率", "Change resolution"),
                    message = ManagementTexts.text("正在应用 ${width}x$height。", "Applying ${width}x$height."),
                ) {
                    runShellAction("wm size ${width}x$height", successMessage = ManagementTexts.text("分辨率已修改为 ${width}x$height。", "Resolution changed to ${width}x$height."))
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
                    title = ManagementTexts.text("动画调整", "Animation scale"),
                    message = ManagementTexts.text("正在应用动画缩放参数。", "Applying animation scale values."),
                ) {
                    runShellAction(
                        command =
                            "settings put global window_animation_scale $windowScale && " +
                                "settings put global transition_animation_scale $transitionScale && " +
                                "settings put global animator_duration_scale $durationScale",
                        successMessage = ManagementTexts.text("动画缩放参数已更新。", "Animation scale values updated."),
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
                        Toast.makeText(context, ManagementTexts.text("截图已保存到相册", "Screenshot saved to gallery"), Toast.LENGTH_SHORT).show()
                    } else {
                        resultDialog =
                            ManagementResultDialogState(
                                title = ManagementTexts.text("保存截图", "Save screenshot"),
                                message = result.exceptionOrNull()?.message ?: ManagementTexts.text("保存到相册失败", "Couldn't save to gallery"),
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
private val SessionManagementProcessContentHorizontalPadding = 2.dp
private val SessionManagementContentBottomPadding = 20.dp
private val SessionManagementContentTopPadding = 110.dp
private val SessionManagementProgressSpacing = 14.dp
private val SessionManagementProgressIndicatorSize = 22.dp
private val SessionManagementTextEditorHorizontalPadding = 16.dp
private val SessionManagementTextEditorVerticalPadding = 12.dp
private val SessionManagementTextEditorTopActionSpacing = 4.dp
private val SessionManagementImagePreviewHeight = 280.dp
private val SessionManagementVideoPreviewHeight = 220.dp
private val SessionManagementBinaryPreviewHeight = 260.dp

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

    if (selectedSection == SessionManagementSection.Process) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = SessionManagementContentTopPadding,
                        bottom = SessionManagementContentBottomPadding,
                    ),
        ) {
            SessionManagementProcessPage(
                modifier = Modifier.fillMaxSize(),
                snapshot = snapshot,
                refreshToken = refreshToken,
            )
        }
        return
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

            SessionManagementSection.Process -> Unit

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
    val labelZh: String,
    val labelEn: String,
    val packageName: String,
    val command: String,
) {
    val label: String
        get() = ManagementTexts.text(labelZh, labelEn)
}

private enum class StandbyAction(
    val labelZh: String,
    val labelEn: String,
    val command: String,
) {
    Sleep("息屏", "Sleep", "input keyevent 223"),
    Wake("亮屏", "Wake", "input keyevent 224");

    val label: String
        get() = ManagementTexts.text(labelZh, labelEn)
}

private enum class RebootMode(
    val labelZh: String,
    val labelEn: String,
    val command: String,
) {
    Normal("正常重启", "Restart", "reboot"),
    Recovery("恢复模式", "Recovery", "reboot recovery"),
    Fastboot("引导模式", "Fastboot", "reboot bootloader");

    val label: String
        get() = ManagementTexts.text(labelZh, labelEn)
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
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun SessionManagementExitConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ManagementTexts.text("确认退出管理页？", "Leave management page?")) },
        text = { Text(ManagementTexts.text("返回键会离开当前设备管理页面。", "This will leave the current device management page.")) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(ManagementTexts.text("退出", "Leave"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(ManagementTexts.text("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun SessionManagementRebootDialog(
    onDismiss: () -> Unit,
    onAction: (RebootMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ManagementTexts.text("高级重启", "Advanced reboot")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RebootMode.entries.forEach { mode ->
                    Surface(
                        shape = RoundedCornerShape(SessionManagementOptionCornerRadius),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.5.dp,
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
                Text(ManagementTexts.text("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
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
                Text(ManagementTexts.text("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
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
        title = { Text(ManagementTexts.text("修改分辨率", "Change resolution")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = width,
                    onValueChange = { width = it },
                    label = { Text(ManagementTexts.text("宽度", "Width")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text(ManagementTexts.text("高度", "Height")) },
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
                Text(ManagementTexts.text("应用", "Apply"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(ManagementTexts.text("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
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
        title = { Text(ManagementTexts.text("截图已完成", "Screenshot ready")) },
        text = {
            Text(ManagementTexts.text("文件已保存到本机缓存目录：${state.file.absolutePath}", "Saved to local cache: ${state.file.absolutePath}"))
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(ManagementTexts.text("保存", "Save"))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(ManagementTexts.text("取消", "Cancel"))
                }
                TextButton(onClick = onOpen) {
                    Text(ManagementTexts.text("打开", "Open"))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
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
        title = { Text(ManagementTexts.text("动画调整", "Animation scale")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = windowScale,
                    onValueChange = { windowScale = it },
                    label = { Text(ManagementTexts.text("窗口动画缩放倍数", "Window animation scale")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = transitionScale,
                    onValueChange = { transitionScale = it },
                    label = { Text(ManagementTexts.text("过渡动画缩放倍数", "Transition animation scale")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = durationScale,
                    onValueChange = { durationScale = it },
                    label = { Text(ManagementTexts.text("动画时长缩放倍数", "Animator duration scale")) },
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
                Text(ManagementTexts.text("应用", "Apply"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(ManagementTexts.text("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun SessionManagementStandbyDialog(
    onDismiss: () -> Unit,
    onAction: (StandbyAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ManagementTexts.text("熄屏待机", "Screen standby")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StandbyAction.entries.forEach { action ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.5.dp,
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
                Text(ManagementTexts.text("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
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
        title = { Text(ManagementTexts.text("激活应用", "Activate app")) },
        text = {
            when {
                appInventory.isLoading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(ManagementTexts.text("正在加载可激活应用列表。", "Loading available activation apps."))
                    }
                }

                appInventory.errorMessage != null -> {
                    Text(appInventory.errorMessage ?: ManagementTexts.text("读取应用列表失败。", "Couldn't load app list."))
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
                Text(ManagementTexts.text("取消", "Cancel"))
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
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
            },
        tonalElevation = 0.5.dp,
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
                text = if (installed) ManagementTexts.text("已安装", "Installed") else ManagementTexts.text("未安装", "Not installed"),
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
            ?: return Result.failure(IllegalStateException(ManagementTexts.text("当前没有可用的 ADB 连接。", "No ADB connection is available.")))

    return AdbShellManager
        .execute(connection = connection, command = command, retryOnFailure = false)
        .map { output ->
            output.trim().ifBlank { successMessage }
        }
}

private suspend fun captureDeviceScreenshot(context: Context): Result<File> {
    val connection =
        AdbBridge.getConnection()
            ?: return Result.failure(IllegalStateException(ManagementTexts.text("当前没有可用的 ADB 连接。", "No ADB connection is available.")))

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
                error(ManagementTexts.text("截图文件为空。", "The screenshot file is empty."))
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
        Toast.makeText(context, ManagementTexts.text("无法打开截图预览", "Couldn't open the screenshot preview"), Toast.LENGTH_SHORT).show()
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
                    ?: error(ManagementTexts.text("无法创建相册文件", "Couldn't create a gallery file"))

            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error(ManagementTexts.text("无法写入相册文件", "Couldn't write the gallery file"))

            ManagementTexts.text("截图已保存到相册。", "Screenshot saved to gallery.")
        }
    }

internal suspend fun prepareRemoteFileForLocalOpen(
    context: Context,
    entry: RemoteFileEntry,
): Result<File> {
    val connection =
        AdbBridge.getConnection()
            ?: return Result.failure(IllegalStateException(ManagementTexts.text("当前没有可用的 ADB 连接。", "No ADB connection is available.")))

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
            error(ManagementTexts.text("当前文件不像文本文件，请改用专门预览或外部打开。", "This file doesn't look like text. Use preview or open externally instead."))
        }
        if (localFile.length() > 512 * 1024L) {
            error(ManagementTexts.text("简易编辑器暂只支持 512 KB 以内文本文件。", "The built-in editor only supports text files up to 512 KB."))
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
            ?: return Result.failure(IllegalStateException(ManagementTexts.text("当前没有可用的 ADB 连接。", "No ADB connection is available.")))

    return withContext(Dispatchers.IO) {
        runCatching {
            state.localFile.writeText(content, Charsets.UTF_8)
            connection.pushFile(state.localFile.absolutePath, state.entry.fullPath).getOrThrow()
            ManagementTexts.text("文件已保存并回写到设备。", "File saved and pushed back to the device.")
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
        context.startActivity(Intent.createChooser(intent, ManagementTexts.text("打开文件", "Open file")))
        ManagementTexts.text("已调用外部程序打开本机临时文件。", "Opened the local temp file with an external app.")
    }

internal suspend fun pushPreparedLocalFileToDevice(
    context: Context,
    entry: RemoteFileEntry,
): Result<String> {
    val connection =
        AdbBridge.getConnection()
            ?: return Result.failure(IllegalStateException(ManagementTexts.text("当前没有可用的 ADB 连接。", "No ADB connection is available.")))

    return withContext(Dispatchers.IO) {
        runCatching {
            val localFile = getPreparedLocalFile(context, entry)
            require(localFile.exists()) { ManagementTexts.text("当前没有可回写的本机副本，请先打开或预览该文件。", "No local copy is available to push back yet.") }
            connection.pushFile(localFile.absolutePath, entry.fullPath).getOrThrow()
            ManagementTexts.text("本机副本已回写到设备。", "Local copy pushed back to the device.")
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
            ?: return ManagementTexts.text("无法读取二进制预览。", "Couldn't read the binary preview.")
    if (bytes.isEmpty()) return ManagementTexts.text("空文件", "Empty file")

    return bytes
        .toList()
        .chunked(16)
        .mapIndexed { index, chunk ->
            val address = (index * 16).toString(16).padStart(4, '0')
            val hex = chunk.joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
            "$address  $hex"
        }.joinToString(separator = "\n")
}

internal suspend fun loadRemoteFileDetailSnapshot(entry: RemoteFileEntry): RemoteFileDetailSnapshot {
    val connection =
        AdbBridge.getConnection()
            ?: return RemoteFileDetailSnapshot.loading(entry).copy(
                isLoading = false,
                errorMessage = ManagementTexts.text("当前没有可用的 ADB 连接，无法读取文件详情。", "No ADB connection is available, so file details can't be loaded."),
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
                typeLabel = if (entry.isDirectory) ManagementTexts.text("文件夹", "Folder") else ManagementTexts.text("文件", "File"),
                permissions = parts.getOrNull(0)?.trim().orEmpty(),
                owner = parts.getOrNull(1)?.trim().orEmpty(),
                group = parts.getOrNull(2)?.trim().orEmpty(),
                sizeLabel = sizeBytes?.let(::formatFileSize) ?: "--",
                modifiedTime = modified,
            )
        } else {
            val lsOutput = shell("ls -ld ${quoteShellArg(entry.fullPath)}")
            val parsed = parseLsDetailLine(lsOutput, entry)
            parsed ?: error(ManagementTexts.text("无法解析文件详情", "Couldn't parse file details"))
        }
    }.getOrElse { error ->
        RemoteFileDetailSnapshot.loading(entry).copy(
            isLoading = false,
            errorMessage = error.message ?: ManagementTexts.text("读取文件详情失败。", "Couldn't load file details."),
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
        typeLabel = if (entry.isDirectory) ManagementTexts.text("文件夹", "Folder") else ManagementTexts.text("文件", "File"),
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
                        AppColors.iOSBlue.copy(alpha = 0.1f)
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
                    AppColors.iOSBlue.copy(alpha = 0.12f)
                } else {
                    managementSubtleFillColor()
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
                tint = if (entry.isDirectory) AppColors.iOSBlue else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    text = if (entry.isDirectory) ManagementTexts.text("文件夹", "Folder") else ManagementTexts.text("文件", "File"),
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
                contentDescription = if (selected) ManagementTexts.text("已选中", "Selected") else ManagementTexts.text("未选中", "Not selected"),
                tint = if (selected) AppColors.iOSBlue else MaterialTheme.colorScheme.outline,
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
                Text(ManagementTexts.text("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
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
                Text(ManagementTexts.text("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
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
        title = { Text(ManagementTexts.text("安装应用", "Install app")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SessionManagementActionRow(
                    icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                    label = ManagementTexts.text("选择apk文件", "Choose APK file"),
                    onClick = onPickApk,
                )
                SessionManagementActionRow(
                    icon = Icons.Default.Apps,
                    label = ManagementTexts.text("选择已安装应用", "Choose installed app"),
                    onClick = onPickInstalledApp,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(ManagementTexts.text("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
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
            title = ManagementTexts.text("设备信息读取失败", "Couldn't load device info"),
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
            title = ManagementTexts.text("系统与硬件", "System and hardware"),
            items =
                listOf(
                    ManagementTexts.text("品牌型号", "Brand and model") to snapshot.brandModelLabel,
                    "SOC" to snapshot.socModel,
                    ManagementTexts.text("安卓版本", "Android version") to snapshot.androidVersionLabel,
                    ManagementTexts.text("开机时长", "Uptime") to snapshot.uptime,
                    ManagementTexts.text("基带版本", "Baseband") to snapshot.basebandVersion,
                    ManagementTexts.text("产品代号", "Product codename") to snapshot.productCodeName,
                    ManagementTexts.text("安全补丁", "Security patch") to snapshot.securityPatch,
                    ManagementTexts.text("序列号", "Serial number") to snapshot.serialNumber,
                    ManagementTexts.text("处理器", "CPU") to snapshot.cpuSummary,
                    "ABI" to snapshot.abi,
                    ManagementTexts.text("主板", "Board") to snapshot.board,
                ),
        )

        SessionManagementInfoGroup(
            title = ManagementTexts.text("显示与能耗", "Display and power"),
            items =
                listOf(
                    ManagementTexts.text("显示", "Display") to formatDisplaySummary(snapshot.resolution, snapshot.refreshRate),
                    ManagementTexts.text("屏幕", "Screen") to formatScreenMetricsSummary(snapshot.dpi, snapshot.ppi, snapshot.screenSize),
                    ManagementTexts.text("刷新率列表", "Refresh rates") to snapshot.supportedRefreshRates,
                    ManagementTexts.text("电池", "Battery") to formatBatterySummary(snapshot.batteryHealth, snapshot.voltage, snapshot.currentNow),
                    ManagementTexts.text("状态", "Status") to
                        formatBatteryStatusSummary(snapshot.batteryStatus, snapshot.batteryLevel, snapshot.temperature),
                    ManagementTexts.text("循环次数", "Cycle count") to snapshot.batteryCycleCount,
                ),
        )

        SessionManagementInfoGroup(
            title = ManagementTexts.text("存储与内存", "Storage and memory"),
            items =
                listOf(
                    ManagementTexts.text("存储空间", "Storage") to snapshot.storageSummary,
                    ManagementTexts.text("运行内存", "Memory") to formatMemorySummary(snapshot.memoryAvailable, snapshot.memoryTotal),
                ),
        )

        SessionManagementInfoGroup(
            title = ManagementTexts.text("网络", "Network"),
            items =
                listOf(
                    ManagementTexts.text("蜂窝网络", "Cellular") to formatMobileBandSummary(snapshot.mobileNetworkType, snapshot.mobileBand),
                    ManagementTexts.text("运营商", "Carrier") to snapshot.carrierNames,
                    "PCI" to snapshot.mobilePci,
                    "EARFCN" to snapshot.mobileEarfcn,
                    "RSRP" to snapshot.rsrp,
                    "RSRQ" to snapshot.rsrq,
                    "SINR" to snapshot.sinr,
                    ManagementTexts.text("无线SSID", "Wi-Fi SSID") to snapshot.wifiSsid,
                    "WLAN IP" to snapshot.wifiIpAddress,
                    ManagementTexts.text("Wi‑Fi 信息", "Wi-Fi info") to formatWifiSummary(snapshot.wifiFrequency, snapshot.wifiLinkSpeed),
                    "BSSID" to snapshot.wifiBssid,
                ),
        )

        if (snapshot.fingerprint.isNotBlank()) {
            SessionManagementInfoGroup(
                title = ManagementTexts.text("系统标识", "System identity"),
                items = listOf("Fingerprint" to snapshot.fingerprint),
            )
        }
    }
}

@Composable
private fun SessionManagementHomeLoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SessionManagementInfoLoadingGroup(
            title = ManagementTexts.text("系统与硬件", "System and hardware"),
            labels =
                listOf(
                    ManagementTexts.text("品牌型号", "Brand and model"),
                    "SOC",
                    ManagementTexts.text("安卓版本", "Android version"),
                    ManagementTexts.text("开机时长", "Uptime"),
                    ManagementTexts.text("基带版本", "Baseband"),
                    ManagementTexts.text("产品代号", "Product codename"),
                    ManagementTexts.text("安全补丁", "Security patch"),
                    ManagementTexts.text("序列号", "Serial number"),
                    ManagementTexts.text("处理器", "CPU"),
                    "ABI",
                    ManagementTexts.text("主板", "Board"),
                ),
        )
        SessionManagementInfoLoadingGroup(
            title = ManagementTexts.text("显示与能耗", "Display and power"),
            labels = listOf(
                ManagementTexts.text("显示", "Display"),
                ManagementTexts.text("屏幕", "Screen"),
                ManagementTexts.text("刷新率列表", "Refresh rates"),
                ManagementTexts.text("电池", "Battery"),
                ManagementTexts.text("状态", "Status"),
                ManagementTexts.text("循环次数", "Cycle count"),
            ),
        )
        SessionManagementInfoLoadingGroup(
            title = ManagementTexts.text("存储与内存", "Storage and memory"),
            labels = listOf(ManagementTexts.text("存储空间", "Storage"), ManagementTexts.text("运行内存", "Memory")),
        )
        SessionManagementInfoLoadingGroup(
            title = ManagementTexts.text("网络", "Network"),
            labels =
                listOf(
                    ManagementTexts.text("蜂窝网络", "Cellular"),
                    ManagementTexts.text("运营商", "Carrier"),
                    "PCI",
                    "EARFCN",
                    "RSRP",
                    "RSRQ",
                    "SINR",
                    ManagementTexts.text("无线SSID", "Wi-Fi SSID"),
                    "WLAN IP",
                    ManagementTexts.text("Wi-Fi 信息", "Wi-Fi info"),
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
            color = managementPanelColor(),
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
internal fun SessionManagementInfoPlaceholderRow(
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
            color = managementPanelColor(),
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
internal fun SessionManagementInfoRow(
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
internal fun SessionManagementNoteCard(
    title: String,
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(AppDimens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.5.dp,
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
                color = MaterialTheme.colorScheme.onSurface,
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
internal fun SessionManagementDialogCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(SessionManagementCardCornerRadius),
        color = sessionManagementDialogCardColor(),
    ) {
        content()
    }
}


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

internal data class RemoteFileDetailSnapshot(
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
                typeLabel = if (entry.isDirectory) ManagementTexts.text("文件夹", "Folder") else ManagementTexts.text("文件", "File"),
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
        override val title: String = ManagementTexts.text("确认回写设备？", "Push local copy back?")
        override val message: String = ManagementTexts.text("会用本机副本直接覆盖设备上的 ${entry.fullPath}", "This will overwrite ${entry.fullPath} on the device.")
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

private fun supportedActivationTargets(installedPackages: Set<String>): List<ActivationTarget> =
    listOfNotNull(
        activationTargetIfInstalled(
            labelZh = "Shizuku",
            labelEn = "Shizuku",
            packageName = "moe.shizuku.privileged.api",
            command = "sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh",
            installedPackages = installedPackages,
        ),
        activationTargetIfInstalled(
            labelZh = "黑域",
            labelEn = "Brevent",
            packageName = "me.piebridge.brevent",
            command = "sh /data/data/me.piebridge.brevent/brevent.sh",
            installedPackages = installedPackages,
        ),
        activationTargetIfInstalled(
            labelZh = "冰箱",
            labelEn = "Ice Box",
            packageName = "com.catchingnow.icebox",
            command = "sh /storage/emulated/0/Android/data/com.catchingnow.icebox/files/start.sh",
            installedPackages = installedPackages,
        ),
    ).ifEmpty {
        listOf(
            ActivationTarget(
                labelZh = "Shizuku",
                labelEn = "Shizuku",
                packageName = "moe.shizuku.privileged.api",
                command = "",
            ),
            ActivationTarget(
                labelZh = "黑域",
                labelEn = "Brevent",
                packageName = "me.piebridge.brevent",
                command = "",
            ),
            ActivationTarget(
                labelZh = "冰箱",
                labelEn = "Ice Box",
                packageName = "com.catchingnow.icebox",
                command = "",
            ),
        )
    }

private fun activationTargetIfInstalled(
    labelZh: String,
    labelEn: String,
    packageName: String,
    command: String,
    installedPackages: Set<String>,
): ActivationTarget =
    ActivationTarget(
        labelZh = labelZh,
        labelEn = labelEn,
        packageName = packageName,
        command = if (packageName in installedPackages) command else "",
    )

internal suspend fun loadFileBrowserSnapshot(path: String): FileBrowserSnapshot {
    val connection =
        AdbBridge.getConnection()
            ?: return FileBrowserSnapshot.loading(path).copy(
                isLoading = false,
                errorMessage = ManagementTexts.text("当前没有可用的 ADB 连接，无法读取目录。", "No ADB connection is available, so the folder can't be loaded."),
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
            errorMessage = error.message ?: ManagementTexts.text("目录读取失败。", "Couldn't load folder."),
        )
    }
}

internal suspend fun exportPackageApk(
    context: Context,
    packageName: String,
): Result<String> {
    val connection =
        AdbBridge.getConnection()
            ?: return Result.failure(IllegalStateException(ManagementTexts.text("当前没有可用的 ADB 连接。", "No ADB connection is available.")))

    return withContext(Dispatchers.IO) {
        runCatching {
            val remotePath =
                connection
                    .executeShell("pm path $packageName | head -n 1", retryOnFailure = false)
                    .getOrThrow()
                    .removePrefix("package:")
                    .trim()
                    .ifBlank { error(ManagementTexts.text("未找到安装包路径", "Couldn't find the APK path")) }

            val exportDir = File(context.cacheDir, "session-management/apks").apply { mkdirs() }
            val localFile = File(exportDir, "${packageName.substringAfterLast('.')}.apk")
            connection.pullFile(remotePath, localFile.absolutePath).getOrThrow()
            ManagementTexts.text("安装包已导出到 ${localFile.absolutePath}", "APK exported to ${localFile.absolutePath}")
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
