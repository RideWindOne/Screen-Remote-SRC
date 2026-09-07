package com.screen.remote.android.feature.remote.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.util.FilePickerHelper
import com.screen.remote.android.core.common.util.LocalDisplaySpec
import com.screen.remote.android.core.common.util.resolveLocalDisplaySpec
import com.screen.remote.android.core.data.datastore.PreferencesManager
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.data.repository.SessionRepository
import com.screen.remote.android.core.designsystem.component.MessageItem
import com.screen.remote.android.core.designsystem.component.MessageListState
import com.screen.remote.android.core.designsystem.component.rememberMessageListState
import com.screen.remote.android.core.domain.model.AppSettings
import com.screen.remote.android.core.domain.model.ConnectionProgress
import com.screen.remote.android.core.domain.model.ScreenRotationPolicy
import com.screen.remote.android.core.domain.model.getDisplayText
import com.screen.remote.android.core.domain.model.getIcon
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.feature.remote.input.RemoteHardwareKeyEventHandler
import com.screen.remote.android.feature.remote.input.RemoteHardwareKeyEventHost
import com.screen.remote.android.feature.remote.model.RemoteUiLayoutNode
import com.screen.remote.android.feature.remote.model.RemoteUiLayoutSnapshot
import com.screen.remote.android.feature.remote.presentation.ConnectStatus
import com.screen.remote.android.feature.remote.presentation.ConnectionViewModel
import com.screen.remote.android.feature.remote.presentation.ControlViewModel
import com.screen.remote.android.feature.remote.presentation.RemoteFileSendResult
import com.screen.remote.android.feature.remote.presentation.VideoDecoderManager
import com.screen.remote.android.feature.remote.presentation.rememberAudioDecoderManager
import com.screen.remote.android.feature.remote.presentation.rememberVideoDecoderManager
import com.screen.remote.android.feature.remote.ui.internal.RemoteLayoutInspectorOverlay
import com.screen.remote.android.feature.remote.widget.connection.ConnectionActionOverlay
import com.screen.remote.android.feature.remote.widget.connection.ConnectionStateOverlay
import com.screen.remote.android.feature.remote.widget.floating.AutoFloatingMenu
import com.screen.remote.android.feature.remote.widget.floating.FloatingMenuActions
import com.screen.remote.android.feature.remote.widget.touch.KeyboardInputHandler
import com.screen.remote.android.feature.remote.widget.video.RemotePerformanceStatsOverlay
import com.screen.remote.android.feature.remote.widget.video.VideoDisplayArea
import com.screen.remote.android.feature.session.viewmodel.MainViewModel
import com.screen.remote.android.feature.settings.viewmodel.SettingsViewModel
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.adb.connection.AdbDisplayInfo
import com.screen.remote.android.infrastructure.adb.mdns.MdnsSessionDiscoveryManager
import com.screen.remote.android.infrastructure.media.audio.AudioStream
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionState
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoStream
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderResolutionRecoveryRequest
import com.screen.remote.android.infrastructure.scrcpy.session.model.VideoResolutionRecoverySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private data class RemoteLayoutInspectorUiState(
    val isLoading: Boolean,
    val isOverlayVisible: Boolean,
    val isTargetKeyboardVisible: Boolean,
    val snapshot: RemoteUiLayoutSnapshot?,
    val nodes: List<RemoteUiLayoutNode>,
)

internal enum class RemoteScreenOrientation {
    PORTRAIT,
    LANDSCAPE,
}

internal fun isDisplayAdaptedToLocalDevice(
    target: AdbDisplayInfo,
    local: LocalDisplaySpec,
): Boolean =
    target.currentWidth == local.width &&
        target.currentHeight == local.height &&
        target.currentDensityDpi == local.densityDpi

internal fun remoteScreenOrientation(
    width: Int,
    height: Int,
): RemoteScreenOrientation? =
    when {
        width <= 0 || height <= 0 || width == height -> null
        width > height -> RemoteScreenOrientation.LANDSCAPE
        else -> RemoteScreenOrientation.PORTRAIT
    }

internal fun requestedOrientationForRemoteScreen(orientation: RemoteScreenOrientation?): Int? =
    when (orientation) {
        RemoteScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        RemoteScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        null -> null
    }

internal fun requestedOrientationForRotationPolicy(
    policy: ScreenRotationPolicy,
    remoteOrientation: RemoteScreenOrientation?,
    originalRequestedOrientation: Int,
): Int? =
    when (policy) {
        ScreenRotationPolicy.NONE -> originalRequestedOrientation
        ScreenRotationPolicy.LOCAL -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        ScreenRotationPolicy.TARGET -> requestedOrientationForRemoteScreen(remoteOrientation)
    }

internal fun localScreenOrientation(configurationOrientation: Int): RemoteScreenOrientation? =
    when (configurationOrientation) {
        Configuration.ORIENTATION_LANDSCAPE -> RemoteScreenOrientation.LANDSCAPE
        Configuration.ORIENTATION_PORTRAIT -> RemoteScreenOrientation.PORTRAIT
        else -> null
    }

internal fun shouldRotateTargetForLocalPolicy(
    policy: ScreenRotationPolicy,
    localOrientation: RemoteScreenOrientation?,
    remoteOrientation: RemoteScreenOrientation?,
): Boolean =
    policy == ScreenRotationPolicy.LOCAL &&
        localOrientation != null &&
        remoteOrientation != null &&
        localOrientation != remoteOrientation

internal fun requestedOrientationAfterRemoteSession(originalRequestedOrientation: Int): Int =
    if (originalRequestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
        ActivityInfo.SCREEN_ORIENTATION_USER
    } else {
        originalRequestedOrientation
    }

internal fun shouldCancelConnectionOnBack(
    connectionState: ConnectionState,
    connectStatus: ConnectStatus,
): Boolean =
    connectionState !is ConnectionState.Connected &&
        (connectionState is ConnectionState.Connecting ||
            connectionState is ConnectionState.Reconnecting ||
            connectStatus is ConnectStatus.Connecting)

internal fun shouldInterceptRemoteBack(
    connectionState: ConnectionState,
    connectStatus: ConnectStatus,
): Boolean =
    connectionState is ConnectionState.Connected || shouldCancelConnectionOnBack(connectionState, connectStatus)

internal fun connectionStateForRemoteOverlay(
    connectionState: ConnectionState,
    compatibilityMode: Boolean,
    compatibilityFrameAvailable: Boolean,
): ConnectionState =
    if (
        compatibilityMode &&
        !compatibilityFrameAvailable &&
        connectionState is ConnectionState.Connected
    ) {
        ConnectionState.Connecting
    } else {
        connectionState
    }

private data class RemoteDisplayScreenRouteState(
    val videoStream: VideoStream?,
    val compatibilityFrame: Bitmap?,
    val audioStream: AudioStream?,
    val connectionState: ConnectionState,
    val connectStatus: ConnectStatus,
    val connectionProgress: List<ConnectionProgress>,
    val decoderResolutionRecoveryRequest: DecoderResolutionRecoveryRequest?,
    val settings: AppSettings,
    val sessionData: SessionData?,
    val messageListState: MessageListState,
    val videoDecoderManager: VideoDecoderManager,
    val floatingMenuActions: FloatingMenuActions,
    val uploadPickerRequestToken: Int,
    val keyboardRequestToken: Int,
    val layoutInspectorState: RemoteLayoutInspectorUiState,
    val sendSelectedFile: (Uri) -> Unit,
    val refreshLayoutInspectorOverlay: () -> Unit,
    val hideLayoutInspectorOverlay: () -> Unit,
    val showKeyboardInput: Boolean,
    val onKeyboardInputVisibleChange: (Boolean) -> Unit,
    val surfaceHolder: SurfaceHolder?,
    val onSurfaceHolderChanged: (SurfaceHolder?) -> Unit,
    val renderSurface: Surface?,
    val onRenderSurfaceChanged: (Surface?) -> Unit,
    val lifecycleState: Lifecycle.Event,
    val onLifecycleStateChanged: (Lifecycle.Event) -> Unit,
    val videoAspectRatio: Float,
    val videoWidth: Int,
    val videoHeight: Int,
    val onVideoMetricsChanged: (Int, Int, Float) -> Unit,
    val showPatternLockDialog: androidx.compose.runtime.MutableState<Boolean>,
    val usePatternLockCache: androidx.compose.runtime.MutableState<Boolean>,
)

@SuppressLint("ClickableViewAccessibility", "ConfigurationScreenWidthHeight")
@Composable
fun RemoteDisplayScreen(
    sessionId: String,
    mainViewModel: MainViewModel,
    onClose: () -> Unit,
    onBackToApp: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sessionRepository = remember { SessionRepository(context) }
    val appContext = context.applicationContext
    val adbConnectionManager = remember(appContext) { AdbConnectionManager.getInstance(appContext) }
    val preferencesManager = remember { PreferencesManager(context) }

    val scrcpyClient = mainViewModel.scrcpyClient
    val connectionViewModel = mainViewModel.connectionViewModel
    val controlViewModel: ControlViewModel =
        viewModel(
            factory = ControlViewModel.provideFactory(scrcpyClient, adbConnectionManager),
        )
    val settingsViewModel: SettingsViewModel =
        viewModel(
            factory = SettingsViewModel.provideFactory(preferencesManager),
        )

    val routeState =
        rememberRemoteDisplayScreenRouteState(
            context = context,
            sessionId = sessionId,
            sessionRepository = sessionRepository,
            controlViewModel = controlViewModel,
            connectionViewModel = connectionViewModel,
            settingsViewModel = settingsViewModel,
            onBackToApp = onBackToApp,
        )

    RemoteDisplayScreenEffects(
        routeState = routeState,
        controlViewModel = controlViewModel,
        connectionViewModel = connectionViewModel,
        scope = scope,
    )

    RemoteDisplayScreenContent(
        sessionId = sessionId,
        routeState = routeState,
        controlViewModel = controlViewModel,
        connectionViewModel = connectionViewModel,
        onClose = onClose,
    )
}

@Composable
private fun rememberRemoteDisplayScreenRouteState(
    context: Context,
    sessionId: String,
    sessionRepository: SessionRepository,
    controlViewModel: ControlViewModel,
    connectionViewModel: ConnectionViewModel,
    settingsViewModel: SettingsViewModel,
    onBackToApp: () -> Unit,
): RemoteDisplayScreenRouteState {
    val videoStream by connectionViewModel.getVideoStream().collectAsState()
    val compatibilityFrame by connectionViewModel.getCompatibilityFrame().collectAsState()
    val videoResolution by connectionViewModel.getVideoResolution().collectAsState()
    val audioStream by connectionViewModel.getAudioStream().collectAsState()
    val connectionState by connectionViewModel.getConnectionState().collectAsState()
    val connectStatus by connectionViewModel.connectStatus.collectAsState()
    val connectionProgress by connectionViewModel.connectionProgress.collectAsState()
    val decoderResolutionRecoveryRequest by connectionViewModel.decoderResolutionRecoveryRequest.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val sessionData by remember(sessionId, sessionRepository) {
        sessionRepository.getSessionDataFlow(sessionId)
    }.collectAsState(initial = null)
    val activeSessionData by connectionViewModel.activeSessionData.collectAsState()
    val resolvedSessionData = activeSessionData?.takeIf { it.id == sessionId } ?: sessionData
    val scope = rememberCoroutineScope()

    DisposableEffect(resolvedSessionData?.config?.keepDeviceAwake) {
        val activity = context as? ComponentActivity
        if (resolvedSessionData?.config?.keepDeviceAwake == true) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val messageListState = rememberMessageListState()

    var showKeyboardInput by remember { mutableStateOf(false) }
    var keyboardRequestToken by remember { mutableIntStateOf(0) }
    var isLayoutInspectorLoading by remember { mutableStateOf(false) }
    var isSendingFile by remember { mutableStateOf(false) }
    var uploadPickerRequestToken by remember { mutableIntStateOf(0) }
    var isLayoutInspectorVisible by remember { mutableStateOf(false) }
    var isLayoutInspectorAutoRefreshEnabled by remember { mutableStateOf(false) }
    var isTargetKeyboardVisible by remember { mutableStateOf(false) }
    var layoutInspectorSnapshot by remember { mutableStateOf<RemoteUiLayoutSnapshot?>(null) }
    var layoutInspectorNodes by remember { mutableStateOf<List<RemoteUiLayoutNode>>(emptyList()) }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    var renderSurface by remember { mutableStateOf<Surface?>(null) }
    var lifecycleState by remember { mutableStateOf(Lifecycle.Event.ON_ANY) }
    var videoAspectRatio by remember { mutableFloatStateOf(9f / 16f) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    val deviceResolutionAdaptedState = remember { mutableStateOf(false) }
    val showPatternLockDialog = remember { mutableStateOf(false) }
    val usePatternLockCache = remember { mutableStateOf(true) }

    LaunchedEffect(resolvedSessionData?.config?.compatibilityMode, videoResolution) {
        if (resolvedSessionData?.config?.compatibilityMode == true) {
            videoResolution?.let { (width, height) ->
                if (width > 0 && height > 0) {
                    videoWidth = width
                    videoHeight = height
                    videoAspectRatio = width.toFloat() / height.toFloat()
                }
            }
        }
    }

    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            controlViewModel.getTargetDisplayInfo()
                .onSuccess { displayInfo ->
                    deviceResolutionAdaptedState.value =
                        isDisplayAdaptedToLocalDevice(displayInfo, context.resolveLocalDisplaySpec())
                }
        } else {
            deviceResolutionAdaptedState.value = false
        }
    }

    rememberAudioDecoderManager(
        connectionViewModel = connectionViewModel,
        audioStream = audioStream,
        audioVolume = 1.0f,
    )

    val videoDecoderManager =
        rememberVideoDecoderManager(
            connectionViewModel = connectionViewModel,
            videoStream = videoStream,
            surfaceHolder = surfaceHolder,
            renderSurface = renderSurface,
            usePersistentSurface = resolvedSessionData?.config?.let { it.useFullScreen && !it.gameMode } ?: false,
            lifecycleState = lifecycleState,
            onVideoSizeChanged = { width, height, aspectRatio ->
                videoWidth = width
                videoHeight = height
                videoAspectRatio = aspectRatio
            },
        )

    fun requestLayoutInspectorRender(showOverlayOnSuccess: Boolean) {
        if (isLayoutInspectorLoading) {
            return
        }

        scope.launch {
            isLayoutInspectorLoading = true

            val result = controlViewModel.captureCurrentUiLayout()
            result
                .onSuccess { snapshot ->
                    if (snapshot.nodes.isEmpty()) {
                        layoutInspectorSnapshot = snapshot
                        layoutInspectorNodes = emptyList()
                        isLayoutInspectorVisible = false
                        isLayoutInspectorAutoRefreshEnabled = false
                        Toast.makeText(context, RemoteTexts.REMOTE_LAYOUT_RENDER_EMPTY.get(), Toast.LENGTH_SHORT).show()
                    } else {
                        layoutInspectorSnapshot = snapshot
                        layoutInspectorNodes = snapshot.nodes
                        if (showOverlayOnSuccess || isLayoutInspectorAutoRefreshEnabled) {
                            isLayoutInspectorVisible = true
                        }
                    }
                }.onFailure { error ->
                    val message =
                        error.message?.takeIf { it.isNotBlank() }
                            ?: RemoteTexts.REMOTE_LAYOUT_RENDER_FAILED.get()
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }

            controlViewModel.isTargetDeviceKeyboardVisible()
                .onSuccess { visible ->
                    isTargetKeyboardVisible = visible
                }

            isLayoutInspectorLoading = false
        }
    }

    val floatingMenuActions =
        remember(controlViewModel, settings.enableFloatingHapticFeedback, connectionViewModel) {
            FloatingMenuActions(
                controlViewModel = controlViewModel,
                captureTargetDeviceScreenshot = {
                    controlViewModel.captureTargetDeviceScreenshot()
                },
                toggleDeviceResolutionAdaptation = {
                    val display = context.resolveLocalDisplaySpec()
                    controlViewModel.getTargetDisplayInfo(refresh = true).fold(
                        onSuccess = { currentDisplayInfo ->
                            val shouldAdapt = !isDisplayAdaptedToLocalDevice(currentDisplayInfo, display)
                            controlViewModel.setTargetDisplayResolution(
                                width = display.width,
                                height = display.height,
                                densityDpi = display.densityDpi,
                                adapted = shouldAdapt,
                            ).onSuccess { displayInfo ->
                                deviceResolutionAdaptedState.value =
                                    isDisplayAdaptedToLocalDevice(displayInfo, display)
                                val message =
                                    if (shouldAdapt) {
                                        RemoteTexts.REMOTE_DEVICE_RESOLUTION_ADAPTED.get()
                                    } else {
                                        RemoteTexts.REMOTE_DEVICE_RESOLUTION_RESTORED.get()
                                    }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                val message =
                                    error.message?.takeIf { it.isNotBlank() }
                                        ?: RemoteTexts.REMOTE_DEVICE_RESOLUTION_CHANGE_FAILED.get()
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }.map { shouldAdapt }
                        },
                        onFailure = { error ->
                            val message =
                                error.message?.takeIf { it.isNotBlank() }
                                    ?: RemoteTexts.REMOTE_DEVICE_RESOLUTION_CHANGE_FAILED.get()
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            Result.failure(error)
                        },
                    )
                },
                isDeviceResolutionAdapted = { deviceResolutionAdaptedState.value },
                disconnect = {
                    connectionViewModel.clearConnectStatus()
                    connectionViewModel.disconnectFromDevice()
                },
                reconnect = {
                    connectionViewModel.reconnectActiveSession()
                },
                backToApp = {
                    onBackToApp()
                },
                disableStayAwake = {
                    val result = connectionViewModel.disableStayAwake()
                    result.onSuccess {
                        Toast.makeText(context, "已关闭屏幕常亮", Toast.LENGTH_SHORT).show()
                    }.onFailure { error ->
                        Toast.makeText(context, "关闭失败：${error.message}", Toast.LENGTH_SHORT).show()
                    }
                    result
                },
                showPatternLock = {
                    showPatternLockDialog.value = true
                },
                showKeyboardInput = {
                    showKeyboardInput = true
                    keyboardRequestToken += 1
                },
                requestUploadFilePicker = {
                    if (!isSendingFile) {
                        uploadPickerRequestToken += 1
                    }
                },
                requestLayoutInspectorRender = {
                    isLayoutInspectorAutoRefreshEnabled = true
                    requestLayoutInspectorRender(showOverlayOnSuccess = true)
                },
                rotateTargetDevice = controlViewModel::rotateTargetDevice,
                hapticEnabled = settings.enableFloatingHapticFeedback,
            )
        }

    fun sendSelectedFile(uri: Uri) {
        if (isSendingFile) {
            return
        }

        scope.launch {
            isSendingFile = true

            val result = controlViewModel.sendFileToDevice(context, uri)
            result
                .onSuccess { sendResult ->
                    val message =
                        when (sendResult) {
                            is RemoteFileSendResult.FileUploaded ->
                                RemoteTexts.REMOTE_FILE_UPLOADED.get().format(sendResult.remotePath)

                            is RemoteFileSendResult.ApkInstalled ->
                                RemoteTexts.REMOTE_APK_INSTALLED.get().format(sendResult.fileName)
                        }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    val message = error.message?.ifBlank { null } ?: RemoteTexts.REMOTE_FILE_SEND_FAILED.get()
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }

            isSendingFile = false
        }
    }

    return RemoteDisplayScreenRouteState(
        videoStream = videoStream,
        compatibilityFrame = compatibilityFrame,
        audioStream = audioStream,
        connectionState = connectionState,
        connectStatus = connectStatus,
        connectionProgress = connectionProgress,
        decoderResolutionRecoveryRequest = decoderResolutionRecoveryRequest,
        settings = settings,
        sessionData = resolvedSessionData,
        messageListState = messageListState,
        videoDecoderManager = videoDecoderManager,
        floatingMenuActions = floatingMenuActions,
        uploadPickerRequestToken = uploadPickerRequestToken,
        keyboardRequestToken = keyboardRequestToken,
        layoutInspectorState =
            RemoteLayoutInspectorUiState(
                isLoading = isLayoutInspectorLoading,
                isOverlayVisible = isLayoutInspectorVisible && isLayoutInspectorAutoRefreshEnabled,
                isTargetKeyboardVisible = isTargetKeyboardVisible,
                snapshot = layoutInspectorSnapshot,
                nodes = layoutInspectorNodes,
            ),
        sendSelectedFile = ::sendSelectedFile,
        refreshLayoutInspectorOverlay = {
            requestLayoutInspectorRender(showOverlayOnSuccess = false)
        },
        hideLayoutInspectorOverlay = {
            isLayoutInspectorVisible = false
            isLayoutInspectorAutoRefreshEnabled = false
            isTargetKeyboardVisible = false
        },
        showKeyboardInput = showKeyboardInput,
        onKeyboardInputVisibleChange = { showKeyboardInput = it },
        surfaceHolder = surfaceHolder,
        onSurfaceHolderChanged = { surfaceHolder = it },
        renderSurface = renderSurface,
        onRenderSurfaceChanged = { renderSurface = it },
        lifecycleState = lifecycleState,
        onLifecycleStateChanged = { lifecycleState = it },
        videoAspectRatio = videoAspectRatio,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        onVideoMetricsChanged = { width, height, aspectRatio ->
            videoWidth = width
            videoHeight = height
            videoAspectRatio = aspectRatio
        },
        showPatternLockDialog = showPatternLockDialog,
        usePatternLockCache = usePatternLockCache,
    )
}

@Composable
private fun RemoteDisplayScreenEffects(
    routeState: RemoteDisplayScreenRouteState,
    controlViewModel: ControlViewModel,
    connectionViewModel: ConnectionViewModel,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val containerSize = LocalWindowInfo.current.containerSize
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? ComponentActivity
    val originalRequestedOrientation =
        remember(activity) {
            activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    val gameMode = routeState.sessionData?.config?.gameMode == true
    val gameRuntimeActive = gameMode && routeState.connectionState is ConnectionState.Connected

    DisposableEffect(activity, originalRequestedOrientation) {
        onDispose {
            activity?.requestedOrientation =
                requestedOrientationAfterRemoteSession(originalRequestedOrientation)
        }
    }

    LaunchedEffect(
        activity,
        originalRequestedOrientation,
        routeState.sessionData?.config?.screenRotationPolicy,
        configuration.orientation,
        routeState.connectionState,
        routeState.videoWidth,
        routeState.videoHeight,
    ) {
        val rotationPolicy =
            routeState.sessionData?.config?.screenRotationPolicy ?: ScreenRotationPolicy.NONE
        val remoteOrientation = remoteScreenOrientation(routeState.videoWidth, routeState.videoHeight)
        val targetOrientation =
            requestedOrientationForRotationPolicy(
                policy = rotationPolicy,
                remoteOrientation = remoteOrientation,
                originalRequestedOrientation = originalRequestedOrientation,
            )

        if (targetOrientation != null && activity?.requestedOrientation != targetOrientation) {
            activity?.requestedOrientation = targetOrientation
            LogManager.d(
                LogTags.REMOTE_DISPLAY,
                "Screen rotation policy applied: policy=${rotationPolicy.name.lowercase()}, " +
                    "remote=${routeState.videoWidth}x${routeState.videoHeight}, requestedOrientation=$targetOrientation",
            )
        }

        if (
            routeState.connectionState is ConnectionState.Connected &&
            shouldRotateTargetForLocalPolicy(
                policy = rotationPolicy,
                localOrientation = localScreenOrientation(configuration.orientation),
                remoteOrientation = remoteOrientation,
            )
        ) {
            controlViewModel.rotateTargetDevice().onFailure { error ->
                LogManager.e(
                    LogTags.REMOTE_DISPLAY,
                    "Failed to synchronize target rotation with the local device: ${error.message}",
                    error,
                )
            }
        }
    }

    DisposableEffect(gameRuntimeActive) {
        if (gameRuntimeActive) {
            LogManager.setRuntimeLoggingSuppressed(true)
            MdnsSessionDiscoveryManager.get().setGameModePaused(true)
        }
        onDispose {
            if (gameRuntimeActive) {
                LogManager.setRuntimeLoggingSuppressed(false)
                MdnsSessionDiscoveryManager.get().setGameModePaused(false)
            }
        }
    }

    LaunchedEffect(routeState.connectionState) {
        if (
            routeState.connectionState is ConnectionState.Connecting ||
            routeState.connectionState is ConnectionState.Reconnecting
        ) {
            routeState.messageListState.clear()
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
        activity?.window?.let { window ->
            ApiCompatHelper.setFullScreen(window, true)
        }
        onDispose {
            activity?.window?.let { window ->
                ApiCompatHelper.setFullScreen(window, false)
            }
        }
    }

    LaunchedEffect(routeState.connectionProgress) {
        if (routeState.connectionProgress.isEmpty()) {
            routeState.messageListState.clear()
        } else {
            routeState.connectionProgress.forEach { progress ->
                val messageId = progress.step.name
                val newMessage =
                    MessageItem(
                        id = messageId,
                        icon = progress.status.getIcon(),
                        title = progress.step.getDisplayText(),
                        subtitle = progress.message,
                        error = progress.error,
                    )
                val existingMessage = routeState.messageListState.messages.find { it.id == messageId }
                if (existingMessage == null) {
                    routeState.messageListState.addMessage(newMessage)
                } else {
                    routeState.messageListState.updateMessage(messageId) { newMessage }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                routeState.onLifecycleStateChanged(event)
                if (event == Lifecycle.Event.ON_RESUME) {
                    scope.launch {
                        runCatching { controlViewModel.wakeUpScreen() }
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isALandscape = containerSize.width > containerSize.height
    LaunchedEffect(containerSize, routeState.videoWidth, routeState.videoHeight) {
        if (routeState.videoWidth > 0 && routeState.videoHeight > 0) {
            val aspectRatio = routeState.videoWidth.toFloat() / routeState.videoHeight.toFloat()
            routeState.onVideoMetricsChanged(routeState.videoWidth, routeState.videoHeight, aspectRatio)

            val isBLandscape = routeState.videoWidth > routeState.videoHeight
            val containerAspectRatio =
                if (containerSize.height > 0) {
                    containerSize.width.toFloat() / containerSize.height.toFloat()
                } else {
                    aspectRatio
                }
            val matchHeightFirst = aspectRatio < containerAspectRatio

            LogManager.d(
                LogTags.REMOTE_DISPLAY,
                "🔄 ${RemoteTexts.REMOTE_SCREEN_ROTATION_A.english}: A${
                    if (isALandscape) {
                        RemoteTexts.REMOTE_LANDSCAPE.english
                    } else {
                        RemoteTexts.REMOTE_PORTRAIT.english
                    }
                }, B${
                    if (isBLandscape) {
                        RemoteTexts.REMOTE_LANDSCAPE.english
                    } else {
                        RemoteTexts.REMOTE_PORTRAIT.english
                    }
                }, ${RemoteTexts.REMOTE_ASPECT_RATIO.english}=$aspectRatio, ${RemoteTexts.REMOTE_SCALE_STRATEGY.english}: ${
                    if (matchHeightFirst) {
                        RemoteTexts.REMOTE_FILL_HEIGHT.english
                    } else {
                        RemoteTexts.REMOTE_FILL_WIDTH.english
                    }
                }",
            )
        }
    }

    BackHandler(
        enabled = shouldInterceptRemoteBack(routeState.connectionState, routeState.connectStatus),
    ) {
        LogManager.d(
            LogTags.REMOTE_DISPLAY,
            "The return key is triggered, current status: ${routeState.connectionState}"
        )
        if (shouldCancelConnectionOnBack(routeState.connectionState, routeState.connectStatus)) {
            LogManager.d(LogTags.REMOTE_DISPLAY, "Connecting/reconnecting, canceling connection")
            connectionViewModel.cancelConnect()
        } else when (routeState.connectionState) {
            is ConnectionState.Connected -> {
                scope.launch {
                    val result = controlViewModel.sendKeyEvent(4)
                    if (result.isFailure) {
                        LogManager.e(
                            LogTags.REMOTE_DISPLAY,
                            "Failed to send return key: ${result.exceptionOrNull()?.message}",
                        )
                    } else {
                        LogManager.d(LogTags.REMOTE_DISPLAY, "Return key sent successfully")
                    }
                }
            }

            else -> Unit
        }
    }

    val hardwareKeyEventHost = context as? RemoteHardwareKeyEventHost
    DisposableEffect(hardwareKeyEventHost, routeState.connectionState) {
        val captureVolumeKeys = routeState.connectionState is ConnectionState.Connected
        val handler =
            if (captureVolumeKeys) {
                var volumePressCount = 0
                var passCurrentPressToLocal = false

                RemoteHardwareKeyEventHandler { event ->
                    val remoteKeyCode =
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_VOLUME_UP -> KeyEvent.KEYCODE_VOLUME_UP
                            KeyEvent.KEYCODE_VOLUME_DOWN -> KeyEvent.KEYCODE_VOLUME_DOWN
                            else -> return@RemoteHardwareKeyEventHandler false
                        }
                    val action = event.action
                    val metaState = event.metaState
                    val repeatCount = event.repeatCount
                    val passThroughToLocal =
                        when (action) {
                            KeyEvent.ACTION_DOWN -> {
                                if (repeatCount == 0) {
                                    volumePressCount += 1
                                    passCurrentPressToLocal = volumePressCount % 2 == 0
                                }
                                passCurrentPressToLocal
                            }

                            KeyEvent.ACTION_UP -> passCurrentPressToLocal
                            else -> false
                        }

                    scope.launch {
                        controlViewModel.sendKeyEvent(
                            keyCode = remoteKeyCode,
                            action = action,
                            metaState = metaState,
                            repeat = repeatCount,
                        ).onFailure { error ->
                            LogManager.e(
                                LogTags.REMOTE_DISPLAY,
                                "Failed to forward hardware volume key: keyCode=$remoteKeyCode action=$action: ${error.message}",
                                error,
                            )
                        }
                    }
                    if (action == KeyEvent.ACTION_UP) {
                        passCurrentPressToLocal = false
                    }
                    !passThroughToLocal
                }
            } else {
                null
            }
        hardwareKeyEventHost?.setRemoteHardwareKeyEventHandler(handler)
        onDispose {
            hardwareKeyEventHost?.setRemoteHardwareKeyEventHandler(null)
        }
    }
}

@Composable
private fun RemoteDisplayScreenContent(
    sessionId: String,
    routeState: RemoteDisplayScreenRouteState,
    controlViewModel: ControlViewModel,
    connectionViewModel: ConnectionViewModel,
    onClose: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeSessionData by connectionViewModel.activeSessionData.collectAsState()
    val uploadLauncher =
        FilePickerHelper.rememberImportFileLauncher { uri ->
            uri?.let(routeState.sendSelectedFile)
        }

    LaunchedEffect(routeState.uploadPickerRequestToken) {
        if (routeState.uploadPickerRequestToken > 0) {
            uploadLauncher.launch(arrayOf("*/*"))
        }
    }

    LaunchedEffect(routeState.layoutInspectorState.isOverlayVisible) {
        if (routeState.layoutInspectorState.isOverlayVisible) {
            while (true) {
                delay(1000.milliseconds)
                routeState.refreshLayoutInspectorOverlay()
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (
                shouldShowFloatingMenu(
                    videoAvailable = routeState.videoStream != null || routeState.compatibilityFrame != null,
                    showFloatingBall = routeState.sessionData?.config?.showFloatingBall,
                )
            ) {
                AutoFloatingMenu(actions = routeState.floatingMenuActions)
            }

            VideoDisplayArea(
                controlViewModel = controlViewModel,
                sessionData = routeState.sessionData,
                videoAspectRatio = routeState.videoAspectRatio,
                videoWidth = routeState.videoWidth,
                videoHeight = routeState.videoHeight,
                compatibilityFrame = routeState.compatibilityFrame,
                configuration = configuration,
                onSurfaceHolderChanged = routeState.onSurfaceHolderChanged,
                onRenderSurfaceChanged = routeState.onRenderSurfaceChanged,
                videoDecoderManager = routeState.videoDecoderManager,
            ) {
                if (routeState.layoutInspectorState.isOverlayVisible) {
                    RemoteLayoutInspectorOverlay(
                        snapshot = routeState.layoutInspectorState.snapshot,
                        nodes = routeState.layoutInspectorState.nodes,
                        isLoading = routeState.layoutInspectorState.isLoading,
                        onRefresh = routeState.refreshLayoutInspectorOverlay,
                        onClose = routeState.hideLayoutInspectorOverlay,
                    )
                }

                if (
                    routeState.layoutInspectorState.isOverlayVisible &&
                    routeState.layoutInspectorState.isTargetKeyboardVisible
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 40.dp),
                        color = Color(0xCC1F2937),
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = RemoteTexts.REMOTE_TARGET_KEYBOARD_OPEN.get(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            if (
                routeState.settings.showPerformanceStats &&
                routeState.videoStream != null
            // && routeState.sessionData?.config?.gameMode != true // 游戏模式禁用帧率显示和网络速率
            ) {
                RemotePerformanceStatsOverlay(
                    videoDecoderManager = routeState.videoDecoderManager,
                    isNetworkSession = routeState.sessionData?.isUsbConnection() != true,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                )
            }

            ConnectionStateOverlay(
                connectionState =
                    connectionStateForRemoteOverlay(
                        connectionState = routeState.connectionState,
                        compatibilityMode = routeState.sessionData?.config?.compatibilityMode == true,
                        compatibilityFrameAvailable = routeState.compatibilityFrame != null,
                    ),
                sessionName = routeState.sessionData?.name.orEmpty(),
                messageListState = routeState.messageListState,
                onReconnect = connectionViewModel::reconnectActiveSession,
                onClose = onClose,
            )

            if (routeState.showKeyboardInput) {
                KeyboardInputHandler(
                    controlViewModel = controlViewModel,
                    keyboardController = keyboardController,
                    requestToken = routeState.keyboardRequestToken,
                    onDismiss = { routeState.onKeyboardInputVisibleChange(false) },
                )
            }
        }
    }

    routeState.decoderResolutionRecoveryRequest?.let { request ->
        DecoderResolutionRecoveryOverlay(
            request = request,
            onConfirm = connectionViewModel::confirmDecoderResolutionRecovery,
            onDismiss = {
                connectionViewModel.dismissDecoderResolutionRecovery()
                onClose()
            },
        )
    }

    // 密码输入对话框（图案密码/文本密码切换）
    if (routeState.showPatternLockDialog.value) {
        PatternLockInputDialog(
            onDismiss = { routeState.showPatternLockDialog.value = false },
            onPatternComplete = { pattern ->
                routeState.showPatternLockDialog.value = false
                scope.launch {
                    sendPatternToRemote(
                        pattern = pattern,
                        controlViewModel = controlViewModel,
                        screenWidth = routeState.videoWidth,
                        screenHeight = routeState.videoHeight,
                        context = context,
                        pollInterval = activeSessionData?.config?.patternLockPollInterval ?: 200,
                        sessionId = sessionId,
                        useCache = routeState.usePatternLockCache.value,
                    )
                }
            },
            onTextPasswordComplete = { password ->
                routeState.showPatternLockDialog.value = false
                scope.launch {
                    val result = controlViewModel.sendText(password)
                    result.onSuccess {
                        android.widget.Toast.makeText(context, "密码已发送", android.widget.Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        android.widget.Toast.makeText(context, "发送失败：${it.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onClearCache = {
                // 清除当前会话的持久化缓存位置
                if (sessionId.isNotBlank()) {
                    PatternLockCacheManager.clear(context, sessionId)
                }
                android.widget.Toast.makeText(context, "当前设备图案密码位置缓存已清除", android.widget.Toast.LENGTH_SHORT).show()
            },
            useCache = routeState.usePatternLockCache.value,
            onUseCacheChange = { routeState.usePatternLockCache.value = it },
        )
    }
}

/**
 * 将图案密码发送到远程设备
 * @param pattern 选中的点索引列表（0-8，按行优先排列）
 * @param controlViewModel ControlViewModel 用于发送触摸事件
 * @param screenWidth 远程设备屏幕宽度
 * @param screenHeight 远程设备屏幕高度
 * @param context Context 用于显示 Toast
 * @param pollInterval 图案密码循环检测间隔（毫秒）
 */
private suspend fun sendPatternToRemote(
    pattern: List<Int>,
    controlViewModel: ControlViewModel,
    screenWidth: Int,
    screenHeight: Int,
    context: android.content.Context,
    pollInterval: Int = 200,
    sessionId: String = "",
    useCache: Boolean = true,
) {
    if (pattern.isEmpty()) return

    try {
        // 获取远程设备的实际屏幕分辨率
        val displayInfo = controlViewModel.getTargetDisplayInfo(refresh = true).getOrNull()
        val actualWidth = displayInfo?.currentWidth ?: screenWidth
        val actualHeight = displayInfo?.currentHeight ?: screenHeight

        // 唤醒屏幕
        android.util.Log.d("PatternLock", "Waking up screen...")
        controlViewModel.sendKeyEvent(224) // KEYCODE_WAKEUP
        kotlinx.coroutines.delay(500)

        // 斜向上滑动（从右下角滑到左上角）
        swipeDiagonalUp(controlViewModel, actualWidth, actualHeight)
        kotlinx.coroutines.delay(500)

        // 循环检测图案锁，直到出现为止（自动滑动或用户手动滑动后自动检测）
        val patternArea = waitForPatternLock(controlViewModel, actualWidth, actualHeight, context, pollInterval, sessionId, useCache)
        val patternAreaLeft = patternArea.left
        val patternAreaTop = patternArea.top
        val patternAreaWidth = patternArea.width
        val patternAreaHeight = patternArea.height

        android.util.Log.d("PatternLock", "actualWidth=$actualWidth, actualHeight=$actualHeight")
        android.util.Log.d("PatternLock", "patternArea: left=$patternAreaLeft, top=$patternAreaTop, width=$patternAreaWidth, height=$patternAreaHeight")

        // 计算9个点的实际坐标（3x3点阵）
        val pointCoordinates = List(9) { index ->
            val row = index / 3
            val col = index % 3
            val x = patternAreaLeft + (col + 0.5f) * (patternAreaWidth / 3f)
            val y = patternAreaTop + (row + 0.5f) * (patternAreaHeight / 3f)
            Pair(x.toInt(), y.toInt())
        }

        android.util.Log.d("PatternLock", "pointCoordinates: ${pointCoordinates.mapIndexed { i, p -> "$i=(${p.first},${p.second})" }.joinToString()}")
        android.util.Log.d("PatternLock", "pattern: $pattern")

        val pointerId = 1L

        // 发送 ACTION_DOWN
        val firstPoint = pointCoordinates[pattern[0]]
        val downResult = controlViewModel.sendTouchEvent(
            action = android.view.MotionEvent.ACTION_DOWN,
            pointerId = pointerId,
            x = firstPoint.first,
            y = firstPoint.second,
            screenWidth = actualWidth,
            screenHeight = actualHeight,
        )
        android.util.Log.d("PatternLock", "ACTION_DOWN at (${firstPoint.first},${firstPoint.second}), result=$downResult")
        kotlinx.coroutines.delay(100)

        // 发送 ACTION_MOVE（经过每个点）
        for (i in 1 until pattern.size) {
            val point = pointCoordinates[pattern[i]]
            // 在两个点之间插入一些中间点，模拟滑动
            val prevPoint = pointCoordinates[pattern[i - 1]]
            val steps = 10
            for (step in 1..steps) {
                val interpolatedX = prevPoint.first + (point.first - prevPoint.first) * step / steps
                val interpolatedY = prevPoint.second + (point.second - prevPoint.second) * step / steps
                val moveResult = controlViewModel.sendTouchEvent(
                    action = android.view.MotionEvent.ACTION_MOVE,
                    pointerId = pointerId,
                    x = interpolatedX,
                    y = interpolatedY,
                    screenWidth = actualWidth,
                    screenHeight = actualHeight,
                )
                if (step == steps) {
                    android.util.Log.d("PatternLock", "ACTION_MOVE to (${point.first},${point.second}), result=$moveResult")
                }
                kotlinx.coroutines.delay(15)
            }
        }

        // 发送 ACTION_UP
        val lastPoint = pointCoordinates[pattern.last()]
        val upResult = controlViewModel.sendTouchEvent(
            action = android.view.MotionEvent.ACTION_UP,
            pointerId = pointerId,
            x = lastPoint.first,
            y = lastPoint.second,
            screenWidth = actualWidth,
            screenHeight = actualHeight,
        )
        android.util.Log.d("PatternLock", "ACTION_UP at (${lastPoint.first},${lastPoint.second}), result=$upResult")

        android.widget.Toast.makeText(context, "图案密码已发送", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.util.Log.e("PatternLock", "sendPatternToRemote exception", e)
        android.widget.Toast.makeText(context, "发送失败：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/**
 * 斜向上滑动（从右下角滑到左上角）
 */
private suspend fun swipeDiagonalUp(
    controlViewModel: ControlViewModel,
    screenWidth: Int,
    screenHeight: Int,
) {
    try {
        // 从右下角滑到左上角
        val startX = (screenWidth * 0.8).toInt()
        val startY = (screenHeight * 0.8).toInt()
        val endX = (screenWidth * 0.2).toInt()
        val endY = (screenHeight * 0.2).toInt()
        val pointerId = 2L

        android.util.Log.d("PatternLock", "Swiping diagonal up: from ($startX,$startY) to ($endX,$endY)")

        // ACTION_DOWN
        controlViewModel.sendTouchEvent(
            action = android.view.MotionEvent.ACTION_DOWN,
            pointerId = pointerId,
            x = startX,
            y = startY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        kotlinx.coroutines.delay(50)

        // ACTION_MOVE（插值滑动）
        val steps = 20
        for (step in 1..steps) {
            val interpolatedX = startX + (endX - startX) * step / steps
            val interpolatedY = startY + (endY - startY) * step / steps
            controlViewModel.sendTouchEvent(
                action = android.view.MotionEvent.ACTION_MOVE,
                pointerId = pointerId,
                x = interpolatedX,
                y = interpolatedY,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )
            kotlinx.coroutines.delay(10)
        }

        // ACTION_UP
        controlViewModel.sendTouchEvent(
            action = android.view.MotionEvent.ACTION_UP,
            pointerId = pointerId,
            x = endX,
            y = endY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )

        android.util.Log.d("PatternLock", "Diagonal swipe up completed")
    } catch (e: Exception) {
        android.util.Log.w("PatternLock", "Failed to swipe diagonal up: ${e.message}")
    }
}

/**
 * 图案锁区域数据类
 */
private data class PatternLockArea(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * 图案锁位置缓存管理器（持久化到 SharedPreferences）
 * 使用设备唯一标识作为 key，重新添加设备也能找到缓存
 */
private object PatternLockCacheManager {
    private const val PREFS_NAME = "pattern_lock_cache"
    private const val KEY_PREFIX = "pattern_lock_"

    fun save(context: android.content.Context, deviceIdentifier: String, area: PatternLockArea) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + deviceIdentifier
        prefs.edit()
            .putInt("$key.left", area.left)
            .putInt("$key.top", area.top)
            .putInt("$key.width", area.width)
            .putInt("$key.height", area.height)
            .apply()
        android.util.Log.d("PatternLock", "Cached position saved for: $deviceIdentifier")
    }

    fun load(context: android.content.Context, deviceIdentifier: String): PatternLockArea? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + deviceIdentifier
        val left = prefs.getInt("$key.left", -1)
        if (left == -1) return null
        val top = prefs.getInt("$key.top", 0)
        val width = prefs.getInt("$key.width", 0)
        val height = prefs.getInt("$key.height", 0)
        android.util.Log.d("PatternLock", "Cached position loaded for: $deviceIdentifier")
        return PatternLockArea(left, top, width, height)
    }

    fun clear(context: android.content.Context, deviceIdentifier: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + deviceIdentifier
        prefs.edit()
            .remove("$key.left")
            .remove("$key.top")
            .remove("$key.width")
            .remove("$key.height")
            .apply()
        android.util.Log.d("PatternLock", "Cached position cleared for: $deviceIdentifier")
    }
}

/**
 * 循环等待图案锁出现
 * 直到检测到图案锁为止
 */
private suspend fun waitForPatternLock(
    controlViewModel: ControlViewModel,
    screenWidth: Int,
    screenHeight: Int,
    context: android.content.Context,
    pollInterval: Int = 200,
    sessionId: String = "",
    useCache: Boolean = true,
): PatternLockArea {
    // 如果启用缓存且有持久化缓存，直接使用缓存位置，不再重新检测
    val cached = if (useCache && sessionId.isNotBlank()) {
        PatternLockCacheManager.load(context, sessionId)
    } else null
    if (cached != null) {
        android.util.Log.d("PatternLock", "Using cached pattern lock position directly for session: $sessionId")
        android.widget.Toast.makeText(context, "采用缓存位置，正在输入...", android.widget.Toast.LENGTH_SHORT).show()
        return cached
    }

    // 没有缓存位置，进行完整检测循环
    val maxWaitTime = 30000L // 最多等待30秒
    val startTime = System.currentTimeMillis()

    while (System.currentTimeMillis() - startTime < maxWaitTime) {
        val result = tryFindPatternLockArea(controlViewModel, screenWidth, screenHeight)
        if (result != null) {
            android.util.Log.d("PatternLock", "Pattern lock detected after ${System.currentTimeMillis() - startTime}ms")
            // 保存到持久化缓存（按会话ID存储）
            if (sessionId.isNotBlank()) {
                PatternLockCacheManager.save(context, sessionId, result)
            }
            android.widget.Toast.makeText(context, "已检测到图案密码框，正在输入...", android.widget.Toast.LENGTH_SHORT).show()
            return result
        }
        kotlinx.coroutines.delay(pollInterval.toLong())
    }

    // 超时，使用默认位置
    android.util.Log.w("PatternLock", "Timeout waiting for pattern lock, using default position")
    val defaultWidth = screenWidth / 3
    val defaultHeight = defaultWidth
    val defaultLeft = (screenWidth - defaultWidth) / 2
    val defaultTop = (screenHeight - defaultHeight) / 2 - screenHeight / 8
    return PatternLockArea(defaultLeft, defaultTop, defaultWidth, defaultHeight)
}

/**
 * 尝试查找图案锁区域，找不到返回null
 */
private suspend fun tryFindPatternLockArea(
    controlViewModel: ControlViewModel,
    screenWidth: Int,
    screenHeight: Int,
): PatternLockArea? {
    return try {
        val layoutResult = controlViewModel.captureCurrentUiLayout()
        layoutResult.fold(
            onSuccess = { snapshot ->
                val lockPatternNode = snapshot.nodes.find { node ->
                    node.className.contains("LockPattern", ignoreCase = true) ||
                        node.resourceId.contains("lockPattern", ignoreCase = true) ||
                        node.className.contains("PatternView", ignoreCase = true)
                }

                if (lockPatternNode != null && lockPatternNode.bounds.hasArea()) {
                    val bounds = lockPatternNode.bounds
                    PatternLockArea(bounds.left, bounds.top, bounds.width, bounds.height)
                } else {
                    null
                }
            },
            onFailure = { null },
        )
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun DecoderResolutionRecoveryOverlay(
    request: DecoderResolutionRecoveryRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isServerCaptureFailure = request.source == VideoResolutionRecoverySource.ServerCapture
    val title =
        rememberText(
            if (isServerCaptureFailure) {
                RemoteTexts.REMOTE_CAPTURE_SIZE_UNSUPPORTED_TITLE
            } else {
                RemoteTexts.REMOTE_DECODER_SIZE_UNSUPPORTED_TITLE
            },
        )
    val confirm = rememberText(RemoteTexts.REMOTE_DECODER_SIZE_RECOVERY_CONFIRM)
    val cancel = rememberText(RemoteTexts.REMOTE_DECODER_SIZE_RECOVERY_CANCEL)

    ConnectionActionOverlay(
        title = title,
        message =
            if (isServerCaptureFailure) {
                RemoteTexts.REMOTE_CAPTURE_SIZE_UNSUPPORTED_MESSAGE.format(request.suggestedMaxSize)
            } else {
                RemoteTexts.REMOTE_DECODER_SIZE_UNSUPPORTED_MESSAGE.format(
                    request.decoderName,
                    request.width,
                    request.height,
                    request.suggestedMaxSize,
                )
            },
        confirmText = confirm,
        dismissText = cancel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

internal fun shouldShowFloatingMenu(
    videoAvailable: Boolean,
    showFloatingBall: Boolean?,
): Boolean = videoAvailable && showFloatingBall == true
