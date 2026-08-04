package com.screen.remote.android.feature.session.ui

import android.annotation.SuppressLint
import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.domain.model.CustomShellCommand
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.i18n.SettingsTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private const val SESSION_MANAGEMENT_SHELL_KEEPALIVE_INTERVAL_MS = 20_000L
private const val SESSION_MANAGEMENT_SHELL_RECONNECT_DELAY_MS = 1_200L
private const val SESSION_MANAGEMENT_SHELL_MAX_RECONNECT_ATTEMPTS = 3
private const val SESSION_MANAGEMENT_COMMAND_OUTPUT_LIMIT = 16_000

@SuppressLint("SdCardPath")
private const val SESSION_MANAGEMENT_DEFAULT_SHELL_DIRECTORY = "/sdcard"
private const val SESSION_MANAGEMENT_COMMAND_DONE_MARKER = "__SCREEN_REMOTE_COMMAND_DONE__"
private const val SESSION_MANAGEMENT_TERMINAL_MIN_TEXT_SCALE = 0.7f
private const val SESSION_MANAGEMENT_TERMINAL_MAX_TEXT_SCALE = 2.2f
private const val SESSION_MANAGEMENT_SHELL_INTERRUPT = "\u0003"
private val SESSION_MANAGEMENT_UNSUPPORTED_INTERACTIVE_COMMANDS =
    setOf(
        "vi", "vim", "view", "ex", "vimdiff", "nvim", "nano",
        "emacs", "less", "more", "man", "watch", "screen", "tmux"
    )
private val SESSION_MANAGEMENT_SHELL_SEGMENT_PATTERN = Regex("""&&|\|\||[|;]""")
private val SESSION_MANAGEMENT_SHELL_ASSIGNMENT_PATTERN = Regex("""[A-Za-z_][A-Za-z0-9_]*=.*""")
private val SESSION_MANAGEMENT_ANSI_PATTERN =
    Regex("""\u001B(?:\[[0-?]*[ -/]*[@-~]|][^\u0007]*(?:\u0007|\u001B\\)|[PX^_].*?\u001B\\|[@-Z\\-_])""")
private val SESSION_MANAGEMENT_TERMINAL_ERROR_PATTERN =
    Regex(
        """\b(error|failed|failure|fatal|exception|denied|not found|cannot|can't|invalid|unable|timed? out|no such file|permission denied)\b""",
        RegexOption.IGNORE_CASE,
    )
private val SESSION_MANAGEMENT_TERMINAL_WARNING_PATTERN =
    Regex("""\b(warn(?:ing)?|deprecated|retry|retrying|reconnect(?:ing)?)\b""", RegexOption.IGNORE_CASE)
private val SESSION_MANAGEMENT_TERMINAL_SUCCESS_PATTERN =
    Regex("""\b(connected|success|succeeded|complete|completed|ready|enabled|ok|done)\b""", RegexOption.IGNORE_CASE)
private val SESSION_MANAGEMENT_GREP_COMMAND_PATTERN = Regex("""(?:^|[|;&])\s*(?:grep|egrep|fgrep|rg)\b""")
private val SESSION_MANAGEMENT_SHELL_TOKEN_PATTERN = Regex("""'(?:[^']*)'|"(?:[^"\\]|\\.)*"|\S+""")
private val SESSION_MANAGEMENT_TERMINAL_TIMESTAMP_PATTERN =
    Regex("""\b(?:\d{4}-\d{2}-\d{2}[ T])?\d{2}:\d{2}:\d{2}(?:\.\d+)?\b""")
private val SESSION_MANAGEMENT_TERMINAL_ENDPOINT_PATTERN =
    Regex("""\b(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?\b|\[[0-9A-Fa-f:]+](?::\d{1,5})?""")
private val SESSION_MANAGEMENT_TERMINAL_PATH_PATTERN =
    Regex("""(?<![\w.])/(?:[^\s/:]+/)*[^\s]*""")
private val SESSION_MANAGEMENT_TERMINAL_KEY_PATTERN =
    Regex("""\b[A-Za-z_][A-Za-z0-9_.-]*(?=\s*[:=])""")
private val SESSION_MANAGEMENT_TERMINAL_NUMBER_PATTERN =
    Regex("""(?<![\w.])[-+]?(?:0x[0-9A-Fa-f]+|\d+(?:\.\d+)?)(?:\s?(?:%|ms|s|KB|MB|GB|KiB|MiB|GiB|GHz|MHz|DPI))?\b""")
private val SESSION_MANAGEMENT_TERMINAL_BOOLEAN_PATTERN =
    Regex("""\b(true|false|on|off|yes|no|enabled|disabled|running|stopped)\b""", RegexOption.IGNORE_CASE)
private val SESSION_MANAGEMENT_TERMINAL_QUOTED_STRING_PATTERN = Regex("""'(?:[^']*)'|"(?:[^"\\]|\\.)*""")
private val SESSION_MANAGEMENT_TERMINAL_FLAG_PATTERN = Regex("""(?<!\S)--?[A-Za-z0-9][A-Za-z0-9_-]*""")

private val SessionManagementCommandTextColor @Composable get() = sessionManagementTerminalPalette().text
private val SessionManagementCommandHintColor @Composable get() = sessionManagementTerminalPalette().hint
private val SessionManagementCommandPromptColor @Composable get() = sessionManagementTerminalPalette().accent
private val SessionManagementCommandErrorColor @Composable get() = sessionManagementTerminalPalette().error

internal enum class TerminalOutputTone {
    PROMPT,
    NORMAL,
    SUCCESS,
    WARNING,
    ERROR,
    COMMAND_SEPARATOR,
}

internal data class TerminalTextRange(
    val start: Int,
    val endExclusive: Int,
)

internal data class TerminalOutputLine(
    val text: String,
    val tone: TerminalOutputTone,
    val grepMatches: List<TerminalTextRange> = emptyList(),
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
    onCommandInputChange: (String) -> Unit,
    showPresetDialog: Boolean,
    onShowPresetDialogChange: (Boolean) -> Unit,
    customCommands: List<CustomShellCommand>,
    replaceDefaultCommands: Boolean,
) {
    var historyCursor by remember(terminalSession) { mutableStateOf<Int?>(null) }
    var inputBeforeHistory by remember(terminalSession) { mutableStateOf("") }
    val availableHistory = terminalSession.commandHistory

    fun sendCommand(command: String) {
        val normalized = command.trim()
        if (normalized.isBlank()) return
        terminalSession.sendLine(normalized)
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
            terminalSession.resetCompletion()
            onCommandInputChange(value)
        },
        onExecuteCommand = ::sendCommand,
        historyPreviousEnabled = availableHistory.isNotEmpty() && (historyCursor == null || historyCursor!! < availableHistory.lastIndex),
        historyNextEnabled = historyCursor != null,
        isCompletionLoading = terminalSession.isCompletionLoading,
        onInterrupt = terminalSession::interrupt,
        onComplete = {
            historyCursor = null
            terminalSession.completeInput(commandInput) { completedInput ->
                inputBeforeHistory = completedInput
                onCommandInputChange(completedInput)
            }
        },
        onHistoryPrevious = {
            terminalSession.resetCompletion()
            if (historyCursor == null) inputBeforeHistory = commandInput
            val nextIndex = ((historyCursor ?: -1) + 1).coerceAtMost(availableHistory.lastIndex)
            historyCursor = nextIndex
            onCommandInputChange(availableHistory[nextIndex])
        },
        onHistoryNext = {
            terminalSession.resetCompletion()
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
            isExecuting = !terminalSession.canWrite,
            customCommands = customCommands,
            replaceDefaultCommands = replaceDefaultCommands,
            onExecuteCommand = { command ->
                sendCommand(command)
                onShowPresetDialogChange(false)
            },
            onDismiss = { onShowPresetDialogChange(false) },
        )
    }
}

@SuppressLint("AutoboxingStateCreation")
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
    isCompletionLoading: Boolean,
    onInterrupt: () -> Unit,
    onComplete: () -> Unit,
    onHistoryPrevious: () -> Unit,
    onHistoryNext: () -> Unit,
    onClear: () -> Unit,
) {
    val terminalPalette = sessionManagementTerminalPalette()
    var textScale by rememberSaveable { mutableFloatStateOf(1f) }
    val outputFontSize = SessionManagementTerminalTextTokens.outputFontSize * textScale
    val outputLineHeight = SessionManagementTerminalTextTokens.outputLineHeight * textScale
    val inputFontSize = SessionManagementTerminalTextTokens.inputFontSize * textScale
    val inputLineHeight = SessionManagementTerminalTextTokens.inputLineHeight * textScale
    Surface(
        shape = SessionManagementCardShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Surface(
            shape = SessionManagementCardShape,
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
                    interruptEnabled = inputEnabled,
                    previousEnabled = historyPreviousEnabled,
                    nextEnabled = historyNextEnabled,
                    isCompletionLoading = isCompletionLoading,
                    onInterrupt = onInterrupt,
                    onComplete = onComplete,
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
                    onInterrupt = onInterrupt,
                    onHistoryPrevious = onHistoryPrevious,
                    onHistoryNext = onHistoryNext,
                    onComplete = onComplete,
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
    interruptEnabled: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    isCompletionLoading: Boolean,
    onInterrupt: () -> Unit,
    onComplete: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, end = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onInterrupt,
            enabled = interruptEnabled,
            modifier = Modifier.heightIn(min = 30.dp),
        ) {
            Text(
                text = "Ctrl+C",
                color =
                    if (interruptEnabled) {
                        SessionManagementCommandErrorColor
                    } else {
                        SessionManagementCommandHintColor.copy(alpha = 0.32f)
                    },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = SessionManagementTerminalTextTokens.monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        TextButton(
            onClick = onComplete,
            enabled = interruptEnabled && !isCompletionLoading,
            modifier = Modifier.heightIn(min = 30.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ManagementTexts.Commands.COMPLETE.get(),
                    color =
                        if (interruptEnabled) {
                            SessionManagementCommandPromptColor
                        } else {
                            SessionManagementCommandHintColor.copy(alpha = 0.32f)
                        },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = SessionManagementTerminalTextTokens.monospace,
                    fontWeight = FontWeight.Bold,
                )
                if (isCompletionLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = SessionManagementCommandPromptColor,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onPrevious,
            enabled = previousEnabled,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = ManagementTexts.Commands.PREVIOUS_COMMAND.get(),
                tint = if (previousEnabled) SessionManagementCommandPromptColor else SessionManagementCommandHintColor.copy(
                    alpha = 0.32f
                ),
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
                tint = if (nextEnabled) SessionManagementCommandPromptColor else SessionManagementCommandHintColor.copy(
                    alpha = 0.32f
                ),
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
    val outputScrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val terminalPalette = sessionManagementTerminalPalette()
    val clipboardManager = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val displayLines =
        rememberTerminalOutputLines(
            output = output,
            isConnected = isConnected,
        )

    LaunchedEffect(displayLines.size, output.length, isConnected) {
        if (displayLines.isNotEmpty()) {
            outputScrollState.animateScrollToItem(
                index = displayLines.lastIndex,
            )
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp)
                        .padding(14.dp),
                state = outputScrollState,
            ) {
                items(displayLines.size) { index ->
                    val line = displayLines[index]
                    if (line.tone == TerminalOutputTone.COMMAND_SEPARATOR) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 5.dp),
                            thickness = 0.5.dp,
                            color = terminalPalette.separator.copy(alpha = 0.36f),
                        )
                    } else {
                        Text(
                            text = terminalLineText(line, terminalPalette),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = SessionManagementTerminalTextTokens.monospace,
                                    fontSize = fontSize,
                                    lineHeight = lineHeight,
                                ),
                            color = terminalLineColor(line.tone, terminalPalette),
                        )
                    }
                }
            }
        }

        if (output.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        clipboardScope.launch {
                            clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("Screen Remote", output)))
                        }
                    },
                    modifier = Modifier.heightIn(min = 30.dp),
                ) {
                    Text(
                        text = ManagementTexts.Commands.COPY_ALL.get(),
                        color = SessionManagementCommandPromptColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = SessionManagementTerminalTextTokens.monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }

                IconButton(
                    onClick = onClear,
                    modifier =
                        Modifier
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
}

@Composable
private fun rememberTerminalOutputLines(
    output: String,
    isConnected: Boolean,
): List<TerminalOutputLine> =
    androidx.compose.runtime.remember(output, isConnected) {
        val displayOutput =
            output.ifBlank {
                if (isConnected) {
                    ManagementTexts.Commands.INTERACTIVE_SHELL_CONNECTED.get()
                } else {
                    ManagementTexts.Commands.OPENING_INTERACTIVE_SHELL.get()
                }
            }
        parseTerminalOutputLines(displayOutput)
    }

internal fun parseTerminalOutputLines(output: String): List<TerminalOutputLine> {
    var activeGrepPattern: Regex? = null
    val parsedLines = mutableListOf<TerminalOutputLine>()
    output.lineSequence().forEach { line ->
        if (line.isCommandDoneMarker()) {
            activeGrepPattern = null
            if (parsedLines.lastOrNull()?.let { it.text.isEmpty() && it.tone == TerminalOutputTone.NORMAL } == true) {
                parsedLines.removeAt(parsedLines.lastIndex)
            }
            parsedLines +=
                TerminalOutputLine(
                    text = "",
                    tone = TerminalOutputTone.COMMAND_SEPARATOR,
                )
        } else if (line.startsWith("$ ")) {
            activeGrepPattern = extractGrepPattern(line.removePrefix("$ "))
            parsedLines +=
                TerminalOutputLine(
                    text = line,
                    tone = TerminalOutputTone.PROMPT,
                )
        } else {
            val tone = terminalOutputTone(line)
            val matches =
                activeGrepPattern
                    ?.findAll(line)
                    ?.map { match -> TerminalTextRange(match.range.first, match.range.last + 1) }
                    ?.filter { range -> range.endExclusive > range.start }
                    ?.toList()
                    .orEmpty()
            parsedLines +=
                TerminalOutputLine(
                    text = line,
                    tone = tone,
                    grepMatches = matches,
                )
        }
    }
    return parsedLines
}

private fun String.isCommandDoneMarker(): Boolean =
    trim().startsWith("$SESSION_MANAGEMENT_COMMAND_DONE_MARKER:")

private fun terminalOutputTone(line: String): TerminalOutputTone =
    when {
        SESSION_MANAGEMENT_TERMINAL_ERROR_PATTERN.containsMatchIn(line) -> TerminalOutputTone.ERROR
        SESSION_MANAGEMENT_TERMINAL_WARNING_PATTERN.containsMatchIn(line) -> TerminalOutputTone.WARNING
        SESSION_MANAGEMENT_TERMINAL_SUCCESS_PATTERN.containsMatchIn(line) -> TerminalOutputTone.SUCCESS
        else -> TerminalOutputTone.NORMAL
    }

internal fun extractGrepPattern(command: String): Regex? {
    val grepStart = SESSION_MANAGEMENT_GREP_COMMAND_PATTERN.findAll(command).lastOrNull() ?: return null
    val arguments = command.substring(grepStart.range.last + 1).trim()
    val tokens = SESSION_MANAGEMENT_SHELL_TOKEN_PATTERN.findAll(arguments).map { it.value }.toList()
    var ignoreCase = false
    var pattern: String? = null
    var index = 0

    while (index < tokens.size) {
        val token = tokens[index]
        when {
            token == "-e" || token == "--regexp" -> {
                pattern = tokens.getOrNull(index + 1)?.unquoteShellToken()
                break
            }

            token.startsWith("--regexp=") -> {
                pattern = token.substringAfter('=').unquoteShellToken()
                break
            }

            token.startsWith("-") -> {
                if ('i' in token) ignoreCase = true
            }

            else -> {
                pattern = token.unquoteShellToken()
                break
            }
        }
        index += 1
    }

    val normalizedPattern = pattern?.takeIf(String::isNotBlank) ?: return null
    val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
    return runCatching { Regex(normalizedPattern, options) }
        .getOrElse { Regex(Regex.escape(normalizedPattern), options) }
}

internal fun unsupportedInteractiveCommand(command: String): String? =
    unsupportedInteractiveCommand(command, depth = 0)

private fun unsupportedInteractiveCommand(
    command: String,
    depth: Int,
): String? {
    if (depth > 2) return null
    return SESSION_MANAGEMENT_SHELL_SEGMENT_PATTERN.split(command).firstNotNullOfOrNull { segment ->
        val tokens =
            SESSION_MANAGEMENT_SHELL_TOKEN_PATTERN
                .findAll(segment)
                .map { it.value.unquoteShellToken() }
                .toList()
        var index = 0

        while (index < tokens.size && SESSION_MANAGEMENT_SHELL_ASSIGNMENT_PATTERN.matches(tokens[index])) index += 1
        if (tokens.getOrNull(index) == "env") {
            index += 1
            while (
                index < tokens.size &&
                (tokens[index].startsWith("-") || SESSION_MANAGEMENT_SHELL_ASSIGNMENT_PATTERN.matches(tokens[index]))
            ) {
                index += 1
            }
        }
        if (tokens.getOrNull(index) == "command") index += 1
        if (tokens.getOrNull(index) in setOf("busybox", "toybox")) index += 1

        val executable =
            tokens.getOrNull(index)?.substringAfterLast('/')?.lowercase() ?: return@firstNotNullOfOrNull null
        when (executable) {
            in SESSION_MANAGEMENT_UNSUPPORTED_INTERACTIVE_COMMANDS -> executable
            in setOf("sh", "bash", "zsh") if tokens.getOrNull(index + 1) == "-c" ->
                tokens.getOrNull(index + 2)?.let { nested -> unsupportedInteractiveCommand(nested, depth + 1) }

            else -> null
        }
    }
}

private fun String.unquoteShellToken(): String =
    if (length >= 2 && ((first() == '\'' && last() == '\'') || (first() == '"' && last() == '"'))) {
        substring(1, lastIndex)
    } else {
        this
    }

private fun terminalLineColor(
    tone: TerminalOutputTone,
    palette: SessionManagementTerminalPalette,
) =
    when (tone) {
        TerminalOutputTone.SUCCESS -> palette.success
        TerminalOutputTone.WARNING -> palette.warning
        TerminalOutputTone.ERROR -> palette.error
        TerminalOutputTone.COMMAND_SEPARATOR -> palette.separator
        else -> palette.text
    }

private fun terminalLineText(
    line: TerminalOutputLine,
    palette: SessionManagementTerminalPalette,
): AnnotatedString =
    buildAnnotatedString {
        append(line.text)
        if (line.tone == TerminalOutputTone.PROMPT && line.text.startsWith("$")) {
            addStyle(
                SpanStyle(color = palette.accent, fontWeight = FontWeight.Bold),
                start = 0,
                end = 1,
            )
        }
        if (line.tone == TerminalOutputTone.NORMAL || line.tone == TerminalOutputTone.PROMPT) {
            SESSION_MANAGEMENT_TERMINAL_TIMESTAMP_PATTERN.findAll(line.text).forEach { match ->
                addStyle(SpanStyle(color = palette.hint), match.range.first, match.range.last + 1)
            }
            SESSION_MANAGEMENT_TERMINAL_KEY_PATTERN.findAll(line.text).forEach { match ->
                addStyle(
                    SpanStyle(color = palette.accent, fontWeight = FontWeight.Medium),
                    match.range.first,
                    match.range.last + 1,
                )
            }
            SESSION_MANAGEMENT_TERMINAL_NUMBER_PATTERN.findAll(line.text).forEach { match ->
                addStyle(SpanStyle(color = palette.number), match.range.first, match.range.last + 1)
            }
            SESSION_MANAGEMENT_TERMINAL_PATH_PATTERN.findAll(line.text).forEach { match ->
                addStyle(SpanStyle(color = palette.path), match.range.first, match.range.last + 1)
            }
            SESSION_MANAGEMENT_TERMINAL_ENDPOINT_PATTERN.findAll(line.text).forEach { match ->
                addStyle(
                    SpanStyle(color = palette.match, fontWeight = FontWeight.Medium),
                    match.range.first,
                    match.range.last + 1,
                )
            }
            SESSION_MANAGEMENT_TERMINAL_QUOTED_STRING_PATTERN.findAll(line.text).forEach { match ->
                addStyle(SpanStyle(color = palette.string), match.range.first, match.range.last + 1)
            }
            SESSION_MANAGEMENT_TERMINAL_FLAG_PATTERN.findAll(line.text).forEach { match ->
                addStyle(SpanStyle(color = palette.path), match.range.first, match.range.last + 1)
            }
            SESSION_MANAGEMENT_TERMINAL_BOOLEAN_PATTERN.findAll(line.text).forEach { match ->
                val positive =
                    match.value.lowercase() in setOf("true", "on", "yes", "enabled", "running")
                addStyle(
                    SpanStyle(
                        color = if (positive) palette.success else palette.error,
                        fontWeight = FontWeight.Medium,
                    ),
                    match.range.first,
                    match.range.last + 1,
                )
            }
        }
        line.grepMatches.forEach { range ->
            addStyle(
                SpanStyle(
                    color =
                        when (line.tone) {
                            TerminalOutputTone.ERROR -> palette.error
                            TerminalOutputTone.WARNING -> palette.warning
                            else -> palette.match
                        },
                    fontWeight = FontWeight.Bold,
                ),
                start = range.start,
                end = range.endExclusive,
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
    onInterrupt: () -> Unit,
    onHistoryPrevious: () -> Unit,
    onHistoryNext: () -> Unit,
    onComplete: () -> Unit,
) {
    val normalizedCommand = commandInput.trim()
    val inputScrollState = rememberScrollState()
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = commandInput,
                selection = TextRange(commandInput.length),
            ),
        )
    }

    LaunchedEffect(commandInput) {
        if (commandInput != fieldValue.text) {
            fieldValue =
                TextFieldValue(
                    text = commandInput,
                    selection = TextRange(commandInput.length),
                )
        }
    }

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
            value = fieldValue,
            onValueChange = { value ->
                fieldValue = value
                onCommandInputChange(value.text)
            },
            enabled = inputEnabled,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = SessionManagementControlHeight, max = 132.dp)
                    .verticalScroll(inputScrollState)
                    .onPreviewKeyEvent { event ->
                        when {
                            event.isCtrlPressed && event.key == Key.C -> {
                                if (event.type == KeyEventType.KeyDown) onInterrupt()
                                true
                            }

                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                                onHistoryPrevious()
                                true
                            }

                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                                onHistoryNext()
                                true
                            }

                            event.type == KeyEventType.KeyDown && event.key == Key.Tab -> {
                                onComplete()
                                true
                            }

                            else -> false
                        }
                    }
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
                    if (fieldValue.text.isEmpty()) {
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
            modifier = Modifier.size(SessionManagementControlHeight),
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
    customCommands: List<CustomShellCommand>,
    replaceDefaultCommands: Boolean,
    onExecuteCommand: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultPresets = managementCommandPresets()
    val customPresets =
        customCommands.map { custom ->
            ManagementCommandPreset(
                title = custom.name,
                description = SettingsTexts.SETTINGS_CUSTOM_COMMANDS.get(),
                command = custom.command,
                icon = Icons.Default.Code,
            )
        }
    val presets = combineShellCommandPresets(defaultPresets, customPresets, replaceDefaultCommands)

    SessionManagementCenteredDialog(
        title = ManagementTexts.Commands.QUICK_COMMANDS.get(),
        onDismiss = onDismiss,
        widthRatio = SessionManagementContentWidthFraction,
        maxHeightRatio = 0.72f,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (presets.isEmpty()) {
                item {
                    Text(
                        text = SettingsTexts.CUSTOM_COMMANDS_EMPTY.get(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            itemsIndexed(
                items = presets,
                key = { index, preset -> "$index:${preset.command}" },
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

internal fun <T> combineShellCommandPresets(
    defaultCommands: List<T>,
    customCommands: List<T>,
    replaceDefaultCommands: Boolean,
): List<T> = if (replaceDefaultCommands) customCommands else defaultCommands + customCommands

@Composable
private fun SessionManagementCommandPresetCard(
    preset: ManagementCommandPreset,
    isExecuting: Boolean,
    onExecuteCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = SessionManagementCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(SessionManagementCardShape)
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
    var commandHistory by mutableStateOf<List<String>>(emptyList())
        private set
    var isCompletionLoading by mutableStateOf(false)
        private set

    private var shellStream: ManagementShellStream? = null
    private var readJob: Job? = null
    private var keepAliveJob: Job? = null
    private var completionJob: Job? = null
    private var completionCycle: ShellCompletionCycle? = null
    private var completionRequestId = 0L
    private var pendingMarkerText = ""
    private var pendingCarriageReturn = false
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
        userRequestedExit = false
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
                withContext(Dispatchers.IO) {
                    runCatching {
                        stream.write("cd $SESSION_MANAGEMENT_DEFAULT_SHELL_DIRECTORY\n")
                    }
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
                        delay(SESSION_MANAGEMENT_SHELL_RECONNECT_DELAY_MS.milliseconds)
                        start()
                    } else {
                        appendConnectionFailed()
                    }
                }
            }
    }

    fun sendLine(command: String) {
        resetCompletion()
        val line = command.trimEnd()
        if (line.isBlank()) {
            return
        }
        commandHistory = nextTerminalCommandHistory(commandHistory, line)

        if (line.trim() == "clear") {
            clear()
            return
        }

        unsupportedInteractiveCommand(line)?.let { blockedCommand ->
            append("$ $line\n")
            append(ManagementTexts.Commands.UNSUPPORTED_INTERACTIVE_COMMAND.format(blockedCommand))
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
                    stream.write("$line\n")
                } else {
                    stream.write(buildManagementShellPayload(line))
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    append(ManagementTexts.Commands.SHELL_WRITE_FAILED.format(error.message.orEmpty()))
                    close(clearHistory = false)
                }
            }
        }
    }

    fun completeInput(
        input: String,
        onCompleted: (String) -> Unit,
    ) {
        completionCycle?.takeIf { it.completedInput == input }?.let { cycle ->
            if (!shouldLoadNextShellCompletionLevel(cycle.completedInput, cycle.candidates)) {
                val nextIndex = (cycle.selectedIndex + 1) % cycle.candidates.size
                val completed = applyShellCompletion(cycle.sourceInput, cycle.target, cycle.candidates, nextIndex)
                completionCycle =
                    cycle.copy(
                        selectedIndex = nextIndex,
                        completedInput = completed,
                    )
                if (completed != input) {
                    onCompleted(completed)
                }
                return
            }
        }

        val target = shellCompletionTarget(input) ?: return
        completionJob?.cancel()
        val requestId = ++completionRequestId
        isCompletionLoading = true
        completionJob =
            scope.launch {
                try {
                    val candidates = loadShellCompletionCandidates(target)
                    if (candidates.isEmpty()) {
                        completionCycle = null
                        return@launch
                    }
                    val completed = applyShellCompletion(input, target, candidates, candidateIndex = 0)
                    completionCycle =
                        ShellCompletionCycle(
                            sourceInput = input,
                            target = target,
                            candidates = candidates,
                            selectedIndex = 0,
                            completedInput = completed,
                        )
                    if (completed != input) {
                        onCompleted(completed)
                    }
                } finally {
                    if (completionRequestId == requestId) {
                        isCompletionLoading = false
                        completionJob = null
                    }
                }
            }
    }

    fun resetCompletion() {
        completionRequestId += 1
        completionJob?.cancel()
        completionJob = null
        completionCycle = null
        isCompletionLoading = false
    }

    fun interrupt() {
        val stream =
            shellStream ?: run {
                append(ManagementTexts.Commands.SHELL_NOT_CONNECTED.get())
                return
            }
        scope.launch(Dispatchers.IO) {
            try {
                stream.write(SESSION_MANAGEMENT_SHELL_INTERRUPT)
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    append(ManagementTexts.Commands.SHELL_WRITE_FAILED.format(error.message.orEmpty()))
                }
            }
        }
    }

    fun clear() {
        output = ""
        pendingMarkerText = ""
        pendingCarriageReturn = false
    }

    fun close(clearHistory: Boolean = true) {
        closeRequested = true
        resetCompletion()
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
        pendingMarkerText = ""
        pendingCarriageReturn = false
        if (clearHistory) {
            commandHistory = emptyList()
        }
    }

    private fun startKeepAlive(stream: ManagementShellStream) {
        keepAliveJob?.cancel()
        keepAliveJob =
            scope.launch(Dispatchers.IO) {
                while (!closeRequested && !userRequestedExit) {
                    delay(SESSION_MANAGEMENT_SHELL_KEEPALIVE_INTERVAL_MS.milliseconds)
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
        val incomingText = pendingMarkerText + SESSION_MANAGEMENT_ANSI_PATTERN.replace(text, "")
        pendingMarkerText = ""
        val appended =
            appendTerminalTextChunk(
                currentOutput = output,
                incomingText = incomingText,
                pendingCarriageReturn = pendingCarriageReturn,
            )
        pendingCarriageReturn = appended.pendingCarriageReturn
        val partition = partitionTerminalMarkerTail(appended.output)
        pendingMarkerText = partition.pending
        output = partition.visible.takeLast(SESSION_MANAGEMENT_COMMAND_OUTPUT_LIMIT)
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

internal fun buildManagementShellPayload(command: String): String =
    $$"$$command\nprintf '\\n$$SESSION_MANAGEMENT_COMMAND_DONE_MARKER:%s\\n' \"$?\"\n"

internal data class TerminalTextAppendResult(
    val output: String,
    val pendingCarriageReturn: Boolean,
)

internal fun appendTerminalTextChunk(
    currentOutput: String,
    incomingText: String,
    pendingCarriageReturn: Boolean = false,
): TerminalTextAppendResult {
    val result = StringBuilder(currentOutput.length + incomingText.length).append(currentOutput)
    var index = 0
    var hasPendingCarriageReturn = pendingCarriageReturn

    fun applyCarriageReturn() {
        val lineStart = result.lastIndexOf("\n") + 1
        result.setLength(lineStart)
    }

    if (hasPendingCarriageReturn) {
        if (incomingText.startsWith('\n')) {
            result.append('\n')
            index = 1
        } else {
            applyCarriageReturn()
        }
        hasPendingCarriageReturn = false
    }

    while (index < incomingText.length) {
        when (val char = incomingText[index]) {
            '\r' -> {
                if (index + 1 >= incomingText.length) {
                    hasPendingCarriageReturn = true
                    index += 1
                } else if (incomingText[index + 1] == '\n') {
                    result.append('\n')
                    index += 2
                } else {
                    applyCarriageReturn()
                    index += 1
                }
            }

            '\b' -> {
                if (result.isNotEmpty()) {
                    result.setLength(result.length - 1)
                }
                index += 1
            }

            else -> {
                result.append(char)
                index += 1
            }
        }
    }

    return TerminalTextAppendResult(
        output = result.toString(),
        pendingCarriageReturn = hasPendingCarriageReturn,
    )
}

internal data class TerminalMarkerPartition(
    val visible: String,
    val pending: String,
)

internal fun partitionTerminalMarkerTail(text: String): TerminalMarkerPartition {
    val lineStart = text.lastIndexOf('\n') + 1
    val tail = text.substring(lineStart)
    val incompleteMarker =
        tail.isNotEmpty() &&
            (SESSION_MANAGEMENT_COMMAND_DONE_MARKER.startsWith(tail) ||
                tail == "$SESSION_MANAGEMENT_COMMAND_DONE_MARKER:")
    return if (incompleteMarker) {
        TerminalMarkerPartition(
            visible = text.substring(0, lineStart),
            pending = tail,
        )
    } else {
        TerminalMarkerPartition(visible = text, pending = "")
    }
}

internal data class ShellCompletionTarget(
    val startIndex: Int,
    val token: String,
    val commandToken: Boolean,
)

private data class ShellCompletionCycle(
    val sourceInput: String,
    val target: ShellCompletionTarget,
    val candidates: List<String>,
    val selectedIndex: Int,
    val completedInput: String,
)

internal fun shouldLoadNextShellCompletionLevel(
    completedInput: String,
    candidates: List<String>,
): Boolean = candidates.size == 1 && completedInput.endsWith('/')

internal fun shellCompletionTarget(input: String): ShellCompletionTarget? {
    if (input.isEmpty() || input.last().isWhitespace()) return null
    val startIndex = input.indexOfLast(Char::isWhitespace).let { if (it < 0) 0 else it + 1 }
    val token = input.substring(startIndex)
    if (token.isBlank() || token.any { it == '\'' || it == '"' }) return null
    return ShellCompletionTarget(
        startIndex = startIndex,
        token = token,
        commandToken = input.substring(0, startIndex).isBlank() && '/' !in token,
    )
}

private suspend fun loadShellCompletionCandidates(target: ShellCompletionTarget): List<String> {
    val script =
        if (target.commandToken) {
            val prefix = quoteShellArg(target.token.lowercase(Locale.ROOT))
            $$"for dir in $(printf '%s' \"$PATH\" | tr ':' ' '); do " +
                $$"[ -d \"$dir\" ] || continue; " +
                $$"for item in \"$dir\"/*; do " +
                $$"[ -f \"$item\" ] && [ -x \"$item\" ] || continue; " +
                $$"name=${item##*/}; lower_name=$(printf '%s' \"$name\" | tr '[:upper:]' '[:lower:]'); " +
                $$"case \"$lower_name\" in $$prefix*) printf '%s\\n' \"$name\";; esac; " +
                "done; done"
        } else {
            val slashIndex = target.token.lastIndexOf('/')
            val displayDirectory = if (slashIndex >= 0) target.token.substring(0, slashIndex + 1) else ""
            val namePrefix =
                (if (slashIndex >= 0) target.token.substring(slashIndex + 1) else target.token)
                    .lowercase(Locale.ROOT)
            val searchDirectory =
                when {
                    displayDirectory.startsWith("/") -> displayDirectory.trimEnd('/').ifEmpty { "/" }
                    displayDirectory.isEmpty() -> SESSION_MANAGEMENT_DEFAULT_SHELL_DIRECTORY
                    else -> "$SESSION_MANAGEMENT_DEFAULT_SHELL_DIRECTORY/${displayDirectory.trimEnd('/')}"
                }
            $$"for item in $${quoteShellArg(searchDirectory)}/*; do " +
                $$"[ -e \"$item\" ] || continue; name=${item##*/}; " +
                $$"lower_name=$(printf '%s' \"$name\" | tr '[:upper:]' '[:lower:]'); " +
                $$"case \"$lower_name\" in $${quoteShellArg(namePrefix)}*) " +
                $$"if [ -d \"$item\" ]; then printf '%s/\\n' \"$name\"; else printf '%s\\n' \"$name\"; fi;; esac; done"
        }

    return executeManagementShell("sh -c ${quoteShellArg(script)}")
        .getOrNull()
        .orEmpty()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .sorted()
        .toList()
}

internal fun applyShellCompletion(
    input: String,
    target: ShellCompletionTarget,
    candidates: List<String>,
    candidateIndex: Int = 0,
): String {
    if (candidates.isEmpty()) return input
    val slashIndex = target.token.lastIndexOf('/')
    val directoryPrefix = if (slashIndex >= 0) target.token.substring(0, slashIndex + 1) else ""
    val typedName = if (slashIndex >= 0) target.token.substring(slashIndex + 1) else target.token
    val completedName = candidates[candidateIndex.mod(candidates.size)]
    if (!completedName.startsWith(typedName, ignoreCase = true)) return input
    val completedToken = directoryPrefix + completedName
    val suffix = if (!completedToken.endsWith('/')) " " else ""
    return input.replaceRange(target.startIndex, input.length, completedToken + suffix)
}

internal fun nextTerminalCommandHistory(
    history: List<String>,
    command: String,
): List<String> =
    (listOf(command) + history.filterNot { it == command })

private fun managementCommandPresets(): List<ManagementCommandPreset> =
    listOf(
        ManagementCommandPreset(
            title = ManagementTexts.Commands.DEVICE_OVERVIEW.get(),
            description = ManagementTexts.Commands.QUICK_CHECK_BRAND_MODEL_ANDROID_VERSION.get(),
            command =
                "echo Manufacturer: $(getprop ro.product.manufacturer) && " +
                    "echo Model: $(getprop ro.product.model) && " +
                    "echo Android: $(getprop ro.build.version.release)",
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
