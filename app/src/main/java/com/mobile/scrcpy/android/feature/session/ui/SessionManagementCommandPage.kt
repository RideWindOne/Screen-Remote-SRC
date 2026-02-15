package com.mobile.scrcpy.android.feature.session.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbBridge
import com.mobile.scrcpy.android.infrastructure.adb.shell.AdbShellManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val SESSION_MANAGEMENT_COMMAND_HISTORY_MAX_SIZE = 12

private const val SESSION_MANAGEMENT_COMMAND_OUTPUT_LIMIT = 16_000

data class ManagementCommandRecord(
    val command: String,
    val output: String,
    val isSuccess: Boolean,
    val executedAtMillis: Long,
    val durationMs: Long,
)

private data class ManagementCommandPreset(
    val title: String,
    val description: String,
    val command: String,
    val icon: ImageVector,
    val accent: Color,
)

@Composable
internal fun SessionManagementCommandPage(
    modifier: Modifier = Modifier,
    commandInput: String,
    history: List<ManagementCommandRecord>,
    isExecuting: Boolean,
    onCommandInputChange: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
    onClearHistory: () -> Unit,
    showPresetDialog: Boolean,
    onShowPresetDialogChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(isExecuting) {
        if (isExecuting) {
            Toast.makeText(context, "正在执行，请稍候…", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 终端显示区域 - 无左右 padding
        SessionManagementTerminalDisplay(
            history = history,
            isExecuting = isExecuting,
            onClearHistory = onClearHistory,
        )

        // 命令输入框
        SessionManagementCommandInputCard(
            commandInput = commandInput,
            isExecuting = isExecuting,
            onCommandInputChange = onCommandInputChange,
            onExecuteCommand = onExecuteCommand,
        )
    }

    // 快捷命令弹窗
    if (showPresetDialog) {
        SessionManagementCommandPresetDialog(
            isExecuting = isExecuting,
            onExecuteCommand = { command ->
                onExecuteCommand(command)
                onShowPresetDialogChange(false)
            },
            onDismiss = { onShowPresetDialogChange(false) },
        )
    }
}

@Composable
private fun SessionManagementTerminalDisplay(
    history: List<ManagementCommandRecord>,
    isExecuting: Boolean,
    onClearHistory: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E), // 深色终端背景
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 终端内容区域
        SessionManagementTerminalContent(
            history = history,
            isExecuting = isExecuting,
            onClearHistory = onClearHistory,
        )
    }
}

@Composable
private fun SessionManagementTerminalContent(
    history: List<ManagementCommandRecord>,
    isExecuting: Boolean,
    onClearHistory: () -> Unit,
) {
    val scrollState = rememberScrollState()

    // 自动滚动到底部
    LaunchedEffect(history.size, isExecuting) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 400.dp)
                    .verticalScroll(scrollState)
                    .padding(14.dp),
        ) {
            if (history.isEmpty()) {
                // 空状态提示
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "$ # 欢迎使用 Shell 终端",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        color = Color(0xFF6A9955), // 绿色注释
                    )
                    Text(
                        text = "$ # 输入命令后按回车执行，或使用下方快捷命令",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        color = Color(0xFF6A9955),
                    )
                }
            } else {
                SelectionContainer {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        history.asReversed().forEach { record ->
                            SessionManagementTerminalEntry(record = record)
                        }

                        if (isExecuting) {
                            Text(
                                text = "$ 执行中...",
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                color = Color(0xFF4EC9B0), // 青色
                            )
                        }
                    }
                }
            }
        }

        // 清空按钮 - 右上角小 X
        if (history.isNotEmpty()) {
            IconButton(
                onClick = onClearHistory,
                enabled = !isExecuting,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "清空",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SessionManagementTerminalEntry(record: ManagementCommandRecord) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 命令行
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "$",
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                color = Color(0xFF4EC9B0), // 青色提示符
            )
            Text(
                text = record.command,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                color = Color(0xFFD4D4D4), // 浅灰色命令
            )
        }

        // 输出
        if (record.output.isNotBlank()) {
            Text(
                text = record.output,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                color =
                    if (record.isSuccess) {
                        Color(0xFFCCCCCC) // 成功输出：浅灰
                    } else {
                        Color(0xFFF48771) // 错误输出：红色
                    },
            )
        }

        // 状态行
        Text(
            text = "# ${if (record.isSuccess) "✓" else "✗"} ${record.executedAtLabel()} (${
                formatCommandDuration(
                    record.durationMs,
                )
            })",
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
            color = Color(0xFF6A9955), // 绿色注释
        )
    }
}

@Composable
private fun SessionManagementCommandInputCard(
    commandInput: String,
    isExecuting: Boolean,
    onCommandInputChange: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
) {
    val normalizedCommand = commandInput.trim()
    val inputScrollState = rememberScrollState()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 112.dp, max = 220.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "$",
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier
                                    .padding(start = 12.dp, top = 14.dp),
                        )

                        BasicTextField(
                            value = commandInput,
                            onValueChange = onCommandInputChange,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .verticalScroll(inputScrollState)
                                    .padding(start = 8.dp, end = 56.dp, top = 12.dp, bottom = 44.dp),
                            textStyle =
                                TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions =
                                KeyboardOptions(
                                    imeAction = ImeAction.Send,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onSend = {
                                        if (normalizedCommand.isNotBlank() && !isExecuting) {
                                            onExecuteCommand(normalizedCommand)
                                        }
                                    },
                                ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.TopStart,
                                ) {
                                    if (commandInput.isEmpty()) {
                                        Text(
                                            text = "输入 Shell 命令\n支持长命令换行",
                                            style =
                                                MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    lineHeight = 22.sp,
                                                ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    }

                    IconButton(
                        onClick = { onExecuteCommand(normalizedCommand) },
                        enabled = normalizedCommand.isNotBlank() && !isExecuting,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "执行命令",
                            tint =
                                if (normalizedCommand.isNotBlank() && !isExecuting) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionManagementCommandPresetDialog(
    isExecuting: Boolean,
    onExecuteCommand: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier =
                Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.65f),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 标题栏
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "快捷命令",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                AppDivider()

                // 命令列表
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    managementCommandPresets().forEach { preset ->
                        SessionManagementCommandPresetCard(
                            preset = preset,
                            isExecuting = isExecuting,
                            onExecuteCommand = onExecuteCommand,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionManagementCommandPresetCard(
    preset: ManagementCommandPreset,
    isExecuting: Boolean,
    onExecuteCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isExecuting) { onExecuteCommand(preset.command) }
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = preset.accent.copy(alpha = 0.14f),
                ) {
                    Icon(
                        imageVector = preset.icon,
                        contentDescription = null,
                        tint = preset.accent,
                        modifier =
                            Modifier
                                .padding(8.dp)
                                .size(18.dp),
                    )
                }

                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = preset.command,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal suspend fun executeManagementShellCommand(command: String): ManagementCommandRecord {
    val normalizedCommand = command.trim()
    val executedAtMillis = System.currentTimeMillis()
    val connection = AdbBridge.getConnection()

    if (connection == null) {
        return ManagementCommandRecord(
            command = normalizedCommand,
            output = "当前没有可用的 ADB 连接。",
            isSuccess = false,
            executedAtMillis = executedAtMillis,
            durationMs = 0L,
        )
    }

    val result =
        AdbShellManager.execute(
            connection = connection,
            command = normalizedCommand,
            retryOnFailure = false,
        )
    val durationMs = (System.currentTimeMillis() - executedAtMillis).coerceAtLeast(0L)

    return result.fold(
        onSuccess = { output ->
            ManagementCommandRecord(
                command = normalizedCommand,
                output = normalizeManagementCommandOutput(output, success = true),
                isSuccess = true,
                executedAtMillis = executedAtMillis,
                durationMs = durationMs,
            )
        },
        onFailure = { error ->
            ManagementCommandRecord(
                command = normalizedCommand,
                output = normalizeManagementCommandOutput(error.message.orEmpty(), success = false),
                isSuccess = false,
                executedAtMillis = executedAtMillis,
                durationMs = durationMs,
            )
        },
    )
}

private fun normalizeManagementCommandOutput(
    raw: String,
    success: Boolean,
): String {
    val normalized =
        raw
            .trim()
            .ifBlank {
                if (success) {
                    "命令执行完成，无输出。"
                } else {
                    "命令执行失败。"
                }
            }

    if (normalized.length <= SESSION_MANAGEMENT_COMMAND_OUTPUT_LIMIT) {
        return normalized
    }

    return buildString {
        append(normalized.take(SESSION_MANAGEMENT_COMMAND_OUTPUT_LIMIT))
        append("\n\n")
        append("...输出已截断，原始内容超过 ")
        append(SESSION_MANAGEMENT_COMMAND_OUTPUT_LIMIT)
        append(" 个字符。")
    }
}

private fun formatCommandDuration(durationMs: Long): String =
    when {
        durationMs < 1_000L -> "${durationMs}ms"
        else -> String.format(Locale.US, "%.2fs", durationMs / 1_000f)
    }

private fun ManagementCommandRecord.executedAtLabel(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(executedAtMillis))

private fun managementCommandPresets(): List<ManagementCommandPreset> =
    listOf(
        ManagementCommandPreset(
            title = "设备概览",
            description = "快速确认品牌、型号和系统版本。",
            command =
                "echo Manufacturer: \$(getprop ro.product.manufacturer) && " +
                    "echo Model: \$(getprop ro.product.model) && " +
                    "echo Android: \$(getprop ro.build.version.release)",
            icon = Icons.Default.Android,
            accent = Color(0xFF53A7FF),
        ),
        ManagementCommandPreset(
            title = "屏幕参数",
            description = "读取当前分辨率和 DPI 状态。",
            command = "wm size && wm density",
            icon = Icons.Default.CropFree,
            accent = Color(0xFFFFA94D),
        ),
        ManagementCommandPreset(
            title = "前台页面",
            description = "定位当前焦点窗口和前台 Activity。",
            command = "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'",
            icon = Icons.Default.Search,
            accent = Color(0xFF7B61FF),
        ),
        ManagementCommandPreset(
            title = "第三方应用",
            description = "列出用户安装应用，便于排障或核对包名。",
            command = "pm list packages -3 | head -n 80",
            icon = Icons.Default.Apps,
            accent = Color(0xFF4CB782),
        ),
        ManagementCommandPreset(
            title = "网络状态",
            description = "查看 WLAN 地址和当前无线调试端口。",
            command = "ip addr show wlan0 | grep -m 1 'inet ' && getprop service.adb.tcp.port",
            icon = Icons.Default.Wifi,
            accent = Color(0xFF12B7A2),
        ),
        ManagementCommandPreset(
            title = "Logcat 快照",
            description = "抓取最近 120 行日志，适合先做一次快照排查。",
            command = "logcat -d -t 120",
            icon = Icons.Default.Code,
            accent = Color(0xFF5F6B7A),
        ),
        ManagementCommandPreset(
            title = "内存使用",
            description = "查看系统内存使用情况和可用内存。",
            command = "dumpsys meminfo | grep -E 'Total RAM|Free RAM|Used RAM'",
            icon = Icons.Default.Android,
            accent = Color(0xFFFF6B9D),
        ),
        ManagementCommandPreset(
            title = "电池信息",
            description = "查看电池电量、温度和充电状态。",
            command = "dumpsys battery | grep -E 'level|temperature|status'",
            icon = Icons.Default.Android,
            accent = Color(0xFF4CAF50),
        ),
        ManagementCommandPreset(
            title = "CPU 信息",
            description = "查看 CPU 架构和核心数量。",
            command = "cat /proc/cpuinfo | grep -E 'processor|Hardware|model name' | head -n 10",
            icon = Icons.Default.Android,
            accent = Color(0xFFFF9800),
        ),
        ManagementCommandPreset(
            title = "存储空间",
            description = "查看内部存储和 SD 卡的使用情况。",
            command = "df -h | grep -E '/data|/storage'",
            icon = Icons.Default.Android,
            accent = Color(0xFF9C27B0),
        ),
        ManagementCommandPreset(
            title = "正在运行的进程",
            description = "列出当前正在运行的应用进程。",
            command = "ps -A | grep -v '\\[' | head -n 30",
            icon = Icons.Default.Apps,
            accent = Color(0xFF00BCD4),
        ),
        ManagementCommandPreset(
            title = "清理后台应用",
            description = "强制停止所有后台应用释放内存。",
            command = "am kill-all",
            icon = Icons.Default.DeleteOutline,
            accent = Color(0xFFFF5252),
        ),
        ManagementCommandPreset(
            title = "系统属性",
            description = "查看关键系统属性信息。",
            command = "getprop | grep -E 'ro.build|ro.product|ro.hardware'",
            icon = Icons.Default.Info,
            accent = Color(0xFF607D8B),
        ),
    )
