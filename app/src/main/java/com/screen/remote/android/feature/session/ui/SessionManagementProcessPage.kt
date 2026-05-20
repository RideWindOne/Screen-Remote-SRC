package com.screen.remote.android.feature.session.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.common.AppColors
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.infrastructure.adb.connection.AdbBridge
import kotlinx.coroutines.launch
import java.util.Locale
import com.screen.remote.android.core.designsystem.component.IOSAlertDialog as AlertDialog

private val ProcessChildListPaddingTop = 2.dp
private val ProcessChildRowHorizontalPadding = 12.dp
private val ProcessChildRowVerticalPadding = 1.dp
private val ProcessChildAvatarScale = 0.88f
private val ProcessChildDividerInsetStart = 66.dp
private val ProcessChildDividerInsetEnd = 12.dp
private val ProcessCardHorizontalPadding = 12.dp
private val ProcessRowHorizontalPadding = 10.dp
private val ProcessRowVerticalPadding = 10.dp
private val ProcessMemoryLabelWidth = 62.dp
private val ProcessActionSlotWidth = 76.dp
private const val ProcessPanelWidthFraction = 0.985f

@Composable
internal fun SessionManagementProcessPage(
    modifier: Modifier = Modifier,
    snapshot: DeviceDashboardSnapshot,
    refreshToken: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val helperJar = remember(context) { ensureLocalAppIconHelperJar(context) }
    val expandedPackages = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val appPresentationVersions = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    var actionProgress by remember { mutableStateOf<String?>(null) }
    var actionResult by remember { mutableStateOf<String?>(null) }
    var helperReady by remember { mutableStateOf(false) }
    var searchQuery by remember(refreshToken) { mutableStateOf("") }

    LaunchedEffect(Unit) {
        SessionManagementAppCache.prepareForProcess(context)
    }

    LaunchedEffect(Unit) {
        val connection = AdbBridge.getConnection()
        helperReady = connection?.prepareAppIconHelper(helperJar)?.isSuccess == true
    }

    val processSnapshot by produceState(
        initialValue = ProcessListSnapshot.loading(),
        key1 = refreshToken,
    ) {
        value = loadProcessListSnapshot()
    }

    val totalMemoryBytes = parseDisplayBytes(snapshot.memoryTotal)
    val availableMemoryBytes = parseDisplayBytes(snapshot.memoryAvailable)
    val usedMemoryBytes =
        if (totalMemoryBytes != null && availableMemoryBytes != null) {
            (totalMemoryBytes - availableMemoryBytes).coerceAtLeast(0L)
        } else {
            null
        }
    val progress =
        if (totalMemoryBytes != null && totalMemoryBytes > 0 && usedMemoryBytes != null) {
            usedMemoryBytes.toFloat() / totalMemoryBytes.toFloat()
        } else {
            0f
        }
    val processEntries = processSnapshot.entries
    val filteredEntries =
        if (searchQuery.isBlank()) {
            processEntries
        } else {
            val keyword = searchQuery.trim().lowercase(Locale.getDefault())
            processEntries.filter { entry ->
                entry.appTitle.lowercase(Locale.getDefault()).contains(keyword) ||
                    entry.packageName.lowercase(Locale.getDefault()).contains(keyword) ||
                    entry.children.any { it.name.lowercase(Locale.getDefault()).contains(keyword) }
            }
        }
    val topMemoryEntry = processEntries.maxByOrNull { it.totalMemoryBytes }
    val appProcessCount = processEntries.sumOf { 1 + it.children.size }

    LaunchedEffect(helperReady, processEntries.map { it.packageName }) {
        if (!helperReady || processEntries.isEmpty()) {
            return@LaunchedEffect
        }
        runCatching {
            prefetchAppIconsWithHelper(
                context = context,
                entries = processEntries.map { it.toAppInventoryEntry() },
                helperJar = helperJar,
            ) { updatedPackages ->
                updatedPackages.forEach { packageName ->
                    appPresentationVersions[packageName] = (appPresentationVersions[packageName] ?: 0) + 1
                }
            }
        }
    }

    fun stopProcess(entry: ProcessEntry) {
        actionProgress = ManagementTexts.text("正在结束 ${entry.appTitle}", "Stopping ${entry.appTitle}")
        scope.launch {
            val result =
                runCatching {
                    val connection = AdbBridge.getConnection() ?: error(ManagementTexts.text("当前没有可用的 ADB 连接。", "No ADB connection is available."))
                    connection
                        .executeShell("am force-stop ${entry.packageName}", retryOnFailure = false)
                        .getOrThrow()
                    ManagementTexts.text("已尝试结束 ${entry.packageName} 的运行进程。", "Tried to stop processes for ${entry.packageName}.")
                }
            actionProgress = null
            actionResult = result.getOrNull() ?: (result.exceptionOrNull()?.message ?: ManagementTexts.text("结束进程失败。", "Couldn't stop the process."))
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SessionManagementProcessMemoryCard(
                memoryTotal = snapshot.memoryTotal,
                usedMemory = usedMemoryBytes?.let(::formatBytes) ?: "--",
                availableMemory = snapshot.memoryAvailable.ifBlank { "--" },
                appProcessCount = if (processEntries.isEmpty()) "--" else appProcessCount.toString(),
                progress = progress,
            )
        }

        when {
            processSnapshot.isLoading -> {
                item {
                    SessionManagementNoteCard(
                        title = ManagementTexts.text("正在读取进程列表", "Loading processes"),
                        text = ManagementTexts.text("当前通过 ADB 加载正在运行的应用进程和内存占用。", "Loading running app processes and memory usage over ADB."),
                    )
                }
            }

            processSnapshot.errorMessage != null -> {
                item {
                    SessionManagementNoteCard(
                        title = ManagementTexts.text("进程列表读取失败", "Couldn't load processes"),
                        text = processSnapshot.errorMessage ?: ManagementTexts.text("进程列表读取失败。", "Couldn't load the process list."),
                    )
                }
            }

            else -> {
                item {
                    SessionManagementProcessList(
                        entries = filteredEntries,
                        allEntries = processEntries,
                        expandedPackages = expandedPackages,
                        appPresentationVersions = appPresentationVersions,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onStop = ::stopProcess,
                    )
                }
            }
        }
    }

    actionProgress?.let { message ->
        SessionManagementProgressDialog(
            title = ManagementTexts.text("进程管理", "Processes"),
            message = message,
        )
    }

    actionResult?.let { message ->
        AlertDialog(
            onDismissRequest = { actionResult = null },
            title = { Text(ManagementTexts.text("进程管理", "Processes")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { actionResult = null }) {
                    Text(ManagementTexts.text("确定", "OK"))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun SessionManagementProcessMemoryCard(
    memoryTotal: String,
    usedMemory: String,
    availableMemory: String,
    appProcessCount: String,
    progress: Float,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(ProcessPanelWidthFraction),
        shape = RoundedCornerShape(AppDimens.cardCornerRadius),
        color = managementPanelColor(),
        tonalElevation = 0.5.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(ProcessCardHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ManagementTexts.text("设备内存", "Memory"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = memoryTotal.ifBlank { "--" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp)),
                drawStopIndicator = {},
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SessionManagementProcessStatTile(
                    label = ManagementTexts.text("已用", "Used"),
                    value = usedMemory,
                    accent = Color(0xFFFF9F0A),
                    modifier = Modifier.weight(1f),
                )
                SessionManagementProcessStatTile(
                    label = ManagementTexts.text("可用", "Free"),
                    value = availableMemory,
                    accent = Color(0xFF34C759),
                    modifier = Modifier.weight(1f),
                )
                SessionManagementProcessStatTile(
                    label = ManagementTexts.text("应用进程", "App procs"),
                    value = appProcessCount,
                    accent = AppColors.iOSBlue,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SessionManagementProcessList(
    entries: List<ProcessEntry>,
    allEntries: List<ProcessEntry>,
    expandedPackages: MutableMap<String, Boolean>,
    appPresentationVersions: Map<String, Int>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onStop: (ProcessEntry) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(ProcessPanelWidthFraction),
        shape = RoundedCornerShape(AppDimens.cardCornerRadius),
        color = managementPanelColor(),
        tonalElevation = 0.5.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(ProcessCardHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ManagementTexts.text("运行中的应用", "Running apps"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                SessionManagementUtilityBadge(
                    text = ManagementTexts.countLabel(entries.size),
                    accent = AppColors.iOSBlue,
                    available = true,
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = ManagementTexts.text("清空", "Clear"),
                            )
                        }
                    }
                },
                placeholder = {
                    Text(
                        text = ManagementTexts.text("搜索应用或包名", "Search apps or packages"),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )

            if (entries.isEmpty()) {
                SessionManagementNoteCard(
                    title = ManagementTexts.text("没有匹配结果", "No results"),
                    text =
                        if (allEntries.isEmpty()) {
                            ManagementTexts.text("当前没有可展示的应用进程。", "There are no app processes to show.")
                        } else {
                            ManagementTexts.text("试试别的关键词。", "Try a different keyword.")
                        },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    entries.forEachIndexed { index, entry ->
                        val expanded = expandedPackages[entry.packageName] == true
                        SessionManagementProcessRow(
                            entry = entry,
                            rank = index + 1,
                            expanded = expanded,
                            presentationVersion = appPresentationVersions[entry.packageName] ?: 0,
                            onToggleExpanded = {
                                expandedPackages[entry.packageName] = !expanded
                            },
                            onStop = { onStop(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionManagementProcessStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.1f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SessionManagementProcessRow(
    entry: ProcessEntry,
    rank: Int,
    expanded: Boolean,
    presentationVersion: Int,
    onToggleExpanded: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val hasChildren = entry.children.isNotEmpty()
    val isSystemApp = entry.packageName.startsWith("com.android") || entry.packageName.startsWith("android")
    val presentation by produceState(
        initialValue =
            RemoteAppPresentation(
                title = entry.appTitle,
                icon = SessionManagementAppCache.cachedIcon(entry.packageName),
            ),
        entry.packageName,
        entry.appTitle,
        entry.totalMemoryBytes,
        presentationVersion,
    ) {
        value =
            loadCachedAppPresentation(
                context = context,
                entry = entry.toAppInventoryEntry(),
                packageNameOnlyMode = false,
            )
    }
    val displayTitle = presentation.title
    val iconBitmap = presentation.icon

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.5.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ProcessRowHorizontalPadding, vertical = ProcessRowVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SessionManagementAppAvatar(
                    packageName = entry.packageName,
                    appTitle = displayTitle.ifBlank { rank.toString() },
                    isSystemApp = isSystemApp,
                    iconBitmap = iconBitmap,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.memory,
                        modifier = Modifier.size(width = ProcessMemoryLabelWidth, height = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                    )

                    Row(
                        modifier = Modifier.size(width = ProcessActionSlotWidth, height = 36.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasChildren) {
                            IconButton(
                                onClick = onToggleExpanded,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector =
                                        if (expanded) {
                                            Icons.Default.KeyboardArrowUp
                                        } else {
                                            Icons.Default.KeyboardArrowDown
                                        },
                                    contentDescription =
                                        if (expanded) {
                                            ManagementTexts.text("收起子进程", "Collapse child processes")
                                        } else {
                                            ManagementTexts.text("展开子进程", "Expand child processes")
                                        },
                                )
                            }
                        }

                        IconButton(
                            onClick = onStop,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = ManagementTexts.text("结束进程", "Stop process"),
                                tint = AppColors.iOSBlue,
                            )
                        }
                    }
                }
            }

            if (expanded && hasChildren) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f))
                            .padding(top = ProcessChildListPaddingTop, bottom = 2.dp),
                ) {
                    entry.children.forEachIndexed { index, child ->
                        SessionManagementProcessChildRow(
                            entry = entry,
                            appTitle = displayTitle,
                            child = child,
                            iconBitmap = iconBitmap,
                        )
                        if (index != entry.children.lastIndex) {
                            AppDivider(
                                modifier =
                                    Modifier.padding(
                                        start = ProcessChildDividerInsetStart,
                                        end = ProcessChildDividerInsetEnd,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionManagementProcessChildRow(
    entry: ProcessEntry,
    appTitle: String,
    child: ProcessChildEntry,
    iconBitmap: Bitmap?,
) {
    val isSystemApp = entry.packageName.startsWith("com.android") || entry.packageName.startsWith("android")

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = ProcessChildRowHorizontalPadding,
                    vertical = ProcessChildRowVerticalPadding,
                ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.scale(ProcessChildAvatarScale)) {
                SessionManagementAppAvatar(
                    packageName = entry.packageName,
                    appTitle = appTitle,
                    isSystemApp = isSystemApp,
                    iconBitmap = iconBitmap,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = appTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = child.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = child.memory,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.End,
        )
    }
}

private data class ProcessEntry(
    val packageName: String,
    val appTitle: String,
    val pid: String,
    val totalMemoryBytes: Long,
    val memory: String,
    val children: List<ProcessChildEntry>,
)

private fun ProcessEntry.toAppInventoryEntry(): AppInventoryEntry =
    AppInventoryEntry(
        packageName = packageName,
        appTitle = appTitle,
        isSystemApp = packageName.startsWith("com.android") || packageName.startsWith("android"),
        apkPath = "",
        isEnabled = true,
    )

private data class ProcessChildEntry(
    val name: String,
    val pid: String,
    val memoryBytes: Long,
    val memory: String,
)

private data class RawProcessEntry(
    val name: String,
    val pid: String,
    val memoryBytes: Long,
)

private data class ProcessListSnapshot(
    val isLoading: Boolean,
    val entries: List<ProcessEntry>,
    val errorMessage: String? = null,
) {
    companion object {
        fun loading(): ProcessListSnapshot =
            ProcessListSnapshot(
                isLoading = true,
                entries = emptyList(),
            )
    }
}

private suspend fun loadProcessListSnapshot(): ProcessListSnapshot {
    val connection =
        AdbBridge.getConnection()
            ?: return ProcessListSnapshot.loading().copy(
                isLoading = false,
                errorMessage = ManagementTexts.text("当前没有可用的 ADB 连接，无法读取进程列表。", "No ADB connection is available, so the process list can't be loaded."),
            )

    return runCatching {
        val output =
            connection
                .executeShell(
                    "ps -A -o PID,RSS,NAME 2>/dev/null || ps -A",
                    retryOnFailure = false,
                ).getOrThrow()

        val entries =
            output
                .lineSequence()
                .mapNotNull(::parseProcessLine)
                .filter { it.name.isAppProcessName() }
                .groupBy { it.name.substringBefore(':') }
                .map { (packageName, processes) ->
                    val sortedProcesses = processes.sortedByDescending { it.memoryBytes }
                    val children =
                        sortedProcesses.map { process ->
                            ProcessChildEntry(
                                name = process.name,
                                pid = process.pid,
                                memoryBytes = process.memoryBytes,
                                memory = formatProcessMemory(process.memoryBytes),
                            )
                        }
                    val totalMemoryBytes = processes.sumOf { it.memoryBytes }

                    ProcessEntry(
                        packageName = packageName,
                        appTitle = SessionManagementAppCache.appTitle(packageName, guessAppTitle(packageName)),
                        pid =
                            sortedProcesses
                                .firstOrNull { it.name == packageName }
                                ?.pid
                                ?: sortedProcesses.firstOrNull()?.pid.orEmpty(),
                        totalMemoryBytes = totalMemoryBytes,
                        memory = formatProcessMemory(totalMemoryBytes),
                        children = if (children.size > 1) children else emptyList(),
                    )
                }.sortedByDescending { it.totalMemoryBytes }

        ProcessListSnapshot(
            isLoading = false,
            entries = entries,
            errorMessage = if (entries.isEmpty()) ManagementTexts.text("未读取到正在运行的应用进程。", "No running app processes were found.") else null,
        )
    }.getOrElse { error ->
        ProcessListSnapshot.loading().copy(
            isLoading = false,
            errorMessage = error.message ?: ManagementTexts.text("进程列表读取失败。", "Couldn't load the process list."),
        )
    }
}

private fun parseProcessLine(line: String): RawProcessEntry? {
    val tokens = line.trim().split(Regex("\\s+"))
    if (tokens.size < 3 || tokens.first().equals("PID", ignoreCase = true)) return null

    val compactPid = tokens.getOrNull(0)?.takeIf { it.all(Char::isDigit) }
    val compactRssKb = tokens.getOrNull(1)?.toLongOrNull()
    val compactName = tokens.drop(2).joinToString(" ").trim()
    if (compactPid != null && compactRssKb != null && compactName.isNotBlank()) {
        return RawProcessEntry(
            name = compactName,
            pid = compactPid,
            memoryBytes = compactRssKb * 1024,
        )
    }

    val defaultPid = tokens.getOrNull(1)?.takeIf { it.all(Char::isDigit) } ?: return null
    val defaultRssKb = tokens.getOrNull(4)?.toLongOrNull() ?: return null
    val defaultName = tokens.lastOrNull()?.trim().orEmpty()
    if (defaultName.isBlank()) return null

    return RawProcessEntry(
        name = defaultName,
        pid = defaultPid,
        memoryBytes = defaultRssKb * 1024,
    )
}

private fun String.isAppProcessName(): Boolean {
    val basePackage = substringBefore(':')
    return basePackage.contains('.') &&
        !basePackage.contains('/') &&
        !basePackage.startsWith("[") &&
        basePackage.any { it.isLetter() }
}

private fun formatProcessMemory(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    if (mb < 1024) {
        return String.format(Locale.US, "%.1f MB", mb)
    }
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return String.format(Locale.US, "%.2f G", gb)
}

private fun parseDisplayBytes(text: String): Long? {
    val match =
        Regex("""([0-9]+(?:\.[0-9]+)?)\s*([kmgt]?b?|[kmgt])""", RegexOption.IGNORE_CASE).find(text)
            ?: return null
    val value = match.groupValues[1].toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].lowercase(Locale.US)
    val multiplier =
        when (unit) {
            "t", "tb" -> 1024.0 * 1024 * 1024 * 1024
            "g", "gb" -> 1024.0 * 1024 * 1024
            "m", "mb" -> 1024.0 * 1024
            "k", "kb" -> 1024.0
            "b", "" -> 1.0
            else -> return null
        }
    return (value * multiplier).toLong()
}
