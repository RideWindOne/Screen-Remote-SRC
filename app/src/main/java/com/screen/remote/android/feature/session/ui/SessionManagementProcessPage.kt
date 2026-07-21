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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.designsystem.component.AppDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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

private data class ProcessMemorySnapshot(
    val totalBytes: Long?,
    val availableBytes: Long?,
)

private fun projectVisibleProcesses(
    entries: List<ProcessEntry>,
    normalizedSearchQuery: String,
): List<ProcessEntry> {
    if (normalizedSearchQuery.isBlank()) {
        return entries
    }

    return entries.filter { entry ->
        val displayTitle = SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle)
        matchesProcessSearch(
            displayTitle = displayTitle,
            packageName = entry.packageName,
            childNames = entry.children.map(ProcessChildEntry::name),
            query = normalizedSearchQuery,
        )
    }
}

internal fun matchesProcessSearch(
    displayTitle: String,
    packageName: String,
    childNames: List<String>,
    query: String,
): Boolean {
    val locale = Locale.getDefault()
    val normalizedQuery = query.trim().lowercase(locale)
    if (normalizedQuery.isBlank()) return true
    return displayTitle.lowercase(locale).contains(normalizedQuery) ||
        packageName.lowercase(locale).contains(normalizedQuery) ||
        childNames.any { name -> name.lowercase(locale).contains(normalizedQuery) }
}

@Composable
internal fun SessionManagementProcessPage(
    modifier: Modifier = Modifier,
    snapshot: DeviceDashboardSnapshot,
    refreshToken: Int,
    cacheScopeKey: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var helperJar by remember(context) { mutableStateOf<java.io.File?>(null) }
    val expandedPackages = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val appPresentationVersions = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    var appPresentationGeneration by remember { mutableIntStateOf(0) }
    var actionProgress by remember { mutableStateOf<String?>(null) }
    var actionResult by remember { mutableStateOf<String?>(null) }
    var helperReady by remember { mutableStateOf(false) }
    var cacheReady by remember(cacheScopeKey) { mutableStateOf(false) }
    var searchQuery by remember(refreshToken) { mutableStateOf("") }
    var submittedSearchQuery by remember(refreshToken) { mutableStateOf("") }
    var processSnapshot by remember { mutableStateOf(ProcessListSnapshot.loading()) }
    var processMemorySnapshot by remember {
        mutableStateOf(
            ProcessMemorySnapshot(
                totalBytes = parseDisplayBytes(snapshot.memoryTotal),
                availableBytes = parseDisplayBytes(snapshot.memoryAvailable),
            ),
        )
    }
    var processRefreshing by remember { mutableStateOf(true) }

    LaunchedEffect(context, cacheScopeKey) {
        SessionManagementAppCache.prepareForSession(context, cacheScopeKey)
        cacheReady = true
        helperJar = withContext(Dispatchers.IO) { ensureLocalDadbHelperJar(context) }
    }

    LaunchedEffect(helperJar) {
        val readyHelperJar = helperJar ?: return@LaunchedEffect
        val connection = SessionManagementAdbConnection.current()
        helperReady = connection?.prepareAppIconHelper(readyHelperJar)?.isSuccess == true
    }

    LaunchedEffect(cacheReady, refreshToken) {
        if (!cacheReady) return@LaunchedEffect
        val canKeepCurrentContent = processSnapshot.entries.isNotEmpty() && processSnapshot.errorMessage == null
        processRefreshing = true
        val (nextSnapshot, nextMemorySnapshot) =
            coroutineScope {
                val processDeferred = async { loadProcessListSnapshot() }
                val memoryDeferred = async { loadProcessMemorySnapshot() }
                processDeferred.await() to memoryDeferred.await()
            }
        processRefreshing = false
        nextMemorySnapshot?.let { processMemorySnapshot = it }
        if (nextSnapshot.errorMessage != null && canKeepCurrentContent) {
            actionResult = nextSnapshot.errorMessage
        } else {
            processSnapshot = nextSnapshot
        }
    }

    LaunchedEffect(snapshot.memoryTotal, snapshot.memoryAvailable) {
        val totalBytes = parseDisplayBytes(snapshot.memoryTotal)
        val availableBytes = parseDisplayBytes(snapshot.memoryAvailable)
        if (totalBytes != null || availableBytes != null) {
            processMemorySnapshot =
                ProcessMemorySnapshot(
                    totalBytes = totalBytes,
                    availableBytes = availableBytes,
                )
        }
    }

    val totalMemoryBytes = processMemorySnapshot.totalBytes
    val availableMemoryBytes = processMemorySnapshot.availableBytes
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
    val processPackageNames by remember {
        derivedStateOf { processSnapshot.entries.map { it.packageName } }
    }
    val processInventoryEntries by remember {
        derivedStateOf { processSnapshot.entries.map { it.toAppInventoryEntry() } }
    }
    val normalizedSearchQuery by remember {
        derivedStateOf { submittedSearchQuery.trim().lowercase(Locale.getDefault()) }
    }
    val visibleEntries =
        remember(processEntries, normalizedSearchQuery, appPresentationGeneration) {
            projectVisibleProcesses(
                entries = processEntries,
                normalizedSearchQuery = normalizedSearchQuery,
            )
        }
    val appProcessCount by remember {
        derivedStateOf { processSnapshot.entries.sumOf { 1 + it.children.size } }
    }

    LaunchedEffect(helperReady, processPackageNames) {
        if (processEntries.isEmpty()) {
            return@LaunchedEffect
        }
        runCatching {
            val warmedPackages = warmCachedAppPresentations(
                context = context,
                entries = processInventoryEntries,
                packageNameOnlyMode = false,
            )
            if (warmedPackages.isNotEmpty()) {
                Snapshot.withMutableSnapshot {
                    warmedPackages.forEach { packageName ->
                        appPresentationVersions[packageName] = (appPresentationVersions[packageName] ?: 0) + 1
                    }
                    appPresentationGeneration += 1
                }
            }
            val readyHelperJar = helperJar
            if (!helperReady || readyHelperJar == null) {
                return@runCatching
            }
            prefetchAppIconsWithHelper(
                context = context,
                entries = processInventoryEntries,
                helperJar = readyHelperJar,
            ) { updatedPackages ->
                if (updatedPackages.isNotEmpty()) {
                    Snapshot.withMutableSnapshot {
                        updatedPackages.forEach { packageName ->
                            appPresentationVersions[packageName] = (appPresentationVersions[packageName] ?: 0) + 1
                        }
                        appPresentationGeneration += 1
                    }
                }
            }
        }
    }

    fun stopProcess(entry: ProcessEntry) {
        actionProgress = ManagementTexts.Processes.STOPPING.format(entry.appTitle)
        scope.launch {
            val result =
                runCatching {
                    val connection = SessionManagementAdbConnection.current() ?: error(ManagementTexts.Processes.NO_ADB_CONNECTION_AVAILABLE.get())
                    connection
                        .executeShell("am force-stop ${entry.packageName}", retryOnFailure = false)
                        .getOrThrow()
                    ManagementTexts.Processes.TRIED_STOP_PROCESSES.format(entry.packageName)
                }
            actionProgress = null
            actionResult = result.getOrNull() ?: (result.exceptionOrNull()?.message ?: ManagementTexts.Processes.COULDN_T_STOP_PROCESS.get())
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                SessionManagementProcessMemoryCard(
                    memoryTotal = totalMemoryBytes?.let(::formatBytes).orEmpty(),
                    usedMemory = usedMemoryBytes?.let(::formatBytes) ?: "--",
                    availableMemory = availableMemoryBytes?.let(::formatBytes) ?: "--",
                    appProcessCount = if (processEntries.isEmpty()) "--" else appProcessCount.toString(),
                    progress = progress,
                )
            }
            item {
                Box(modifier = Modifier.height(14.dp))
            }

            when {
                processSnapshot.isLoading -> {
                    item {
                        SessionManagementProcessListHeader(
                            entries = emptyList(),
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onSearch = { submittedSearchQuery = it.trim() },
                        )
                    }
                    item {
                        Box(modifier = Modifier.height(14.dp))
                    }
                    repeat(6) { index ->
                        item(key = "process-placeholder-$index") {
                            SessionManagementVirtualizedPanelRow(
                                index = index,
                                totalCount = 6,
                                widthFraction = ProcessPanelWidthFraction,
                            ) {
                                SessionManagementProcessPlaceholderRow()
                            }
                        }
                    }
                }

                processSnapshot.errorMessage != null -> {
                    item {
                        SessionManagementNoteCard(
                            title = ManagementTexts.Processes.COULDN_T_LOAD_PROCESSES.get(),
                            text = processSnapshot.errorMessage ?: ManagementTexts.Processes.COULDN_T_LOAD_PROCESS_LIST.get(),
                        )
                    }
                }

                else -> {
                    item {
                        SessionManagementProcessListHeader(
                            entries = visibleEntries,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onSearch = { submittedSearchQuery = it.trim() },
                        )
                    }
                    item {
                        Box(modifier = Modifier.height(14.dp))
                    }
                    if (visibleEntries.isEmpty()) {
                        item {
                            SessionManagementNoteCard(
                                title = ManagementTexts.Processes.NO_RESULTS.get(),
                                text =
                                    if (processEntries.isEmpty()) {
                                        ManagementTexts.Processes.THERE_NO_APP_PROCESSES_SHOW.get()
                                    } else {
                                        ManagementTexts.Processes.TRY_DIFFERENT_KEYWORD.get()
                                    },
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = visibleEntries,
                            key = { _, entry -> entry.packageName },
                        ) { index, entry ->
                            val expanded = expandedPackages[entry.packageName] == true
                            SessionManagementVirtualizedPanelRow(
                                index = index,
                                totalCount = visibleEntries.size,
                                widthFraction = ProcessPanelWidthFraction,
                            ) {
                                SessionManagementProcessRow(
                                    entry = entry,
                                    rank = index + 1,
                                    expanded = expanded,
                                    presentationVersion = appPresentationVersions[entry.packageName] ?: 0,
                                    onToggleExpanded = {
                                        expandedPackages[entry.packageName] = !expanded
                                    },
                                    onStop = { stopProcess(entry) },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (processRefreshing) {
            SessionManagementLoadingBar(modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    actionProgress?.let { message ->
        SessionManagementProgressDialog(
            title = ManagementTexts.Processes.PROCESSES.get(),
            message = message,
        )
    }

    actionResult?.let { message ->
        SessionManagementMessageDialog(
            title = ManagementTexts.Processes.PROCESSES.get(),
            message = message,
            onDismiss = { actionResult = null },
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
        shape = SessionManagementCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
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
                    text = ManagementTexts.Processes.MEMORY.get(),
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
                    label = ManagementTexts.Processes.USED.get(),
                    value = usedMemory,
                    modifier = Modifier.weight(1f),
                )
                SessionManagementProcessStatTile(
                    label = ManagementTexts.Processes.FREE.get(),
                    value = availableMemory,
                    modifier = Modifier.weight(1f),
                )
                SessionManagementProcessStatTile(
                    label = ManagementTexts.Processes.APP_PROCS.get(),
                    value = appProcessCount,
                    modifier = Modifier.weight(1f),
                )
            }
        }

    }
}

@Composable
private fun SessionManagementProcessPlaceholderRow() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ProcessRowHorizontalPadding, vertical = ProcessRowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.background),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.58f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.background),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.42f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.background),
            )
        }
        Box(
            modifier =
                Modifier
                    .width(ProcessMemoryLabelWidth)
                    .height(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.background),
        )
        Box(
            modifier =
                Modifier
                    .size(width = ProcessActionSlotWidth, height = 36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background),
        )
    }
}

@Composable
private fun SessionManagementProcessListHeader(
    entries: List<ProcessEntry>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(ProcessPanelWidthFraction),
        shape = SessionManagementCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
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
                    text = ManagementTexts.Processes.RUNNING_APPS.get(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                SessionManagementUtilityBadge(
                    text = ManagementTexts.General.ITEM_COUNT.format(entries.size),
                    accent = MaterialTheme.colorScheme.primary,
                    available = true,
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(SessionManagementControlHeight),
                singleLine = true,
                shape = SessionManagementControlShape,
                trailingIcon = {
                    IconButton(onClick = { onSearch(searchQuery) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = ManagementTexts.Processes.SEARCH.get(),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = { onSearch(searchQuery) },
                    ),
                placeholder = {
                    Text(
                        text = ManagementTexts.Processes.SEARCH_APPS_PACKAGES.get(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun SessionManagementProcessStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val hasChildren = entry.children.isNotEmpty()
    val isSystemApp = entry.packageName.startsWith("com.android") || entry.packageName.startsWith("android")
    val displayTitle =
        remember(entry.packageName, entry.appTitle, presentationVersion) {
            SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle)
        }
    val iconBitmap =
        remember(entry.packageName, presentationVersion) {
            SessionManagementAppCache.cachedIcon(entry.packageName)
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
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
                                            ManagementTexts.Processes.COLLAPSE_CHILD_PROCESSES.get()
                                        } else {
                                            ManagementTexts.Processes.EXPAND_CHILD_PROCESSES.get()
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
                                contentDescription = ManagementTexts.Processes.STOP_PROCESS.get(),
                                tint = MaterialTheme.colorScheme.error,
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
                            .background(MaterialTheme.colorScheme.background)
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

private suspend fun loadProcessMemorySnapshot(): ProcessMemorySnapshot? {
    val connection = SessionManagementAdbConnection.current() ?: return null
    val output =
        connection
            .executeShell(
                "cat /proc/meminfo | grep -E '^MemTotal:|^MemAvailable:'",
                retryOnFailure = false,
            ).getOrNull()
            .orEmpty()

    return withContext(Dispatchers.Default) {
        val values =
            output
                .lineSequence()
                .mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) return@mapNotNull null
                    val key = line.substring(0, separator).trim()
                    val kilobytes =
                        line
                            .substring(separator + 1)
                            .trim()
                            .substringBefore(' ')
                            .toLongOrNull()
                            ?: return@mapNotNull null
                    key to kilobytes * 1024L
                }.toMap()
        val totalBytes = values["MemTotal"]
        val availableBytes = values["MemAvailable"]
        if (totalBytes == null && availableBytes == null) {
            null
        } else {
            ProcessMemorySnapshot(
                totalBytes = totalBytes,
                availableBytes = availableBytes,
            )
        }
    }
}

private suspend fun loadProcessListSnapshot(): ProcessListSnapshot {
    val connection =
        SessionManagementAdbConnection.current()
            ?: return ProcessListSnapshot.loading().copy(
                isLoading = false,
                errorMessage = ManagementTexts.Processes.NO_ADB_CONNECTION_AVAILABLE_SO_PROCESS_LIST_CAN.get(),
            )

    return runCatching {
        val output =
            connection
                .executeShell(
                    "ps -A -o PID,RSS,NAME 2>/dev/null || ps -A",
                    retryOnFailure = false,
                ).getOrThrow()

        val entries =
            withContext(Dispatchers.Default) {
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
            }

        ProcessListSnapshot(
            isLoading = false,
            entries = entries,
            errorMessage = if (entries.isEmpty()) ManagementTexts.Processes.NO_RUNNING_APP_PROCESSES_WERE_FOUND.get() else null,
        )
    }.getOrElse { error ->
        ProcessListSnapshot.loading().copy(
            isLoading = false,
            errorMessage = error.message ?: ManagementTexts.Processes.COULDN_T_LOAD_PROCESS_LIST.get(),
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
