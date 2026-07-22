package com.screen.remote.android.feature.settings.ui

import android.os.Build
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toDrawable
import com.screen.remote.android.core.common.manager.LiveLogEntry
import com.screen.remote.android.core.common.manager.LiveLogStore
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.i18n.DebugTexts
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

private enum class LogBackground(
    val alpha: Float,
) {
    OPAQUE(1f),
    SOLID(0.88f),
    SEMI_OPAQUE(0.68f),
    TRANSLUCENT(0.48f),
    ;

    fun next(): LogBackground = entries[(ordinal + 1) % entries.size]
}

private data class DebugLogTexts(
    val title: String,
    val following: String,
    val notFollowing: String,
    val openLog: String,
    val switchOpacity: String,
    val switchHalfScreen: String,
    val switchFullScreen: String,
    val clearLog: String,
    val closeLog: String,
    val returnToBottom: String,
)

@Composable
private fun rememberDebugLogTexts() =
    DebugLogTexts(
        title = rememberText(DebugTexts.LOG_TITLE),
        following = rememberText(DebugTexts.FOLLOWING),
        notFollowing = rememberText(DebugTexts.NOT_FOLLOWING),
        openLog = rememberText(DebugTexts.OPEN_LOG),
        switchOpacity = rememberText(DebugTexts.SWITCH_OPACITY),
        switchHalfScreen = rememberText(DebugTexts.SWITCH_HALF_SCREEN),
        switchFullScreen = rememberText(DebugTexts.SWITCH_FULL_SCREEN),
        clearLog = rememberText(DebugTexts.CLEAR_LOG),
        closeLog = rememberText(DebugTexts.CLOSE_LOG),
        returnToBottom = rememberText(DebugTexts.RETURN_TO_BOTTOM),
    )

/**
 * The app's single debug surface. Its attached window stays above regular app dialogs while the
 * same composition and scroll state are retained as it changes between button, half and full size.
 */
@Composable
fun DebugLogOverlay(enabled: Boolean) {
    if (!enabled) return

    val containerSize = LocalWindowInfo.current.containerSize
    val expanded = remember { mutableStateOf(false) }
    val fullScreen = remember { mutableStateOf(false) }

    SingleDebugWindow(
        onBackPressed = {
            expanded.value = false
            fullScreen.value = false
        },
    ) { window ->
        val texts = rememberDebugLogTexts()
        val density = LocalDensity.current
        val collapsedSizePx = with(density) { 64.dp.toPx() }
        val initialMarginPx = with(density) { 9.dp.toPx() }
        val screenWidthPx = containerSize.width.toFloat()
        val screenHeightPx = containerSize.height.toFloat()
        var buttonOffsetX by remember { mutableFloatStateOf(initialMarginPx) }
        var buttonOffsetY by remember { mutableFloatStateOf(initialMarginPx) }

        ConfigureDebugWindow(
            window = window,
            expanded = expanded.value,
            fullScreen = fullScreen.value,
            collapsedX = buttonOffsetX.roundToInt(),
            collapsedY = buttonOffsetY.roundToInt(),
            containerHeightPx = containerSize.height,
        )

        if (expanded.value) {
            LiveLogPanel(
                fullScreen = fullScreen.value,
                texts = texts,
                onToggleFullScreen = { fullScreen.value = !fullScreen.value },
                onClose = {
                    expanded.value = false
                    fullScreen.value = false
                    buttonOffsetX = initialMarginPx
                    buttonOffsetY = initialMarginPx
                },
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                FloatingActionButton(
                    onClick = { expanded.value = true },
                    modifier =
                        Modifier
                            .size(46.dp)
                            .pointerInput(screenWidthPx, screenHeightPx) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    buttonOffsetX =
                                        (buttonOffsetX - dragAmount.x)
                                            .coerceIn(0f, (screenWidthPx - collapsedSizePx).coerceAtLeast(0f))
                                    buttonOffsetY =
                                        (buttonOffsetY - dragAmount.y)
                                            .coerceIn(0f, (screenHeightPx - collapsedSizePx).coerceAtLeast(0f))
                                }
                            },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation =
                        FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = texts.openLog,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleDebugWindow(
    onBackPressed: () -> Unit,
    content: @Composable (Window) -> Unit,
) {
    val context = LocalContext.current
    val parentComposition = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val currentOnBackPressed by rememberUpdatedState(onBackPressed)

    DisposableEffect(context, parentComposition) {
        val dialog = ComponentDialog(context)
        dialog.onBackPressedDispatcher.addCallback(dialog) {
            currentOnBackPressed()
        }
        val window = checkNotNull(dialog.window)
        window.setType(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
        )
        window.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )

        val composeView =
            ComposeView(context).apply {
                setParentCompositionContext(parentComposition)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent { currentContent(window) }
            }
        dialog.setContentView(composeView)
        dialog.show()

        onDispose {
            dialog.dismiss()
        }
    }
}

@Composable
private fun ConfigureDebugWindow(
    window: Window,
    expanded: Boolean,
    fullScreen: Boolean,
    collapsedX: Int,
    collapsedY: Int,
    containerHeightPx: Int,
) {
    val density = LocalDensity.current
    val collapsedSize = with(density) { 64.dp.roundToPx() }
    val halfHeight = containerHeightPx / 2

    SideEffect {
        if (expanded) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        window.setGravity(if (expanded) Gravity.BOTTOM else Gravity.BOTTOM or Gravity.END)
        window.attributes =
            window.attributes.apply {
                width =
                    if (expanded) {
                        WindowManager.LayoutParams.MATCH_PARENT
                    } else {
                        collapsedSize
                    }
                height =
                    when {
                        !expanded -> collapsedSize
                        fullScreen -> WindowManager.LayoutParams.MATCH_PARENT
                        else -> halfHeight
                    }
                x = if (expanded) 0 else collapsedX
                y = if (expanded) 0 else collapsedY
            }
    }
}

@Composable
private fun LiveLogPanel(
    fullScreen: Boolean,
    texts: DebugLogTexts,
    onToggleFullScreen: () -> Unit,
    onClose: () -> Unit,
) {
    val entries by LiveLogStore.entries.collectAsState()
    val listState = rememberLazyListState()
    var followLatest by remember { mutableStateOf(true) }
    var background by remember { mutableStateOf(LogBackground.OPAQUE) }
    val panelColor = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()
            val isAtBottom =
                layout.totalItemsCount == 0 ||
                    (last?.index == layout.totalItemsCount - 1 &&
                        last.offset + last.size <= layout.viewportEndOffset + 4)
            listState.isScrollInProgress to isAtBottom
        }.distinctUntilChanged().collect { (isScrolling, isAtBottom) ->
            if (isScrolling) followLatest = isAtBottom
        }
    }

    LaunchedEffect(entries.lastOrNull()?.id, followLatest) {
        if (followLatest && entries.isNotEmpty()) {
            listState.scrollToItem(entries.lastIndex)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(panelColor.copy(alpha = background.alpha))
                .then(if (fullScreen) Modifier.statusBarsPadding() else Modifier)
                .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = texts.title,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium.copy(shadow = logTextShadow()),
                )
                Text(
                    text = "  ${entries.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.bodySmall.copy(shadow = logTextShadow()),
                )
                Text(
                    text = "  ${if (followLatest) texts.following else texts.notFollowing}",
                    color =
                        if (followLatest) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.bodySmall.copy(shadow = logTextShadow()),
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { background = background.next() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.Opacity,
                        contentDescription = texts.switchOpacity,
                        tint = contentColor,
                    )
                }
                IconButton(
                    onClick = onToggleFullScreen,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector =
                            if (fullScreen) {
                                Icons.Default.FullscreenExit
                            } else {
                                Icons.Default.Fullscreen
                            },
                        contentDescription =
                            if (fullScreen) texts.switchHalfScreen else texts.switchFullScreen,
                        tint = contentColor,
                    )
                }
                IconButton(
                    onClick = LiveLogStore::clear,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = texts.clearLog,
                        tint = contentColor,
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = texts.closeLog,
                        tint = contentColor,
                    )
                }
            }

            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(entries, key = LiveLogEntry::id) { entry ->
                        LogLine(entry)
                    }
                }
            }
        }

        if (!followLatest && entries.isNotEmpty()) {
            Surface(
                onClick = { followLatest = true },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 4.dp,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = texts.returnToBottom,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun LogLine(entry: LiveLogEntry) {
    val levelColor = logLevelColor(entry.level)
    Text(
        text =
            buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = levelColor,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append(entry.level.take(1))
                }
                withStyle(SpanStyle(color = levelColor.copy(alpha = 0.85f))) {
                    append("/${entry.tag}: ")
                }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                    append(entry.message.trimStart('\r', '\n'))
                }
            },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 1.dp),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        style = MaterialTheme.typography.bodySmall.copy(shadow = logTextShadow()),
    )
}

@Composable
private fun logLevelColor(level: String): Color =
    when (level.uppercase().firstOrNull()) {
        'E', 'F' -> MaterialTheme.colorScheme.error
        'W' -> MaterialTheme.colorScheme.tertiary
        'I' -> MaterialTheme.colorScheme.primary
        'D' -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun logTextShadow(): Shadow =
    Shadow(
        color =
            if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
                Color.White
            } else {
                Color.Black
            },
        blurRadius = 3f,
    )
