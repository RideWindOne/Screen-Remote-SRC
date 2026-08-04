package com.screen.remote.android.feature.settings.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.screen.remote.android.core.common.constants.IosDesignTokens
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.domain.model.AppSettings
import com.screen.remote.android.core.domain.model.CustomShellCommand
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SettingsTexts
import com.screen.remote.android.feature.session.ui.component.LabeledTextField
import java.util.UUID

private val CustomCommandRowHeight = 72.dp

@Composable
fun CustomCommandsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
) {
    var commands by remember(settings.customShellCommands) { mutableStateOf(settings.customShellCommands) }
    var showEditor by remember { mutableStateOf(false) }
    var editingCommand by remember { mutableStateOf<CustomShellCommand?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { CustomCommandRowHeight.toPx() }

    fun saveCommands(updated: List<CustomShellCommand>) {
        commands = updated
        onUpdateSettings(settings.copy(customShellCommands = updated))
    }

    DialogPage(
        title = SettingsTexts.SETTINGS_CUSTOM_COMMANDS.get(),
        onDismiss = onBack,
        showBackButton = true,
        rightButtonText = CommonTexts.BUTTON_ADD.get(),
        onRightButtonClick = {
            editingCommand = null
            showEditor = true
        },
        enableScroll = true,
        scrollContentTopPadding = IosDesignTokens.dialogCompactHeaderSpacerHeight,
        scrollContentBottomPadding = IosDesignTokens.dialogCompactBottomSpacerHeight,
    ) {
        SettingsCard(title = SettingsTexts.CUSTOM_COMMANDS_SECTION.get()) {
            SettingsSwitch(
                title = SettingsTexts.CUSTOM_COMMANDS_REPLACE_DEFAULTS.get(),
                checked = settings.replaceDefaultShellCommands,
                helpText = SettingsTexts.CUSTOM_COMMANDS_REPLACE_DEFAULTS_HELP.get(),
                onCheckedChange = { enabled ->
                    onUpdateSettings(settings.copy(replaceDefaultShellCommands = enabled))
                },
            )
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))

        SettingsCard(title = SettingsTexts.CUSTOM_COMMANDS_LIST.get()) {
            if (commands.isEmpty()) {
                Text(
                    text = SettingsTexts.CUSTOM_COMMANDS_EMPTY.get(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(IosDesignTokens.standardHorizontalPadding),
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                ) {
                    itemsIndexed(
                        items = commands,
                        key = { _, command -> command.id },
                    ) { index, command ->
                        val isDragging = draggingId == command.id
                        val settledDragOffset = remember(command.id) { Animatable(0f) }
                        LaunchedEffect(isDragging, dragDistance) {
                            if (isDragging) {
                                settledDragOffset.snapTo(dragDistance)
                            } else if (settledDragOffset.value != 0f) {
                                settledDragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec =
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                )
                            }
                        }

                        Column(
                            modifier =
                                Modifier
                                    .animateItem(
                                        fadeInSpec = null,
                                        placementSpec =
                                            if (isDragging) {
                                                null
                                            } else {
                                                spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow,
                                                )
                                            },
                                        fadeOutSpec = null,
                                    )
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        translationY =
                                            if (isDragging) {
                                                dragDistance
                                            } else {
                                                settledDragOffset.value
                                            }
                                        scaleX = if (isDragging) 1.01f else 1f
                                        scaleY = if (isDragging) 1.01f else 1f
                                    },
                        ) {
                            if (index > 0) SettingsDivider()
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(CustomCommandRowHeight)
                                        .padding(start = IosDesignTokens.standardHorizontalPadding, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        text = command.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = command.command,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        editingCommand = command
                                        showEditor = true
                                    },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = SettingsTexts.CUSTOM_COMMAND_EDIT.get(),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                IconButton(
                                    onClick = { saveCommands(commands.filterNot { it.id == command.id }) },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = SettingsTexts.CUSTOM_COMMAND_DELETE.get(),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = null,
                                    tint =
                                        if (isDragging) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    modifier =
                                        Modifier
                                            .size(40.dp)
                                            .pointerInput(command.id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggingId = command.id
                                                        dragDistance = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggingId = null
                                                        dragDistance = 0f
                                                    },
                                                    onDragEnd = {
                                                        draggingId = null
                                                        dragDistance = 0f
                                                        onUpdateSettings(settings.copy(customShellCommands = commands))
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragDistance += amount.y
                                                        val currentIndex = commands.indexOfFirst { it.id == command.id }
                                                        val swapThreshold = rowHeightPx / 2f
                                                        val targetIndex =
                                                            when {
                                                                dragDistance >= swapThreshold && currentIndex < commands.lastIndex -> currentIndex + 1
                                                                dragDistance <= -swapThreshold && currentIndex > 0 -> currentIndex - 1
                                                                else -> currentIndex
                                                            }
                                                        if (targetIndex != currentIndex && currentIndex >= 0) {
                                                            commands = moveListItem(commands, currentIndex, targetIndex)
                                                            dragDistance += if (targetIndex > currentIndex) -rowHeightPx else rowHeightPx
                                                        }
                                                    },
                                                )
                                            }
                                            .padding(8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        key(editingCommand?.id ?: "new-command") {
            CustomCommandEditorDialog(
                command = editingCommand,
                onDismiss = {
                    showEditor = false
                    editingCommand = null
                },
                onConfirm = { name, commandValue ->
                    val editing = editingCommand
                    val updated =
                        if (editing == null) {
                            commands +
                                CustomShellCommand(
                                    id = UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    command = commandValue.trim(),
                                )
                        } else {
                            commands.map { current ->
                                if (current.id == editing.id) {
                                    current.copy(name = name.trim(), command = commandValue.trim())
                                } else {
                                    current
                                }
                            }
                        }
                    saveCommands(updated)
                    showEditor = false
                    editingCommand = null
                },
            )
        }
    }
}

internal fun <T> moveListItem(
    items: List<T>,
    fromIndex: Int,
    toIndex: Int,
): List<T> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return items
    return items.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

@Composable
private fun CustomCommandEditorDialog(
    command: CustomShellCommand?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(command?.name.orEmpty()) }
    var commandValue by remember { mutableStateOf(command?.command.orEmpty()) }
    val title =
        if (command == null) {
            SettingsTexts.CUSTOM_COMMAND_ADD.get()
        } else {
            SettingsTexts.CUSTOM_COMMAND_EDIT.get()
        }

    DialogPage(
        title = title,
        onDismiss = onDismiss,
        showBackButton = false,
        leftButtonText = CommonTexts.BUTTON_CANCEL.get(),
        rightButtonText = CommonTexts.BUTTON_SAVE.get(),
        rightButtonEnabled = name.isNotBlank() && commandValue.isNotBlank(),
        onRightButtonClick = { onConfirm(name, commandValue) },
    ) {
        SettingsCard(title = title) {
            LabeledTextField(
                label = SettingsTexts.CUSTOM_COMMAND_NAME.get(),
                value = name,
                onValueChange = { name = it },
                placeholder = SettingsTexts.CUSTOM_COMMAND_NAME_PLACEHOLDER.get(),
            )
            SettingsDivider()
            MultilineShellCommandField(
                value = commandValue,
                onValueChange = { commandValue = it },
                placeholder = SettingsTexts.CUSTOM_COMMAND_VALUE_PLACEHOLDER.get(),
            )
        }
    }
}

@Composable
private fun MultilineShellCommandField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = IosDesignTokens.standardHorizontalPadding,
                    vertical = IosDesignTokens.compactSpacing,
                ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = SettingsTexts.CUSTOM_COMMAND_VALUE.get(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(IosDesignTokens.compactCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 180.dp)
                        .padding(12.dp),
                textStyle =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = false,
                minLines = 4,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}
