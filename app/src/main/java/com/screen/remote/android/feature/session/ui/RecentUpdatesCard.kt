package com.screen.remote.android.feature.session.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.screen.remote.android.core.common.constants.AppConstants
import com.screen.remote.android.core.i18n.TextPair
import com.screen.remote.android.feature.session.update.RecentUpdateContent
import com.screen.remote.android.feature.session.update.latestRecentUpdateContent

/**
 * “最近更新”卡片的唯一维护入口。
 *
 * 发布带更新说明的版本时，在更新内容目录中增加版本节点，并在这里提供对应页面。
 * 用户确认后会记录当前应用版本；只有出现更高的内容版本节点时才会再次展示。
 */
@Composable
fun RecentUpdatesCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val content = remember { latestRecentUpdateContent(AppConstants.APP_VERSION) }
    val pages =
        remember(isDark, content) {
            when (content) {
                RecentUpdateContent.RELEASE_4_4_3_8 -> release4438UpdatePages(isDark)
                null -> emptyList()
            }
        }
    if (pages.isEmpty()) return

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

private fun release4438UpdatePages(isDark: Boolean): List<WelcomePage> {
    val teal = if (isDark) Color(0xFF40C8E0) else Color(0xFF1597A8)
    val blue = if (isDark) Color(0xFF64A8FF) else Color(0xFF007AFF)
    val green = if (isDark) Color(0xFF30D158) else Color(0xFF28A745)
    val purple = if (isDark) Color(0xFFBF5AF2) else Color(0xFFAF52DE)

    return listOf(
        WelcomePage(
            icon = Icons.Default.AutoAwesome,
            eyebrow =
                updateText(
                    "${RecentUpdateContent.RELEASE_4_4_3_8.version} · AI / MCP 链接",
                    "${RecentUpdateContent.RELEASE_4_4_3_8.version} · AI / MCP LINKS",
                ),
            title = updateText("通过 URL Scheme 调用 Screen Remote", "Call Screen Remote through URL schemes"),
            description =
                updateText(
                    "AI 或 MCP 工具现在可以生成并打开 screen-remote:// 链接，将你的指令直接交给 Screen Remote 执行。",
                    "AI or MCP tools can now generate and open screen-remote:// links, handing supported requests directly to Screen Remote.",
                ),
            accent = blue,
            features =
                listOf(
                    WelcomeFeature(
                        Icons.AutoMirrored.Filled.AltRoute,
                        updateText("统一的调用入口", "One invocation entry point"),
                        updateText(
                            "URL Scheme 将 AI / MCP 请求转换为应用内的连接、管理、导航与设置操作。",
                            "URL schemes turn AI / MCP requests into supported connection, management, navigation, and settings actions.",
                        ),
                    ),
                    WelcomeFeature(
                        Icons.Default.Terminal,
                        updateText("直达设备操作", "Jump to device actions"),
                        updateText(
                            "可按会话名称或设备地址发起投屏，或直接打开文件、命令等设备管理页面。",
                            "Start mirroring by session name or device address, or open device management pages such as files and commands.",
                        ),
                    ),
                    WelcomeFeature(
                        Icons.Default.Tune,
                        updateText("参数随链接传入", "Pass options in the link"),
                        updateText(
                            "连接、显示、音视频与应用设置参数可随链接一起传入，便于 AI / MCP 组合调用。",
                            "Connection, display, media, and app settings can travel with the link for composed AI / MCP calls.",
                        ),
                    ),
                ),
        ),
        WelcomePage(
            icon = Icons.Default.Devices,
            eyebrow = updateText("兼容与旧设备", "COMPATIBILITY & OLDER DEVICES"),
            title = updateText("无法编码，也能继续连接", "Stay connected when video encoding fails"),
            description =
                updateText(
                    "新增基于 ADB 的兼容模式，并加强旧版 ADB 协议支持，让更多电视、盒子和旧设备可以显示与控制。",
                    "A new ADB-based compatibility mode and stronger legacy protocol support keep more TVs, boxes, and older devices usable.",
                ),
            accent = teal,
            features =
                listOf(
                    WelcomeFeature(
                        Icons.Default.HighQuality,
                        updateText("ADB 截图兼容模式", "ADB screenshot compatibility mode"),
                        updateText(
                            "绕过 scrcpy 视频编码，以低帧率截图显示画面，并继续提供点击、滑动和按键控制。",
                            "Bypass scrcpy video encoding with low-frame-rate screenshots while retaining taps, swipes, and key controls.",
                        ),
                    ),
                    WelcomeFeature(
                        Icons.AutoMirrored.Filled.AltRoute,
                        updateText("自动兼容旧版 Shell", "Automatic legacy shell fallback"),
                        updateText(
                            "设备不支持或拒绝 shell_v2 时，dadb 会自动降级到旧版 Shell，不再直接中断连接。",
                            "When a device does not support or rejects shell_v2, dadb automatically falls back to legacy shell instead of stopping the connection.",
                        ),
                    ),
                    WelcomeFeature(
                        Icons.Default.Terminal,
                        updateText("文本输入与粘贴", "Text input and paste"),
                        updateText(
                            "兼容模式仍可输入和粘贴文本；自动粘贴板同步继续由普通 scrcpy 模式提供。",
                            "Compatibility mode still supports text input and paste; automatic clipboard sync remains available in standard scrcpy mode.",
                        ),
                    ),
                ),
        ),
        WelcomePage(
            icon = Icons.Default.HighQuality,
            eyebrow = updateText("显示与操控", "DISPLAY & CONTROL"),
            title = updateText("画面恢复更清楚，方向更可控", "Clearer recovery and better rotation control"),
            description =
                updateText(
                    "连接后的显示策略与失败恢复得到加强，遇到分辨率或采集问题时会给出明确选择。",
                    "Display policies and failure recovery are stronger, with clear choices when resolution or capture problems occur.",
                ),
            accent = green,
            features =
                listOf(
                    WelcomeFeature(
                        Icons.Default.Tune,
                        updateText("本机 / 目标旋转策略", "Local or target rotation"),
                        updateText(
                            "可让画面方向跟随本机自由旋转，或限制本机方向并跟随目标设备。",
                            "Let rotation follow the local device freely, or constrain it to follow the target device.",
                        ),
                    ),
                    WelcomeFeature(
                        Icons.Default.Update,
                        updateText("渲染恢复提示", "Rendering recovery prompts"),
                        updateText(
                            "解码器或原画采集不支持当前尺寸时，可确认临时降低 maxSize 并重连，不修改已保存配置。",
                            "If decoding or native capture cannot handle the current size, confirm a temporary maxSize reduction and reconnect without changing saved settings.",
                        ),
                    ),
                    WelcomeFeature(
                        Icons.Default.Bolt,
                        updateText("遥控细节加强", "Improved remote controls"),
                        updateText(
                            "硬件音量键可转发到目标设备，兼容模式首帧到达前也会保持正确的加载提示。",
                            "Hardware volume keys can be forwarded to the target, and compatibility mode now keeps the correct loading state until its first frame.",
                        ),
                    ),
                ),
        ),
        WelcomePage(
            icon = Icons.Default.Terminal,
            eyebrow = updateText("设备管理与诊断", "MANAGEMENT & DIAGNOSTICS"),
            title = updateText("管理更快，排查更直接", "Faster management and clearer diagnostics"),
            description =
                updateText(
                    "设备信息、文件、进程、应用与命令工具都得到优化，并补充了更完整的诊断与隐私控制。",
                    "Device details, files, processes, apps, and command tools are improved, alongside stronger diagnostics and privacy controls.",
                ),
            accent = purple,
            features =
                listOf(
                    WelcomeFeature(
                        Icons.Default.Speed,
                        updateText("连接后提前加载", "Prefetch after connection"),
                        updateText(
                            "设备与应用信息会按会话预取，进入管理页面时减少重复等待。",
                            "Device and app information is prefetched per session to reduce repeated waits when opening management pages.",
                        ),
                    ),
                    WelcomeFeature(
                        Icons.Default.Folder,
                        updateText("文件、进程与应用", "Files, processes, and apps"),
                        updateText(
                            "dadb helper 提供更可靠的目录、进程和应用信息；应用列表支持升降序切换。",
                            "The dadb helper now provides more reliable directory, process, and app data, with reversible app sorting.",
                        ),
                    ),
                    WelcomeFeature(
                        Icons.Default.BugReport,
                        updateText("终端、日志与隐私", "Terminal, logs, and privacy"),
                        updateText(
                            "终端新增补全与命令分隔，调试诊断更完整，并可在设置中关闭匿名遥测。",
                            "The terminal adds completion and command separators, diagnostics are more complete, and anonymous telemetry can be disabled in Settings.",
                        ),
                    ),
                ),
        ),
    )
}
