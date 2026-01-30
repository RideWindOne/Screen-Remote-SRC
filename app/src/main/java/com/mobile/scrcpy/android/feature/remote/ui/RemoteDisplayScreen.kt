package com.mobile.scrcpy.android.feature.remote.ui

import android.annotation.SuppressLint
import android.view.SurfaceHolder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobile.scrcpy.android.app.ScreenRemoteApp
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper
import com.mobile.scrcpy.android.core.data.datastore.PreferencesManager
import com.mobile.scrcpy.android.core.designsystem.component.MessageItem
import com.mobile.scrcpy.android.core.designsystem.component.rememberMessageListState
import com.mobile.scrcpy.android.core.domain.model.getDisplayText
import com.mobile.scrcpy.android.core.domain.model.getIcon
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.feature.remote.components.audio.rememberAudioDecoderManager
import com.mobile.scrcpy.android.feature.remote.components.connection.ConnectionStateOverlay
import com.mobile.scrcpy.android.feature.remote.components.floating.AutoFloatingMenu
import com.mobile.scrcpy.android.feature.remote.components.touch.KeyboardInputHandler
import com.mobile.scrcpy.android.feature.remote.components.video.VideoDisplayArea
import com.mobile.scrcpy.android.feature.remote.components.video.rememberVideoDecoderManager
import com.mobile.scrcpy.android.feature.remote.viewmodel.ConnectionViewModel
import com.mobile.scrcpy.android.feature.remote.viewmodel.ControlViewModel
import com.mobile.scrcpy.android.feature.session.data.repository.SessionRepository
import com.mobile.scrcpy.android.feature.session.viewmodel.MainViewModel
import com.mobile.scrcpy.android.feature.session.viewmodel.SessionViewModel
import com.mobile.scrcpy.android.feature.settings.viewmodel.SettingsViewModel
import com.mobile.scrcpy.android.infrastructure.scrcpy.client.feature.scrcpy.ScrcpyClient
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionState
import kotlinx.coroutines.launch

@SuppressLint("ClickableViewAccessibility", "ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteDisplayScreen(
    sessionId: String,
    mainViewModel: MainViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 获取依赖
    val sessionRepository = remember { SessionRepository(context) }
    val adbConnectionManager = remember { ScreenRemoteApp.instance.adbConnectionManager }
    val preferencesManager = remember { PreferencesManager(context) }

    // 使用 MainViewModel 中的实例
    val scrcpyClient = mainViewModel.scrcpyClient
    val connectionVM = mainViewModel.connectionViewModel

    // 创建其他 ViewModels
    val controlVM: ControlViewModel =
        viewModel(
            factory = ControlViewModel.provideFactory(scrcpyClient, adbConnectionManager),
        )
    val sessionVM: SessionViewModel =
        viewModel(
            factory = SessionViewModel.provideFactory(sessionRepository),
        )
    val settingsVM: SettingsViewModel =
        viewModel(
            factory = SettingsViewModel.provideFactory(preferencesManager),
        )

    // 收集状态
    val videoStream by connectionVM.getVideoStream().collectAsState()
    val audioStream by connectionVM.getAudioStream().collectAsState()
    val connectionState by connectionVM.getConnectionState().collectAsState()
    val connectionProgress by connectionVM.connectionProgress.collectAsState()
    val settings by settingsVM.settings.collectAsState()
    val sessionData by remember {
        sessionRepository.getSessionDataFlow(sessionId)
    }.collectAsState(initial = null)

    val keyboardController = LocalSoftwareKeyboardController.current
    val configuration = LocalConfiguration.current

    // 全屏模式：进入时启用，退出时恢复
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

    // 消息列表状态
    val messageListState = rememberMessageListState()

    // 监听 connectionProgress 变化，同步到消息列表
    LaunchedEffect(connectionProgress) {
        if (connectionProgress.isEmpty()) {
            messageListState.clear()
        } else {
            connectionProgress.forEach { progress ->
                val messageId = progress.step.name
                val existingMessage = messageListState.messages.find { it.id == messageId }

                val newMessage =
                    MessageItem(
                        id = messageId,
                        icon = progress.status.getIcon(),
                        title = progress.step.getDisplayText(),
                        subtitle = progress.message,
                        error = progress.error,
                    )

                if (existingMessage == null) {
                    messageListState.addMessage(newMessage)
                } else {
                    messageListState.updateMessage(messageId) { newMessage }
                }
            }
        }
    }

    // 键盘输入状态
    var showKeyboardInput by remember { mutableStateOf(false) }

    // Surface 状态
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }

    // 生命周期监听
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleState by remember { mutableStateOf(Lifecycle.Event.ON_ANY) }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                lifecycleState = event
                if (event == Lifecycle.Event.ON_RESUME) {
                    scope.launch {
                        try {
                            controlVM.wakeUpScreen()
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 视频尺寸和宽高比
    var videoAspectRatio by remember { mutableFloatStateOf(9f / 16f) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    // 监听 A 的旋转，重新计算宽高比
    val isALandscape = configuration.screenWidthDp > configuration.screenHeightDp
    LaunchedEffect(isALandscape) {
        if (videoWidth > 0 && videoHeight > 0) {
            videoAspectRatio = videoWidth.toFloat() / videoHeight.toFloat()

            val isBLandscape = videoWidth > videoHeight
            val containerAspectRatio =
                configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
            val matchHeightFirst = videoAspectRatio < containerAspectRatio

            LogManager.d(
                LogTags.REMOTE_DISPLAY,
                "🔄 ${RemoteTexts.REMOTE_SCREEN_ROTATION_A.get()}: A${if (isALandscape) {
                    RemoteTexts.REMOTE_LANDSCAPE
                        .get()
                } else {
                    RemoteTexts.REMOTE_PORTRAIT.get()
                }}, B${if (isBLandscape) {
                    RemoteTexts.REMOTE_LANDSCAPE
                        .get()
                } else {
                    RemoteTexts.REMOTE_PORTRAIT.get()
                }}, ${RemoteTexts.REMOTE_ASPECT_RATIO.get()}=$videoAspectRatio, ${RemoteTexts.REMOTE_SCALE_STRATEGY.get()}: ${if (matchHeightFirst) {
                    RemoteTexts.REMOTE_FILL_HEIGHT
                        .get()
                } else {
                    RemoteTexts.REMOTE_FILL_WIDTH.get()
                }}",
            )
        }
    }

    // 音频解码器管理
    val audioVolume = 1.0f // sessionData?.audioVolume?.toFloatOrNull() ?: 1.0f
    rememberAudioDecoderManager(
        connectionViewModel = connectionVM,
        sessionViewModel = sessionVM,
        sessionId = sessionId,
        audioStream = audioStream,
        audioVolume = audioVolume,
    )

    // 视频解码器管理
    val videoDecoderManager =
        rememberVideoDecoderManager(
            connectionViewModel = connectionVM,
            sessionViewModel = sessionVM,
            sessionId = sessionId,
            sessionData = sessionData,
            videoStream = videoStream,
            surfaceHolder = surfaceHolder,
            lifecycleState = lifecycleState,
            onVideoSizeChanged = { w, h, aspectRatio ->
                videoWidth = w
                videoHeight = h
                videoAspectRatio = aspectRatio
            },
        )

    // 拦截返回键
    // - 连接中/重连中：取消连接并返回主目录
    // - 已连接：传递给远程设备
    BackHandler(
        enabled =
            connectionState is ConnectionState.Connected ||
                connectionState is ConnectionState.Connecting ||
                connectionState is ConnectionState.Reconnecting,
    ) {
        when (connectionState) {
            is ConnectionState.Connected -> {
                // 已连接：发送返回键给远程设备
                scope.launch {
                    val result = controlVM.sendKeyEvent(4) // KEYCODE_BACK
                    if (result.isFailure) {
                        LogManager.e(
                            LogTags.REMOTE_DISPLAY,
                            "发送返回键失败: ${result.exceptionOrNull()?.message}",
                        )
                    }
                }
            }

            is ConnectionState.Connecting,
            is ConnectionState.Reconnecting,
            -> {
                // 连接中/重连中：取消连接并返回主目录
                connectionVM.cancelConnect()
            }

            else -> {
                // 其他状态：不处理
            }
        }
    }

    // 主界面布局
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 悬浮球（仅在视频流存在时显示）
            if (videoStream != null) {
                AutoFloatingMenu(viewModel = mainViewModel)
            }

            // 视频显示区域
            VideoDisplayArea(
                controlViewModel = controlVM,
                connectionViewModel = connectionVM,
                sessionData = sessionData,
                videoAspectRatio = videoAspectRatio,
                configuration = configuration,
                surfaceHolder = surfaceHolder,
                onSurfaceHolderChanged = { surfaceHolder = it },
                videoDecoderManager = videoDecoderManager,
            )

            // 连接状态覆盖层
            ConnectionStateOverlay(
                connectionState = connectionState,
                messageListState = messageListState,
                onReconnect = { connectionVM.connectSession(sessionId) },
                onClose = onClose,
            )

            // 键盘输入处理
            if (showKeyboardInput) {
                KeyboardInputHandler(
                    controlViewModel = controlVM,
                    keyboardController = keyboardController,
                    onDismiss = { showKeyboardInput = false },
                )
            }
        }
    }
}
