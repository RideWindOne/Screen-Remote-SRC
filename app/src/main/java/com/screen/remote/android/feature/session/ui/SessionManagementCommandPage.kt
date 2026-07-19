package com.screen.remote.android.feature.session.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import com.screen.remote.android.core.i18n.ManagementTexts
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
private const val SESSION_MANAGEMENT_TERMINAL_MIN_TEXT_SCALE = 0.7f
private const val SESSION_MANAGEMENT_TERMINAL_MAX_TEXT_SCALE = 2.2f
private val SESSION_MANAGEMENT_ANSI_PATTERN =
    Regex("""\u001B(?:\[[0-?]*[ -/]*[@-~]|\][^\u0007]*(?:\u0007|\u001B\\)|[PX^_].*?\u001B\\|[@-Z\\-_])""")

private val SessionManagementCommandTextColor
    @Composable
    get() = sessionManagementTerminalPalette().text
private val SessionManagementCommandHintColor
    @Composable
    get() = sessionManagementTerminalPalette().hint
private val SessionManagementCommandPromptColor
    @Composable
    get() = sessionManagementTerminalPalette().accent
private val SessionManagementCommandErrorColor
    @Composable
    get() = sessionManagementTerminalPalette().error

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
    var enteredCommands by remember(terminalSession) { mutableStateOf<List<String>>(emptyList()) }
    var historyCursor by remember(terminalSession) { mutableStateOf<Int?>(null) }
    var inputBeforeHistory by remember(terminalSession) { mutableStateOf("") }
    val availableHistory = remember(enteredCommands, history) { (enteredCommands + history.map { it.command }).distinct() }

    fun sendCommand(command: String) {
        val normalized = command.trim()
        if (normalized.isBlank()) return
        terminalSession.sendLine(normalized)
        enteredCommands = (listOf(normalized) + enteredCommands).take(SESSION_MANAGEMENT_COMMAND_HISTORY_MAX_SIZE)
        historyCursor = null
        inputBeforeHistory = ""
        onCommandInputChange("")
    }

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
        onCommandInputChange = { value ->
            historyCursor = null
            inputBeforeHistory = value
            onCommandInputChange(value)
        },
        onExecuteCommand = ::sendCommand,
        historyPreviousEnabled = availableHistory.isNotEmpty() && (historyCursor == null || historyCursor!! < availableHistory.lastIndex),
        historyNextEnabled = historyCursor != null,
        onHistoryPrevious = {
            if (historyCursor == null) inputBeforeHistory = commandInput
            val nextIndex = ((historyCursor ?: -1) + 1).coerceAtMost(availableHistory.lastIndex)
            historyCursor = nextIndex
            onCommandInputChange(availableHistory[nextIndex])
        },
        onHistoryNext = {
            val currentIndex = historyCursor ?: return@SessionManagementTerminalDisplay
            if (currentIndex == 0) {
                historyCursor = null
                onCommandInputChange(inputBeforeHistory)
            } else {
                val nextIndex = currentIndex - 1
                historyCursor = nextIndex
                onCommandInputChange(availableHistory[nextIndex])
            }
        },
        onClear = terminalSession::clear,
    )

    // 快捷命令弹窗
    if (showPresetDialog) {
        SessionManagementCommandPresetDialog(
            isExecuting = isExecuting,
            onExecuteCommand = { command ->
                sendCommand(command)
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
    historyPreviousEnabled: Boolean,
    historyNextEnabled: Boolean,
    onHistoryPrevious: () -> Unit,
    onHistoryNext: () -> Unit,
    onClear: () -> Unit,
) {
    val terminalPalette = sessionManagementTerminalPalette()
    var textScale by rememberSaveable { mutableStateOf(1f) }
    val outputFontSize = SessionManagementTerminalTextTokens.outputFontSize * textScale
    val outputLineHeight = SessionManagementTerminalTextTokens.outputLineHeight * textScale
    val inputFontSize = SessionManagementTerminalTextTokens.inputFontSize * textScale
    val inputLineHeight = SessionManagementTerminalTextTokens.inputLineHeight * textScale
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = terminalPalette.background,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .terminalPinchZoom(
                        onZoom = { zoom ->
                            textScale =
                                (textScale * zoom).coerceIn(
                                    SESSION_MANAGEMENT_TERMINAL_MIN_TEXT_SCALE,
                                    SESSION_MANAGEMENT_TERMINAL_MAX_TEXT_SCALE,
                                )
                        },
                    )
                    .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
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
                    fontSize = outputFontSize,
                    lineHeight = outputLineHeight,
                    onClear = onClear,
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = terminalPalette.separator,
                )

                SessionManagementTerminalHistoryActions(
                    previousEnabled = historyPreviousEnabled,
                    nextEnabled = historyNextEnabled,
                    onPrevious = onHistoryPrevious,
                    onNext = onHistoryNext,
                )

                SessionManagementTerminalInputLine(
                    commandInput = commandInput,
                    inputEnabled = inputEnabled,
                    fontSize = inputFontSize,
                    lineHeight = inputLineHeight,
                    onCommandInputChange = onCommandInputChange,
                    onExecuteCommand = onExecuteCommand,
                )
            }
        }
    }
}

private fun Modifier.terminalPinchZoom(onZoom: (Float) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val pressedPointers = event.changes.filter { it.pressed }
                if (pressedPointers.size >= 2) {
                    val zoom = event.calculateZoom()
                    if (zoom.isFinite() && zoom > 0f && zoom != 1f) {
                        onZoom(zoom)
                    }
                    pressedPointers.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

@Composable
private fun SessionManagementTerminalHistoryActions(
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = previousEnabled,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = ManagementTexts.Commands.PREVIOUS_COMMAND.get(),
                tint = if (previousEnabled) SessionManagementCommandPromptColor else SessionManagementCommandHintColor.copy(alpha = 0.32f),
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = ManagementTexts.Commands.NEXT_COMMAND.get(),
                tint = if (nextEnabled) SessionManagementCommandPromptColor else SessionManagementCommandHintColor.copy(alpha = 0.32f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SessionManagementTerminalContent(
    modifier: Modifier = Modifier,
    output: String,
    isConnected: Boolean,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    onClear: () -> Unit,
) {
    val listState = rememberLazyListState()
    val displayLines =
        rememberTerminalOutputLines(
            output = output,
            isConnected = isConnected,
        )

    LaunchedEffect(displayLines.size, output.length, isConnected) {
        if (displayLines.isNotEmpty()) {
            listState.animateScrollToItem(displayLines.lastIndex)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .heightIn(min = 150.dp)
                        .padding(14.dp),
            ) {
                itemsIndexed(
                    items = displayLines,
                    key = { index, _ -> index },
                ) { _, line ->
                    Text(
                        text = line,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = SessionManagementTerminalTextTokens.monospace,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                            ),
                        color = SessionManagementCommandTextColor,
                    )
                }
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
                    contentDescription = ManagementTexts.Commands.CLEAR.get(),
                    tint = SessionManagementCommandErrorColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberTerminalOutputLines(
    output: String,
    isConnected: Boolean,
): List<String> =
    androidx.compose.runtime.remember(output, isConnected) {
        val displayOutput =
            output.ifBlank {
                if (isConnected) {
                    ManagementTexts.Commands.INTERACTIVE_SHELL_CONNECTED.get()
                } else {
                    ManagementTexts.Commands.OPENING_INTERACTIVE_SHELL.get()
                }
            }
        displayOutput.lineSequence().toList()
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
                        fontFamily = SessionManagementTerminalTextTokens.monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                color = SessionManagementCommandPromptColor,
            )
            Text(
                text = record.command,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = SessionManagementTerminalTextTokens.monospace,
                    ),
                color = SessionManagementCommandTextColor,
            )
        }

        // 输出
        if (record.output.isNotBlank()) {
            Text(
                text = record.output,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = SessionManagementTerminalTextTokens.monospace,
                    ),
                color =
                    if (record.isSuccess) {
                        SessionManagementCommandTextColor
                    } else {
                        SessionManagementCommandErrorColor
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
                    fontFamily = SessionManagementTerminalTextTokens.monospace,
                ),
            color = SessionManagementCommandHintColor,
        )
    }
}

@Composable
private fun SessionManagementTerminalInputLine(
    commandInput: String,
    inputEnabled: Boolean,
    fontSize: TextUnit,
    lineHeight: TextUnit,
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
                    fontFamily = SessionManagementTerminalTextTokens.monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                ),
            color = SessionManagementCommandPromptColor,
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
                    fontFamily = SessionManagementTerminalTextTokens.monospace,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = SessionManagementCommandTextColor,
                ),
            cursorBrush = SolidColor(SessionManagementCommandPromptColor),
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
                                    ManagementTexts.Commands.ENTER_SHELL_COMMAND.get()
                                } else {
                                    ManagementTexts.Commands.CONNECTING_SHELL.get()
                                },
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = SessionManagementTerminalTextTokens.monospace,
                                    fontSize = fontSize,
                                    lineHeight = lineHeight,
                                ),
                            color = SessionManagementCommandHintColor,
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
                contentDescription = ManagementTexts.Commands.RUN_COMMAND.get(),
                tint =
                    if (normalizedCommand.isNotBlank() && inputEnabled) {
                    SessionManagementCommandPromptColor
                } else {
                    SessionManagementCommandHintColor.copy(alpha = 0.32f)
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
    val presets = remember { managementCommandPresets() }

    SessionManagementCenteredDialog(
        title = ManagementTexts.Commands.QUICK_COMMANDS.get(),
        onDismiss = onDismiss,
        widthRatio = 0.92f,
        maxHeightRatio = 0.72f,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items = presets,
                key = { _, preset -> preset.command },
            ) { _, preset ->
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

@Composable
private fun SessionManagementCommandPresetCard(
    preset: ManagementCommandPreset,
    isExecuting: Boolean,
    onExecuteCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
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
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                ) {
                    Icon(
                        imageVector = preset.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = SessionManagementTerminalTextTokens.monospace),
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

    private var shellStream: ManagementShellStream? = null
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
            append(ManagementTexts.Commands.SHELL_CONNECTING.get())
        }
        readJob =
            scope.launch {
                val stream = openManagementShellStream()
                if (stream == null) {
                    appendConnectionFailed()
                    return@launch
                }

                shellStream = stream
                isConnected = true
                if (!hasEverConnected) {
                    append(ManagementTexts.Commands.SHELL_CONNECTED.get())
                }
                hasEverConnected = true
                reconnectAttempts = 0
                startKeepAlive(stream)

                withContext(Dispatchers.IO) {
                    try {
                        while (true) {
                            when (val packet = stream.read()) {
                                is ManagementShellPacket.Output -> {
                                    appendTerminalTextOnMain(packet.text)
                                }

                                is ManagementShellPacket.Exit -> {
                                    appendOnMain(ManagementTexts.Commands.SHELL_EXITED.format(packet.code))
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
                append(ManagementTexts.Commands.SHELL_NOT_CONNECTED.get())
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
                    append(ManagementTexts.Commands.SHELL_WRITE_FAILED.format(error.message.orEmpty()))
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
            runCatching { stream.closeInput() }
            runCatching { stream.close() }
        }
        shellStream = null
    }

    private fun startKeepAlive(stream: ManagementShellStream) {
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
        append(ManagementTexts.Commands.SHELL_DISCONNECTED.get())
    }

    private fun appendTerminalText(text: String) {
        var nextOutput = output
        SESSION_MANAGEMENT_ANSI_PATTERN
            .replace(text, "")
            .forEach { char ->
                nextOutput =
                    when (char) {
                        '\r' -> {
                            val lineStart = nextOutput.lastIndexOf('\n') + 1
                            nextOutput.take(lineStart)
                        }

                        '\b' -> {
                            nextOutput.dropLast(1)
                        }

                        else -> {
                            nextOutput + char
                        }
                    }
            }
        output = nextOutput.takeLast(SESSION_MANAGEMENT_COMMAND_OUTPUT_LIMIT)
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
    val result = executeManagementShell(normalizedCommand)
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
                    ManagementTexts.Commands.COMMAND_COMPLETED_NO_OUTPUT.get()
                } else {
                    ManagementTexts.Commands.COMMAND_FAILED.get()
                }
            }

    if (normalized.length <= SESSION_MANAGEMENT_COMMAND_OUTPUT_LIMIT) {
        return normalized
    }

    return buildString {
        append(normalized.take(SESSION_MANAGEMENT_COMMAND_OUTPUT_LIMIT))
        append("\n\n")
        append(ManagementTexts.Commands.OUTPUT_TRUNCATED.format(SESSION_MANAGEMENT_COMMAND_OUTPUT_LIMIT))
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
            title = ManagementTexts.Commands.DEVICE_OVERVIEW.get(),
            description = ManagementTexts.Commands.QUICK_CHECK_BRAND_MODEL_ANDROID_VERSION.get(),
            command =
                "echo Manufacturer: \$(getprop ro.product.manufacturer) && " +
                    "echo Model: \$(getprop ro.product.model) && " +
                    "echo Android: \$(getprop ro.build.version.release)",
            icon = Icons.Default.Android,
        ),
        ManagementCommandPreset(
            title = ManagementTexts.Commands.DISPLAY_METRICS.get(),
            description = ManagementTexts.Commands.READ_CURRENT_RESOLUTION_DPI.get(),
            command = "wm size && wm density",
            icon = Icons.Default.CropFree,
        ),
        ManagementCommandPreset(
            title = ManagementTexts.Commands.FOREGROUND_WINDOW.get(),
            description = ManagementTexts.Commands.LOCATE_FOCUSED_WINDOW_FOREGROUND_ACTIVITY.get(),
            command = "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'",
            icon = Icons.Default.Search,
        ),
        ManagementCommandPreset(
            title = ManagementTexts.Commands.USER_APPS.get(),
            description =
                ManagementTexts.Commands.LIST_USER_APPS_DESCRIPTION.get(),
            command = "pm list packages -3 | head -n 80",
            icon = Icons.Default.Apps,
        ),
        ManagementCommandPreset(
            title = ManagementTexts.Commands.NETWORK_STATUS.get(),
            description = ManagementTexts.Commands.SHOW_WLAN_ADDRESS_WIRELESS_DEBUGGING_PORT.get(),
            command = "ip addr show wlan0 | grep -m 1 'inet ' && getprop service.adb.tcp.port",
            icon = Icons.Default.Wifi,
        ),
        ManagementCommandPreset(
            title = ManagementTexts.Commands.LOGCAT_SNAPSHOT.get(),
            description =
                ManagementTexts.Commands.LOGCAT_SNAPSHOT_DESCRIPTION.get(),
            command = "logcat -d -t 120",
            icon = Icons.Default.Code,
        ),
        ManagementCommandPreset(
            title = ManagementTexts.Commands.MEMORY_USAGE.get(),
            description = ManagementTexts.Commands.SHOW_TOTAL_USED_AVAILABLE_MEMORY.get(),
            command = "dumpsys meminfo | grep -E 'Total RAM|Free RAM|Used RAM'",
            icon = Icons.Default.Android,
        ),
        ManagementCommandPreset(
            title = ManagementTexts.Commands.BATTERY_INFO.get(),
            description = ManagementTexts.Commands.SHOW_BATTERY_LEVEL_TEMPERATURE_CHARGING_STATE.get(),
            command = "dumpsys battery | grep -E 'level|temperature|status'",
            icon = Icons.Default.Android,
        ),
        ManagementCommandPreset(
            title = ManagementTexts.Commands.CPU_INFO.get(),
            description = ManagementTexts.Commands.SHOW_CPU_ARCHITECTURE_CORE_COUNT.get(),
            command = "cat /proc/cpuinfo | grep -E 'processor|Hardware|model name' | head -n 10",
            icon = Icons.Default.Android,
        ),
        ManagementCommandPreset(
            title = ManagementTexts.Commands.STORAGE.get(),
            description = ManagementTexts.Commands.SHOW_INTERNAL_STORAGE_SD_CARD_USAGE.get(),
            command = "df -h | grep -E '/data|/storage'",
            icon = Icons.Default.Android,
        ),
        ManagementCommandPreset(
            title = ManagementTexts.Commands.RUNNING_PROCESSES.get(),
            description = ManagementTexts.Commands.LIST_RUNNING_APP_PROCESSES.get(),
            command = "ps -A | grep -v '\\[' | head -n 30",
            icon = Icons.Default.Apps,
        ),
        ManagementCommandPreset(
            title = ManagementTexts.Commands.KILL_BACKGROUND_APPS.get(),
            description = ManagementTexts.Commands.FORCE_STOP_BACKGROUND_APPS_FREE_MEMORY.get(),
            command = "am kill-all",
            icon = Icons.Default.DeleteOutline,
        ),
        ManagementCommandPreset(
            title = ManagementTexts.Commands.SYSTEM_PROPERTIES.get(),
            description = ManagementTexts.Commands.INSPECT_KEY_SYSTEM_PROPERTIES.get(),
            command = "getprop | grep -E 'ro.build|ro.product|ro.hardware'",
            icon = Icons.Default.Info,
        ),
    )
