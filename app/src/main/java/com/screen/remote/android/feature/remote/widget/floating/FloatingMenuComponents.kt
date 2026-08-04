package com.screen.remote.android.feature.remote.widget.floating

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * 悬浮菜单控制器组件（自动显示版本）
 * 在 RemoteDisplayScreen 中自动显示悬浮球
 *
 * 注意：此组件在连接设备后创建，ScrcpyForegroundService 已在运行
 * 触感反馈使用 Vibrator 服务，独立工作
 *
 * @param actions 悬浮菜单动作边界
 */
@Composable
fun AutoFloatingMenu(actions: FloatingMenuActions) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    var ballSystemReference by remember { mutableStateOf<BallSystemReference?>(null) }
    var isInitialized by remember { mutableStateOf(false) }

    // 在 Activity 中创建悬浮球
    LaunchedEffect(Unit) {
        ballSystemReference = showDualBallSystem(context, actions, scope)
        // 延迟启用旋转监听，避免初始化时的配置抖动
        kotlinx.coroutines.delay(300.milliseconds)
        isInitialized = true
    }

    // 监听屏幕旋转，重新定位小球
    LaunchedEffect(configuration.orientation) {
        if (isInitialized) {
            hideDualBallSystem(ballSystemReference)
            ballSystemReference = null
            kotlinx.coroutines.delay(50.milliseconds)
            ballSystemReference = showDualBallSystem(context, actions, scope)
        }
    }

    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            hideDualBallSystem(ballSystemReference)
            ballSystemReference = null
        }
    }
}

