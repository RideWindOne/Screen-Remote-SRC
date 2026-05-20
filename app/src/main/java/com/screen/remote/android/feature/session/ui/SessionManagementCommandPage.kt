package com.screen.remote.android.feature.session.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.infrastructure.adb.connection.AdbBridge
import com.screen.remote.android.infrastructure.adb.shell.AdbShellManager
import dadb.AdbShellPacket
import dadb.AdbShellStream
import dadb.ID_CLOSE_STDIN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val SESSION_MANAGEMENT_COMMAND_HISTORY_MAX_SIZE = 12

private const val SESSION_MANAGEMENT_COMMAND_OUTPUT_LIMIT = 16_000
private const val SESSION_MANAGEMENT_SHELL_KEEPALIVE_INTERVAL_MS = 20_000L
private const val SESSION_MANAGEMENT_SHELL_RECONNECT_DELAY_MS = 1_200L
private const val SESSION_MANAGEMENT_SHELL_MAX_RECONNECT_ATTEMPTS = 3
private val SESSION_MANAGEMENT_ANSI_PATTERN =
    Regex("""\u001B(?:\[[0-?]*[ -/]*[@-~]|\][^\u0007]*(?:\u0007|\u001B\\)|[PX^_].*?\u001B\\|[@-Z\\-_])""")

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
    terminalSession: ManagementTerminalSession,
    commandInput: String,
    history: List<ManagementCommandRecord>,
    isExecuting: Boolean,
    onCommandInputChange: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
    onClearHistory: () -> Unit,
    showPresetDialog: Boolean,
    onShowPresetDialogChange: (Boolean) -> Unit,
) {
    LaunchedEffect(terminalSession) {
        terminalSession.start()
    }

    SessionManagementTerminalDisplay(
        modifier =
            modifier
                .fillMaxWidth()
                .imePadding(),
        output = terminalSession.output,
        isConnected = terminalSession.isConnected,
        commandInput = commandInput,
        inputEnabled = terminalSession.canWrite,
        onCommandInputChange = onCommandInputChange,
        onExecuteCommand = { command ->
            terminalSession.sendLine(command)
            onCommandInputChange("")
        },
        onClear = terminalSession::clear,
    )

    // 快捷命令弹窗
    if (showPresetDialog) {
        SessionManagementCommandPresetDialog(
            isExecuting = isExecuting,
            onExecuteCommand = { command ->
                terminalSession.sendLine(command)
                onCommandInputChange("")
                onShowPresetDialogChange(false)
            },
            onDismiss = { onShowPresetDialogChange(false) },
        )
    }
}

@Composable
private fun SessionManagementTerminalDisplay(
    modifier: Modifier = Modifier,
    output: String,
    isConnected: Boolean,
    commandInput: String,
    inputEnabled: Boolean,
    onCommandInputChange: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = managementPanelColor(),
        modifier = modifier,
        tonalElevation = 0.5.dp,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(8.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
            ) {
                SessionManagementTerminalContent(
                    modifier = Modifier.weight(1f),
                    output = output,
                    isConnected = isConnected,
                    onClear = onClear,
                )

                AppDivider()

                SessionManagementTerminalInputLine(
                    commandInput = commandInput,
                    inputEnabled = inputEnabled,
                    onCommandInputChange = onCommandInputChange,
                    onExecuteCommand = onExecuteCommand,
                )
            }
        }
    }
}

@Composable
private fun SessionManagementTerminalContent(
    modifier: Modifier = Modifier,
    output: String,
    isConnected: Boolean,
    onClear: () -> Unit,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(output.length, isConnected) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .heightIn(min = 150.dp)
                    .verticalScroll(scrollState)
                    .padding(14.dp),
        ) {
            SelectionContainer {
                Text(
                    text =
                        output.ifBlank {
                            if (isConnected) {
                                ManagementTexts.text("# 已连接交互式 shell。", "# Interactive shell connected.")
                            } else {
                                ManagementTexts.text("# 正在打开交互式 shell...", "# Opening interactive shell...")
                            }
                        },
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 19.sp,
                        ),
                    color = Color(0xFFCCCCCC),
                )
            }
        }

        if (output.isNotEmpty()) {
            IconButton(
                onClick = onClear,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = ManagementTexts.text("清空", "Clear"),
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
private fun SessionManagementTerminalInputLine(
    commandInput: String,
    inputEnabled: Boolean,
    onCommandInputChange: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
) {
    val normalizedCommand = commandInput.trim()
    val inputScrollState = rememberScrollState()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$",
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
            color = Color(0xFF4EC9B0),
            modifier =
                Modifier
                    .padding(top = 9.dp, end = 8.dp),
        )

        BasicTextField(
            value = commandInput,
            onValueChange = onCommandInputChange,
            enabled = inputEnabled,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 132.dp)
                    .verticalScroll(inputScrollState)
                    .padding(top = 8.dp, bottom = 8.dp),
            textStyle =
                TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFFD4D4D4),
                ),
            cursorBrush = SolidColor(Color(0xFF4EC9B0)),
            keyboardOptions =
                KeyboardOptions(
                    imeAction = ImeAction.Send,
                ),
            keyboardActions =
                KeyboardActions(
                    onSend = {
                        if (normalizedCommand.isNotBlank() && inputEnabled) {
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
                            text =
                                if (inputEnabled) {
                                    ManagementTexts.text("输入 Shell 命令", "Enter a shell command")
                                } else {
                                    ManagementTexts.text("正在连接 shell...", "Connecting to shell...")
                                },
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 22.sp,
                                ),
                            color = Color(0xFFCCCCCC).copy(alpha = 0.46f),
                        )
                    }
                    innerTextField()
                }
            },
        )

        IconButton(
            onClick = { onExecuteCommand(normalizedCommand) },
            enabled = normalizedCommand.isNotBlank() && inputEnabled,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = ManagementTexts.text("执行命令", "Run command"),
                tint =
                    if (normalizedCommand.isNotBlank() && inputEnabled) {
                        Color(0xFF4EC9B0)
                    } else {
                        Color(0xFFCCCCCC).copy(alpha = 0.32f)
                    },
            )
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
                    .fillMaxWidth(0.98f)
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
                        text = ManagementTexts.text("快捷命令", "Quick commands"),
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
        color = managementPanelColor(),
        tonalElevation = 0.5.dp,
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

internal class ManagementTerminalSession(
    private val scope: CoroutineScope,
) {
    var output by mutableStateOf("")
        private set
    var isConnected by mutableStateOf(false)
        private set

    private var shellStream: AdbShellStream? = null
    private var readJob: Job? = null
    private var keepAliveJob: Job? = null
    private var closeRequested = false
    private var userRequestedExit = false
    private var hasEverConnected = false
    private var reconnectAttempts = 0

    val canWrite: Boolean
        get() = isConnected && shellStream != null

    fun start() {
        if (readJob?.isActive == true) {
            return
        }

        closeRequested = false
        if (!hasEverConnected) {
            append("# 正在连接交互式 shell...\n")
        }
        readJob =
            scope.launch {
                val connection = AdbBridge.getConnection()
                if (connection == null) {
                    appendConnectionFailed()
                    return@launch
                }

                val stream = connection.openShellStream("")
                if (stream == null) {
                    appendConnectionFailed()
                    return@launch
                }

                shellStream = stream
                isConnected = true
                if (!hasEverConnected) {
                    append("# 已连接。\n")
                }
                hasEverConnected = true
                reconnectAttempts = 0
                startKeepAlive(stream)

                withContext(Dispatchers.IO) {
                    try {
                        while (true) {
                            when (val packet = stream.read()) {
                                is AdbShellPacket.StdOut -> {
                                    appendTerminalTextOnMain(String(packet.payload))
                                }

                                is AdbShellPacket.StdError -> {
                                    appendTerminalTextOnMain(String(packet.payload))
                                }

                                is AdbShellPacket.Exit -> {
                                    appendOnMain("\n# shell 已退出，exit=${packet.payload.firstOrNull()?.toInt() ?: 0}\n")
                                    break
                                }
                            }
                        }
                    } catch (error: Exception) {
                        // Idle shell streams may be closed by the transport. Reconnect silently below.
                    } finally {
                        keepAliveJob?.cancel()
                        keepAliveJob = null
                        withContext(Dispatchers.Main) {
                            isConnected = false
                            shellStream = null
                        }
                        runCatching { stream.close() }
                    }
                }

                readJob = null
                if (!closeRequested && !userRequestedExit) {
                    reconnectAttempts += 1
                    if (reconnectAttempts <= SESSION_MANAGEMENT_SHELL_MAX_RECONNECT_ATTEMPTS) {
                        delay(SESSION_MANAGEMENT_SHELL_RECONNECT_DELAY_MS)
                        start()
                    } else {
                        appendConnectionFailed()
                    }
                }
            }
    }

    fun sendLine(command: String) {
        val line = command.trimEnd()
        if (line.isBlank()) {
            return
        }

        if (line.trim() == "clear") {
            clear()
            return
        }

        val stream =
            shellStream ?: run {
                append("# shell 尚未连接。\n")
                return
            }

        scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    append("$ $line\n")
                }
                if (line.trim() == "exit") {
                    userRequestedExit = true
                }
                stream.write("$line\n")
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    append("\n# 写入失败: ${error.message.orEmpty()}\n")
                    close()
                }
            }
        }
    }

    fun clear() {
        output = ""
    }

    fun close() {
        closeRequested = true
        keepAliveJob?.cancel()
        keepAliveJob = null
        readJob?.cancel()
        readJob = null
        isConnected = false
        shellStream?.let { stream ->
            runCatching { stream.write(ID_CLOSE_STDIN) }
            runCatching { stream.close() }
        }
        shellStream = null
    }

    private fun startKeepAlive(stream: AdbShellStream) {
        keepAliveJob?.cancel()
        keepAliveJob =
            scope.launch(Dispatchers.IO) {
                while (!closeRequested && !userRequestedExit) {
                    delay(SESSION_MANAGEMENT_SHELL_KEEPALIVE_INTERVAL_MS)
                    runCatching {
                        stream.write(":\n")
                    }.onFailure {
                        return@launch
                    }
                }
            }
    }

    private fun append(text: String) {
        output = (output + text).takeLast(SESSION_MANAGEMENT_COMMAND_OUTPUT_LIMIT)
    }

    private fun appendConnectionFailed() {
        output = output.trimEnd()
        append("\n# 已断开。\n")
    }

    private fun appendTerminalText(text: String) {
        SESSION_MANAGEMENT_ANSI_PATTERN
            .replace(text, "")
            .forEach { char ->
                output =
                    when (char) {
                        '\r' -> {
                            val lineStart = output.lastIndexOf('\n') + 1
                            output.take(lineStart)
                        }

                        '\b' -> {
                            output.dropLast(1)
                        }

                        else -> {
                            output + char
                        }
                    }.takeLast(SESSION_MANAGEMENT_COMMAND_OUTPUT_LIMIT)
            }
    }

    private suspend fun appendOnMain(text: String) {
        withContext(Dispatchers.Main) {
            append(text)
        }
    }

    private suspend fun appendTerminalTextOnMain(text: String) {
        withContext(Dispatchers.Main) {
            appendTerminalText(text)
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
            title = ManagementTexts.text("设备概览", "Device overview"),
            description = ManagementTexts.text("快速确认品牌、型号和系统版本。", "Quick check of brand, model, and Android version."),
            command =
                "echo Manufacturer: \$(getprop ro.product.manufacturer) && " +
                    "echo Model: \$(getprop ro.product.model) && " +
                    "echo Android: \$(getprop ro.build.version.release)",
            icon = Icons.Default.Android,
            accent = Color(0xFF53A7FF),
        ),
        ManagementCommandPreset(
            title = ManagementTexts.text("屏幕参数", "Display metrics"),
            description = ManagementTexts.text("读取当前分辨率和 DPI 状态。", "Read the current resolution and DPI."),
            command = "wm size && wm density",
            icon = Icons.Default.CropFree,
            accent = Color(0xFFFFA94D),
        ),
        ManagementCommandPreset(
            title = ManagementTexts.text("前台页面", "Foreground window"),
            description = ManagementTexts.text("定位当前焦点窗口和前台 Activity。", "Locate the focused window and foreground activity."),
            command = "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'",
            icon = Icons.Default.Search,
            accent = Color(0xFF7B61FF),
        ),
        ManagementCommandPreset(
            title = ManagementTexts.text("第三方应用", "User apps"),
            description =
                ManagementTexts.text(
                    "列出用户安装应用，便于排障或核对包名。",
                    "List installed user apps for debugging and package checks.",
                ),
            command = "pm list packages -3 | head -n 80",
            icon = Icons.Default.Apps,
            accent = Color(0xFF4CB782),
        ),
        ManagementCommandPreset(
            title = ManagementTexts.text("网络状态", "Network status"),
            description = ManagementTexts.text("查看 WLAN 地址和当前无线调试端口。", "Show WLAN address and wireless debugging port."),
            command = "ip addr show wlan0 | grep -m 1 'inet ' && getprop service.adb.tcp.port",
            icon = Icons.Default.Wifi,
            accent = Color(0xFF12B7A2),
        ),
        ManagementCommandPreset(
            title = ManagementTexts.text("Logcat 快照", "Logcat snapshot"),
            description =
                ManagementTexts.text(
                    "抓取最近 120 行日志，适合先做一次快照排查。",
                    "Capture the latest 120 log lines for a quick check.",
                ),
            command = "logcat -d -t 120",
            icon = Icons.Default.Code,
            accent = Color(0xFF5F6B7A),
        ),
        ManagementCommandPreset(
            title = ManagementTexts.text("内存使用", "Memory usage"),
            description = ManagementTexts.text("查看系统内存使用情况和可用内存。", "Show total used and available memory."),
            command = "dumpsys meminfo | grep -E 'Total RAM|Free RAM|Used RAM'",
            icon = Icons.Default.Android,
            accent = Color(0xFFFF6B9D),
        ),
        ManagementCommandPreset(
            title = ManagementTexts.text("电池信息", "Battery info"),
            description = ManagementTexts.text("查看电池电量、温度和充电状态。", "Show battery level, temperature, and charging state."),
            command = "dumpsys battery | grep -E 'level|temperature|status'",
            icon = Icons.Default.Android,
            accent = Color(0xFF4CAF50),
        ),
        ManagementCommandPreset(
            title = ManagementTexts.text("CPU 信息", "CPU info"),
            description = ManagementTexts.text("查看 CPU 架构和核心数量。", "Show CPU architecture and core count."),
            command = "cat /proc/cpuinfo | grep -E 'processor|Hardware|model name' | head -n 10",
            icon = Icons.Default.Android,
            accent = Color(0xFFFF9800),
        ),
        ManagementCommandPreset(
            title = ManagementTexts.text("存储空间", "Storage"),
            description = ManagementTexts.text("查看内部存储和 SD 卡的使用情况。", "Show internal storage and SD card usage."),
            command = "df -h | grep -E '/data|/storage'",
            icon = Icons.Default.Android,
            accent = Color(0xFF9C27B0),
        ),
        ManagementCommandPreset(
            title = ManagementTexts.text("正在运行的进程", "Running processes"),
            description = ManagementTexts.text("列出当前正在运行的应用进程。", "List running app processes."),
            command = "ps -A | grep -v '\\[' | head -n 30",
            icon = Icons.Default.Apps,
            accent = Color(0xFF00BCD4),
        ),
        ManagementCommandPreset(
            title = ManagementTexts.text("清理后台应用", "Kill background apps"),
            description = ManagementTexts.text("强制停止所有后台应用释放内存。", "Force-stop background apps to free memory."),
            command = "am kill-all",
            icon = Icons.Default.DeleteOutline,
            accent = Color(0xFFFF5252),
        ),
        ManagementCommandPreset(
            title = ManagementTexts.text("系统属性", "System properties"),
            description = ManagementTexts.text("查看关键系统属性信息。", "Inspect key system properties."),
            command = "getprop | grep -E 'ro.build|ro.product|ro.hardware'",
            icon = Icons.Default.Info,
            accent = Color(0xFF607D8B),
        ),
    )
