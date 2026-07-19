package com.screen.remote.android.feature.session.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.screen.remote.android.core.common.constants.AppConstants
import com.screen.remote.android.core.i18n.TextPair

/**
 * “最近更新”卡片的唯一维护入口。
 *
 * 发布新版本时更新 [recentUpdatePages] 的页面和条目即可。用户确认后会记录
 * [AppConstants.APP_VERSION]，下一个版本号变化时这些卡片会再次出现。
 */
@Composable
fun RecentUpdatesCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val pages = remember(isDark) { recentUpdatePages(isDark) }

    SessionOnboardingPager(
        pages = pages,
        actionText = updateText("知道了", "Got it"),
        swipeHint = updateText("左右滑动，查看全部", "Swipe to see all"),
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

private fun updateText(
    chinese: String,
    english: String,
): String = TextPair(chinese, english).get()

private fun recentUpdatePages(isDark: Boolean): List<WelcomePage> {
    val teal = if (isDark) Color(0xFF40C8E0) else Color(0xFF1597A8)
    val blue = if (isDark) Color(0xFF64A8FF) else Color(0xFF007AFF)
    val green = if (isDark) Color(0xFF30D158) else Color(0xFF28A745)
    val purple = if (isDark) Color(0xFFBF5AF2) else Color(0xFFAF52DE)

    return listOf(
        WelcomePage(
            icon = Icons.Default.Sensors,
            eyebrow = updateText("${AppConstants.APP_VERSION} · 最近更新", "${AppConstants.APP_VERSION} · WHAT'S NEW"),
            title = updateText("无线调试，更快也更稳", "Wireless debugging, faster and steadier"),
            description =
                updateText(
                    "我们重做了 mDNS 无线调试的发现、在线状态与连接体验，让设备更容易被找到，也更快连上。",
                    "We refined mDNS discovery, presence tracking, and connection handling so devices are easier to find and faster to reach.",
                ),
            accent = teal,
            features =
                listOf(
                    WelcomeFeature(
                        Icons.Default.Sensors,
                        updateText("持续发现与在线提醒", "Continuous discovery"),
                        updateText("统一管理 mDNS 服务发现，设备重新上线时也能及时获知。", "mDNS discovery now stays coordinated and can notify you when a saved device comes back online."),
                    ),
                    WelcomeFeature(
                        Icons.AutoMirrored.Filled.AltRoute,
                        updateText("一个会话，多个地址", "Multiple addresses per session"),
                        updateText("同一会话可保存 TCP、USB 与 mDNS 主备地址，网络变化时自动尝试可用链路。", "Save TCP, USB, and mDNS routes in one session and automatically try an available path when the network changes."),
                    ),
                    WelcomeFeature(
                        Icons.Default.Speed,
                        updateText("地址竞速测试", "Endpoint speed test"),
                        updateText("在会话管理中测试全部无线地址，延迟结果一目了然。", "Test every wireless endpoint from session management and compare latency at a glance."),
                    ),
                ),
        ),
        WelcomePage(
            icon = Icons.Default.Bolt,
            eyebrow = updateText("连接与诊断", "CONNECTION & DIAGNOSTICS"),
            title = updateText("少等待，更好排查", "Less waiting, clearer diagnostics"),
            description =
                updateText(
                    "连接流程经过加速；遇到问题时，现在也有更直接的实时诊断入口。",
                    "The connection path is quicker, and live diagnostics are now close at hand when something goes wrong.",
                ),
            accent = blue,
            features =
                listOf(
                    WelcomeFeature(
                        Icons.Default.Bolt,
                        updateText("连接流程加速", "Faster connection flow"),
                        updateText("优化候选地址尝试与建链流程，缩短从点击到画面出现的等待。", "Candidate selection and connection setup were streamlined to reduce the wait from tap to first picture."),
                    ),
                    WelcomeFeature(
                        Icons.Default.BugReport,
                        updateText("实时调试日志", "Live debug logs"),
                        updateText("设置页新增调试按钮，可直接查看应用运行与各数据流日志。", "A new debug button in Settings opens live app and stream logs without leaving the app."),
                    ),
                    WelcomeFeature(
                        Icons.Default.Devices,
                        updateText("状态更准确", "More accurate status"),
                        updateText("主备地址统一聚合在线状态，并显示当前真正使用的连接类型。", "Primary and fallback routes now share one presence state while showing the transport actually in use."),
                    ),
                ),
        ),
        WelcomePage(
            icon = Icons.Default.SportsEsports,
            eyebrow = updateText("进阶玩法", "ADVANCED FEATURES"),
            title = updateText("端口、虚拟屏与游戏模式", "Ports, virtual displays, and game mode"),
            description =
                updateText(
                    "新增三项实用能力，覆盖服务访问、独立屏幕与低延迟游戏操控。",
                    "Three new capabilities cover service access, independent screens, and low-latency gaming controls.",
                ),
            accent = green,
            features =
                listOf(
                    WelcomeFeature(
                        Icons.AutoMirrored.Filled.AltRoute,
                        updateText("端口转发", "Port forwarding"),
                        updateText("为目标设备配置端口转发，直接访问设备上的网络服务。", "Configure forwarding for the target device and reach its network services directly."),
                    ),
                    WelcomeFeature(
                        Icons.Default.DesktopWindows,
                        updateText("虚拟屏", "Virtual display"),
                        updateText("创建独立虚拟屏运行 App 或启动器，不影响设备主屏。", "Run an app or launcher on an independent virtual display without disturbing the main screen."),
                    ),
                    WelcomeFeature(
                        Icons.Default.SportsEsports,
                        updateText("游戏模式", "Game mode"),
                        updateText("优化高帧率视频与触控处理，让操作更稳定、更跟手。", "Optimize high-frame-rate video and touch handling for steadier, more responsive controls."),
                    ),
                ),
        ),
        WelcomePage(
            icon = Icons.Default.HighQuality,
            eyebrow = updateText("播放能力与细节", "PLAYBACK & POLISH"),
            title = updateText("更强的音视频适配", "Broader audio and video support"),
            description =
                updateText(
                    "编解码能力检测、失败恢复和媒体格式支持都得到了加强，更多设备可以顺畅工作。",
                    "Codec detection, failure recovery, and media-format support have all been strengthened for smoother playback on more devices.",
                ),
            accent = purple,
            features =
                listOf(
                    WelcomeFeature(
                        Icons.Default.HighQuality,
                        updateText("编解码能力优化", "Codec improvements"),
                        updateText("新增 VP8/VP9 基础支持、解码能力预检，并在失败时自动降低画面尺寸重试。", "Added foundational VP8/VP9 support, decoder capability checks, and automatic size fallback after decode failures."),
                    ),
                    WelcomeFeature(
                        Icons.Default.Update,
                        updateText("scrcpy 4.1", "scrcpy 4.1"),
                        updateText("内置服务已更新到官方 scrcpy 4.1，连接和媒体能力同步升级。", "The bundled server is now based on official scrcpy 4.1, bringing its latest connection and media improvements."),
                    ),
                    WelcomeFeature(
                        Icons.Default.Folder,
                        updateText("还有更多细节", "And more polish"),
                        updateText("文件路径支持面包屑快速跳转，会话地址编辑与设备管理也有多项体验优化。", "File paths now have breadcrumb navigation, alongside numerous refinements to endpoint editing and device management."),
                    ),
                ),
        ),
    )
}
