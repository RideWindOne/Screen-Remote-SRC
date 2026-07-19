package com.screen.remote.android.feature.remote.widget.floating

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

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
        kotlinx.coroutines.delay(300)
        isInitialized = true
    }

    // 监听屏幕旋转，重新定位小球
    LaunchedEffect(configuration.orientation) {
        if (isInitialized) {
            hideDualBallSystem(ballSystemReference)
            ballSystemReference = null
            kotlinx.coroutines.delay(50)
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

/**
 * 悬浮菜单控制器组件（自动显示版本 - 直接模式，不使用 Service）
 * 在 RemoteDisplayScreen 中自动显示悬浮球
 * 注意：此模式在后台容易被系统杀掉，推荐使用 AutoFloatingMenu（Service 模式）
 * @param actions 悬浮菜单动作边界
 */
@Composable
fun AutoFloatingMenuDirect(actions: FloatingMenuActions) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    var ballSystemReference by remember { mutableStateOf<BallSystemReference?>(null) }
    var isInitialized by remember { mutableStateOf(false) }

    // 自动显示悬浮球
    LaunchedEffect(Unit) {
        ballSystemReference = showDualBallSystem(context, actions, scope)
        // 延迟启用旋转监听，避免初始化时的配置抖动
        kotlinx.coroutines.delay(300)
        isInitialized = true
    }

    // 监听屏幕旋转，重新定位小球
    LaunchedEffect(configuration.orientation) {
        if (isInitialized) {
            hideDualBallSystem(ballSystemReference)
            ballSystemReference = null
            kotlinx.coroutines.delay(50)
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

/**
 * App 首页悬浮菜单控制器组件，提供双球体系统的悬浮窗交互功能。
 */
@Composable
fun FloatingMenuController(actions: FloatingMenuActions) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    var isFloatingShown by remember { mutableStateOf(false) }
    var ballSystemReference by remember { mutableStateOf<BallSystemReference?>(null) }
    var lastOrientation by remember { mutableIntStateOf(configuration.orientation) }

    // 监听屏幕旋转，重新定位小球
    LaunchedEffect(configuration.orientation) {
        if (isFloatingShown && lastOrientation != configuration.orientation) {
            hideDualBallSystem(ballSystemReference)
            ballSystemReference = null
            kotlinx.coroutines.delay(50)
            ballSystemReference = showDualBallSystem(context, actions, scope)
            lastOrientation = configuration.orientation
        } else if (isFloatingShown && ballSystemReference != null) {
            lastOrientation = configuration.orientation
        }
    }

    IconButton(onClick = {
        if (!isFloatingShown) {
            ballSystemReference = showDualBallSystem(context, actions, scope)
            isFloatingShown = true
        } else {
            hideDualBallSystem(ballSystemReference)
            ballSystemReference = null
            isFloatingShown = false
        }
    }) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "测试悬浮窗",
            tint = if (isFloatingShown) Color(0xFFFF3B30) else Color(0xFF007AFF),
        )
    }
}
