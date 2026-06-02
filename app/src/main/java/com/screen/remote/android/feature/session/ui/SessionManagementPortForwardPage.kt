package com.screen.remote.android.feature.session.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.data.repository.SessionRepository
import com.screen.remote.android.core.data.repository.TcpPortForwardRule
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.feature.session.ui.component.LabeledTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PortForwardRuleCardShape = RoundedCornerShape(8.dp)

private class EditablePortForwardRule(rule: TcpPortForwardRule) {
    var targetHost by mutableStateOf(rule.targetHost)
    var targetPort by mutableStateOf(rule.targetPort.toString())
    var localPort by mutableStateOf(rule.localPort.toString())

    fun toRuleOrNull(): TcpPortForwardRule? {
        val normalizedTarget = targetHost.trim()
        val parsedTargetPort = targetPort.toIntOrNull() ?: return null
        val parsedLocalPort = localPort.toIntOrNull() ?: return null
        if (normalizedTarget.isBlank()) return null
        return TcpPortForwardRule(
            targetHost = normalizedTarget,
            targetPort = parsedTargetPort,
            localPort = parsedLocalPort,
        )
    }
}

@Composable
internal fun SessionManagementPortForwardPage(
    sessionData: SessionData,
    modifier: Modifier = Modifier,
    refreshToken: Int,
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { SessionRepository(context) }
    val scope = rememberCoroutineScope()
    var rules by remember(sessionData.id) { mutableStateOf(sessionData.tcpPortForwardRules) }
    var draftRules by remember(sessionData.id) { mutableStateOf(rules.map(::EditablePortForwardRule)) }
    var settingsOpen by remember(sessionData.id) { mutableStateOf(false) }
    var status by remember { mutableStateOf<PortForwardStatus?>(null) }
    var logs by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    suspend fun loadStatusAndLogs() {
        SessionManagementPortForwardManager.status().fold(
            onSuccess = { status = it },
            onFailure = { error ->
                message = error.message ?: ManagementTexts.PortForward.COULDN_T_READ_RELAY_STATUS.get()
                messageIsError = true
            },
        )
        SessionManagementPortForwardManager.logs().onSuccess { logs = it }
    }

    LaunchedEffect(sessionData.tcpPortForwardRules) {
        if (!settingsOpen) {
            rules = sessionData.tcpPortForwardRules
            draftRules = rules.map(::EditablePortForwardRule)
        }
    }

    LaunchedEffect(sessionData.id, refreshToken) {
        loadStatusAndLogs()
    }

    LaunchedEffect(status?.remoteRunning) {
        while (status?.remoteRunning == true) {
            delay(2_000)
            SessionManagementPortForwardManager.logs().onSuccess { logs = it }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PortForwardOverviewCard(
            rules = rules,
            status = status,
            busy = busy,
            onSettings = {
                draftRules = rules.map(::EditablePortForwardRule)
                settingsOpen = true
            },
            onOpenTarget = { rule ->
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:${rule.localPort}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }.onFailure { error ->
                    message = error.message ?: ManagementTexts.PortForward.COULDN_T_OPEN_BROWSER.get()
                    messageIsError = true
                }
            },
            onStart = {
                val config = SessionManagementPortForwardManager.configFor(rules)
                if (config == null) {
                    message = ManagementTexts.PortForward.FORWARDING_SETTINGS_INCOMPLETE.get()
                    messageIsError = true
                } else {
                    busy = true
                    message = null
                    scope.launch {
                        SessionManagementPortForwardManager.start(context, config).fold(
                            onSuccess = { next ->
                                status = next
                            },
                            onFailure = { error ->
                                message = error.message ?: ManagementTexts.PortForward.COULDN_T_START_RELAY_HELPER.get()
                                messageIsError = true
                            },
                        )
                        SessionManagementPortForwardManager.logs().onSuccess { logs = it }
                        busy = false
                    }
                }
            },
            onStop = {
                busy = true
                message = null
                scope.launch {
                    SessionManagementPortForwardManager.stop().fold(
                        onSuccess = { next ->
                            status = next
                            message = ManagementTexts.PortForward.RELAY_HELPER_STOPPED.get()
                            messageIsError = false
                        },
                        onFailure = { error ->
                            message = error.message ?: ManagementTexts.PortForward.COULDN_T_STOP_RELAY_HELPER.get()
                            messageIsError = true
                        },
                    )
                    loadStatusAndLogs()
                    busy = false
                }
            },
        )

        PortForwardLogCard(logs = logs, running = status?.remoteRunning == true)

        message?.let { text ->
            SessionManagementNoteCard(
                title = if (messageIsError) ManagementTexts.PortForward.OPERATION_FAILED.get() else ManagementTexts.PortForward.NOTICE.get(),
                text = text,
            )
        }

    }

    if (settingsOpen) {
        val parsedRules = draftRules.mapNotNull(EditablePortForwardRule::toRuleOrNull)
        val draftConfig =
            if (parsedRules.size == draftRules.size) {
                SessionManagementPortForwardManager.configFor(parsedRules)
            } else {
                null
            }

        DialogPage(
            title = ManagementTexts.PortForward.FORWARD_SETTINGS.get(),
            onDismiss = { settingsOpen = false },
            leftButtonText = ManagementTexts.PortForward.CANCEL.get(),
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = draftConfig != null,
                        onClick = {
                            val normalizedRules = draftConfig?.rules ?: return@TextButton
                            rules = normalizedRules
                            draftRules = normalizedRules.map(::EditablePortForwardRule)
                            settingsOpen = false
                            scope.launch {
                                repository.updateSessionFields(sessionData.id) { current ->
                                    current.copy(tcpPortForwardRules = normalizedRules)
                                }
                            }
                        },
                    ) {
                        Text(ManagementTexts.PortForward.SAVE.get())
                    }
                    IconButton(
                        onClick = {
                            val usedPorts = draftRules.mapNotNull { it.localPort.toIntOrNull() }.toSet()
                            val suggestedLocalPort = (18080..65535).firstOrNull { it !in usedPorts } ?: 18080
                            draftRules =
                                draftRules +
                                    EditablePortForwardRule(
                                        TcpPortForwardRule(
                                            targetHost = "",
                                            targetPort = 80,
                                            localPort = suggestedLocalPort,
                                        ),
                                    )
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = ManagementTexts.PortForward.ADD_PORT_FORWARD.get(),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            enableScroll = true,
            verticalSpacing = 8.dp,
        ) {
            if (draftRules.isEmpty()) {
                Text(
                    text = ManagementTexts.PortForward.TAP_ADD_PORT_FORWARD.get(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                )
            } else {
                draftRules.forEachIndexed { index, rule ->
                    PortForwardRuleEditor(
                        index = index,
                        rule = rule,
                        onRemove = {
                            draftRules = draftRules.filterIndexed { itemIndex, _ -> itemIndex != index }
                        },
                    )
                }
            }

            if (draftRules.isNotEmpty() && draftConfig == null) {
                Text(
                    text =
                        ManagementTexts.PortForward.INVALID_RULES.get(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PortForwardRuleEditor(
    index: Int,
    rule: EditablePortForwardRule,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = PortForwardRuleCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = AppDimens.listItemHeight).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ManagementTexts.PortForward.PORT_FORWARD.format(index + 1),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = ManagementTexts.PortForward.REMOVE_PORT_FORWARD.get(),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        AppDivider()

        LabeledTextField(
            label = ManagementTexts.PortForward.TARGET_ADDRESS.get(),
            value = rule.targetHost,
            onValueChange = { rule.targetHost = it },
            placeholder = "192.168.1.1",
            isError = rule.targetHost.isBlank(),
        )

        AppDivider()

        LabeledTextField(
            label = ManagementTexts.PortForward.TARGET_PORT.get(),
            value = rule.targetPort,
            onValueChange = { value -> rule.targetPort = value.filter(Char::isDigit) },
            placeholder = "80",
            keyboardType = KeyboardType.Number,
            isError = rule.targetPort.toIntOrNull() !in 1..65535,
        )

        AppDivider()

        LabeledTextField(
            label = ManagementTexts.PortForward.LOCAL_PORT.get(),
            value = rule.localPort,
            onValueChange = { value -> rule.localPort = value.filter(Char::isDigit) },
            placeholder = "18080",
            keyboardType = KeyboardType.Number,
            isError = rule.localPort.toIntOrNull() !in 1..65535,
        )
    }
}

@Composable
private fun PortForwardOverviewCard(
    rules: List<TcpPortForwardRule>,
    status: PortForwardStatus?,
    busy: Boolean,
    onSettings: () -> Unit,
    onOpenTarget: (TcpPortForwardRule) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(AppDimens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "TCP Relay",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = ManagementTexts.PortForward.TARGET_DEVICE_PORT_FORWARDING.get(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (busy && status == null) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    SessionManagementUtilityBadge(
                        text =
                            when {
                                status?.running == true -> ManagementTexts.PortForward.RUNNING.get()
                                status?.remoteRunning == true -> ManagementTexts.PortForward.LOCAL_FORWARD_INACTIVE.get()
                                else -> ManagementTexts.PortForward.STOPPED.get()
                            },
                        accent = if (status?.remoteRunning == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        available = status?.remoteRunning == true,
                    )
                }
            }

            status?.pid?.let { pid ->
                Text(
                    text = ManagementTexts.PortForward.TARGET_PROCESS_PID.format(pid),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (rules.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    rules.forEach { rule ->
                        PortForwardRuleButton(
                            rule = rule,
                            onClick = { onOpenTarget(rule) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactPortForwardAction(
                    text = ManagementTexts.PortForward.SETTINGS.get(),
                    onClick = onSettings,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompactPortForwardAction(
                    text = ManagementTexts.PortForward.START.get(),
                    onClick = onStart,
                    enabled = !busy && rules.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
                CompactPortForwardAction(
                    text = ManagementTexts.PortForward.STOP.get(),
                    onClick = onStop,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun PortForwardRuleButton(
    rule: TcpPortForwardRule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier.clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "${rule.targetHost}:${rule.targetPort} -> ${rule.localPort}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompactPortForwardAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PortForwardLogCard(
    logs: String,
    running: Boolean,
) {
    val terminalPalette = sessionManagementTerminalPalette()
    Surface(
        shape = RoundedCornerShape(AppDimens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ManagementTexts.PortForward.LOGS.get(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (running) ManagementTexts.PortForward.AUTO_REFRESH.get() else ManagementTexts.PortForward.RECENT_OUTPUT.get(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                modifier =
                    Modifier
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                        .fillMaxWidth()
                        .heightIn(min = 210.dp, max = 340.dp),
                shape = RoundedCornerShape(16.dp),
                color = terminalPalette.background,
            ) {
                val scrollState = rememberScrollState()
                LaunchedEffect(logs) {
                    scrollState.scrollTo(scrollState.maxValue)
                }
                SelectionContainer {
                    Text(
                        text = logs.ifBlank { ManagementTexts.PortForward.NO_LOG_OUTPUT.get() },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(12.dp),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = SessionManagementTerminalTextTokens.monospace,
                                fontSize = SessionManagementTerminalTextTokens.outputFontSize,
                                lineHeight = SessionManagementTerminalTextTokens.outputLineHeight,
                            ),
                        color = terminalPalette.text,
                    )
                }
            }
        }
    }
}
