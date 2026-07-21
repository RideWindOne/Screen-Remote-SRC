package com.screen.remote.android.feature.remote.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.datastore.PreferencesManager
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.data.repository.SessionRepository
import com.screen.remote.android.core.designsystem.component.MessageItem
import com.screen.remote.android.core.designsystem.component.MessageListState
import com.screen.remote.android.core.designsystem.component.rememberMessageListState
import com.screen.remote.android.core.domain.model.AppSettings
import com.screen.remote.android.core.domain.model.ConnectionProgress
import com.screen.remote.android.core.domain.model.getDisplayText
import com.screen.remote.android.core.domain.model.getIcon
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.util.FilePickerHelper
import com.screen.remote.android.core.common.util.LocalDisplaySpec
import com.screen.remote.android.core.common.util.resolveLocalDisplaySpec
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.feature.remote.model.RemoteUiLayoutNode
import com.screen.remote.android.feature.remote.model.RemoteUiLayoutSnapshot
import com.screen.remote.android.feature.remote.presentation.ConnectionViewModel
import com.screen.remote.android.feature.remote.presentation.ConnectStatus
import com.screen.remote.android.feature.remote.presentation.ControlViewModel
import com.screen.remote.android.feature.remote.presentation.RemoteFileSendResult
import com.screen.remote.android.feature.remote.presentation.VideoDecoderManager
import com.screen.remote.android.feature.remote.presentation.rememberAudioDecoderManager
import com.screen.remote.android.feature.remote.presentation.rememberVideoDecoderManager
import com.screen.remote.android.feature.remote.ui.internal.RemoteLayoutInspectorOverlay
import com.screen.remote.android.feature.remote.widget.connection.ConnectionStateOverlay
import com.screen.remote.android.feature.remote.widget.floating.AutoFloatingMenu
import com.screen.remote.android.feature.remote.widget.floating.FloatingMenuActions
import com.screen.remote.android.feature.remote.widget.touch.KeyboardInputHandler
import com.screen.remote.android.feature.remote.widget.video.VideoDisplayArea
import com.screen.remote.android.feature.remote.widget.video.RemotePerformanceStatsOverlay
import com.screen.remote.android.feature.session.viewmodel.MainViewModel
import com.screen.remote.android.feature.settings.viewmodel.SettingsViewModel
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.adb.connection.AdbDisplayInfo
import com.screen.remote.android.infrastructure.adb.mdns.MdnsSessionDiscoveryManager
import com.screen.remote.android.infrastructure.media.audio.AudioStream
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionState
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
): Boolean = connectionState is ConnectionState.Connected || shouldCancelConnectionOnBack(connectionState, connectStatus)

private data class RemoteDisplayScreenRouteState(
    val videoStream: VideoStream?,
    val audioStream: AudioStream?,
    val connectionState: ConnectionState,
    val connectStatus: ConnectStatus,
    val connectionProgress: List<ConnectionProgress>,
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
)

@SuppressLint("ClickableViewAccessibility", "ConfigurationScreenWidthHeight")
@Composable
fun RemoteDisplayScreen(
    sessionId: String,
    mainViewModel: MainViewModel,
    onClose: () -> Unit,
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
        )

    RemoteDisplayScreenEffects(
        sessionId = sessionId,
        routeState = routeState,
        controlViewModel = controlViewModel,
        connectionViewModel = connectionViewModel,
        onClose = onClose,
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
): RemoteDisplayScreenRouteState {
    val videoStream by connectionViewModel.getVideoStream().collectAsState()
    val audioStream by connectionViewModel.getAudioStream().collectAsState()
    val connectionState by connectionViewModel.getConnectionState().collectAsState()
    val connectStatus by connectionViewModel.connectStatus.collectAsState()
    val connectionProgress by connectionViewModel.connectionProgress.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val sessionData by remember(sessionId, sessionRepository) {
        sessionRepository.getSessionDataFlow(sessionId)
    }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    DisposableEffect(sessionData?.config?.keepDeviceAwake) {
        val activity = context as? ComponentActivity
        if (sessionData?.config?.keepDeviceAwake == true) {
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
            usePersistentSurface = sessionData?.config?.let { it.useFullScreen && !it.gameMode } ?: false,
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
        audioStream = audioStream,
        connectionState = connectionState,
        connectStatus = connectStatus,
        connectionProgress = connectionProgress,
        settings = settings,
        sessionData = sessionData,
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
    )
}

@Composable
private fun RemoteDisplayScreenEffects(
    sessionId: String,
    routeState: RemoteDisplayScreenRouteState,
    controlViewModel: ControlViewModel,
    connectionViewModel: ConnectionViewModel,
    onClose: () -> Unit,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
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
            activity?.requestedOrientation = originalRequestedOrientation
        }
    }

    LaunchedEffect(
        activity,
        originalRequestedOrientation,
        routeState.sessionData?.config?.followRemoteOrientation,
        routeState.videoWidth,
        routeState.videoHeight,
    ) {
        val targetOrientation =
            if (routeState.sessionData?.config?.followRemoteOrientation == true) {
                requestedOrientationForRemoteScreen(
                    remoteScreenOrientation(routeState.videoWidth, routeState.videoHeight),
                )
            } else {
                originalRequestedOrientation
            }

        if (targetOrientation != null && activity?.requestedOrientation != targetOrientation) {
            activity?.requestedOrientation = targetOrientation
            LogManager.d(
                LogTags.REMOTE_DISPLAY,
                "Follow the target device orientation: ${routeState.videoWidth}x${routeState.videoHeight}, requestedOrientation=$targetOrientation",
            )
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

    val isALandscape = configuration.screenWidthDp > configuration.screenHeightDp
    LaunchedEffect(isALandscape, routeState.videoWidth, routeState.videoHeight) {
        if (routeState.videoWidth > 0 && routeState.videoHeight > 0) {
            val aspectRatio = routeState.videoWidth.toFloat() / routeState.videoHeight.toFloat()
            routeState.onVideoMetricsChanged(routeState.videoWidth, routeState.videoHeight, aspectRatio)

            val isBLandscape = routeState.videoWidth > routeState.videoHeight
            val containerAspectRatio =
                configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
            val matchHeightFirst = aspectRatio < containerAspectRatio

            LogManager.d(
                LogTags.REMOTE_DISPLAY,
                "🔄 ${RemoteTexts.REMOTE_SCREEN_ROTATION_A.english}: A${if (isALandscape) {
                    RemoteTexts.REMOTE_LANDSCAPE.english
                } else {
                    RemoteTexts.REMOTE_PORTRAIT.english
                }}, B${if (isBLandscape) {
                    RemoteTexts.REMOTE_LANDSCAPE.english
                } else {
                    RemoteTexts.REMOTE_PORTRAIT.english
                }}, ${RemoteTexts.REMOTE_ASPECT_RATIO.english}=$aspectRatio, ${RemoteTexts.REMOTE_SCALE_STRATEGY.english}: ${if (matchHeightFirst) {
                    RemoteTexts.REMOTE_FILL_HEIGHT.english
                } else {
                    RemoteTexts.REMOTE_FILL_WIDTH.english
                }}",
            )
        }
    }

    BackHandler(
        enabled = shouldInterceptRemoteBack(routeState.connectionState, routeState.connectStatus),
    ) {
        LogManager.d(LogTags.REMOTE_DISPLAY, "The return key is triggered, current status: ${routeState.connectionState}")
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
                delay(1000)
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
                    videoAvailable = routeState.videoStream != null,
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
                connectionState = routeState.connectionState,
                messageListState = routeState.messageListState,
                onReconnect = { connectionViewModel.connectSession(sessionId) },
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
}

internal fun shouldShowFloatingMenu(
    videoAvailable: Boolean,
    showFloatingBall: Boolean?,
): Boolean = videoAvailable && showFloatingBall == true
