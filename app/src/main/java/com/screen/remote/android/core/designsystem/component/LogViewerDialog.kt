package com.screen.remote.android.core.designsystem.component

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.screen.remote.android.core.common.AppColors
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.common.IosDesignTokens
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.LogTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerDialog(
    file: File,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberLogViewerState()
    val dateFormat = remember { SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault()) }
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val loadingText = CommonTexts.STATUS_CONNECTING.get()
    val noResultsText = LogTexts.LOG_NO_RESULTS.get()
    val displayLines by
        produceState(
            initialValue = listOf(loadingText),
            key1 = state.logContent,
            key2 = state.searchQuery,
            key3 = state.selectedTags,
        ) {
            value =
                withContext(Dispatchers.Default) {
                    buildLogDisplayLines(
                        logContent = state.logContent,
                        searchQuery = state.searchQuery,
                        selectedTags = state.selectedTags,
                        loadingText = loadingText,
                        noResultsText = noResultsText,
                    )
                }
        }
    val actions =
        rememberLogViewerActions(
            context = context,
            file = file,
            scope = scope,
            state = state,
            onDismiss = onDismiss,
        )

    LaunchedEffect(file) {
        actions.loadLogContent()
    }

    DialogPage(
        title = LogTexts.LOG_DETAIL_TITLE.get(),
        onDismiss = onDismiss,
        showBackButton = true,
        enableScroll = true,
        trailingContent = {
            LogViewerToolbar(
                isSearchActive = state.isSearchActive,
                selectedTags = state.selectedTags,
                onToggleSearch = state::toggleSearch,
                onOpenFilter = state::openFilterDialog,
                onShare = actions::shareLogFile,
                onRefresh = actions::loadLogContent,
            )
        },
    ) {
        LogViewerContent(
            file = file,
            dateFormat = dateFormat,
            isSearchActive = state.isSearchActive,
            searchQuery = state.searchQuery,
            onSearchQueryChange = state::updateSearchQuery,
            selectedTags = state.selectedTags,
            onRemoveTag = state::removeTag,
            isDarkTheme = isDarkTheme,
            displayLines = displayLines,
        )
    }

    if (state.showFileTooLargeDialog) {
        IOSAlertDialog(
            onDismissRequest = {
                state.dismissFileTooLargeDialog()
                onDismiss()
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(LogTexts.LOG_FILE_TOO_LARGE_TITLE.get()) },
            text = { Text(LogTexts.LOG_FILE_TOO_LARGE_MESSAGE.get()) },
            confirmButton = {
                TextButton(onClick = actions::clearAndRetry) {
                    Text(LogTexts.LOG_CLEAR_AND_RETRY.get())
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        state.dismissFileTooLargeDialog()
                        onDismiss()
                    },
                ) {
                    Text(CommonTexts.BUTTON_CANCEL.get())
                }
            },
        )
    }

    if (state.showFilterDialog) {
        TagFilterDialog(
            availableTags = state.availableTags,
            selectedTags = state.selectedTags,
            onTagsSelected = state::updateSelectedTags,
            onDismiss = state::dismissFilterDialog,
        )
    }
}

@Composable
internal fun rememberLogViewerActions(
    context: Context,
    file: File,
    scope: CoroutineScope,
    state: LogViewerState,
    onDismiss: () -> Unit,
): LogViewerActions =
    remember(context, file, scope, state, onDismiss) {
        LogViewerActions(
            context = context,
            file = file,
            scope = scope,
            state = state,
            onDismiss = onDismiss,
        )
    }

internal class LogViewerActions(
    private val context: Context,
    private val file: File,
    private val scope: CoroutineScope,
    private val state: LogViewerState,
    private val onDismiss: () -> Unit,
) {
    fun loadLogContent() {
        scope.launch {
            if (file.length() > MAX_FILE_SIZE) {
                state.showFileTooLargeDialog()
                return@launch
            }

            val content =
                withContext(Dispatchers.IO) {
                    LogManager.readLogFile(file)
                }
            state.updateLogContent(content)
            state.updateAvailableTags(extractLogTags(content))
        }
    }

    fun shareLogFile() {
        try {
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )

            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Scrcpy Log - ${file.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            context.startActivity(Intent.createChooser(shareIntent, LogTexts.LOG_SHARE_BUTTON.get()))
        } catch (e: Exception) {
            LogManager.e("LogViewerDialog", "Failed to share log file: ${e.message}")
        }
    }

    fun clearAndRetry() {
        state.dismissFileTooLargeDialog()
        scope.launch {
            withContext(Dispatchers.IO) {
                LogManager.clearAllLogs()
            }
            onDismiss()
        }
    }

    companion object {
        private const val MAX_FILE_SIZE = 1024 * 1024L
    }
}

internal class LogViewerState {
    var showFileTooLargeDialog by mutableStateOf(false)
        private set
    var logContent by mutableStateOf("")
        private set
    var searchQuery by mutableStateOf("")
        private set
    var showFilterDialog by mutableStateOf(false)
        private set
    var availableTags by mutableStateOf<List<String>>(emptyList())
        private set
    var selectedTags by mutableStateOf<Set<String>>(emptySet())
        private set
    var isSearchActive by mutableStateOf(false)
        private set

    fun showFileTooLargeDialog() {
        showFileTooLargeDialog = true
    }

    fun dismissFileTooLargeDialog() {
        showFileTooLargeDialog = false
    }

    fun updateLogContent(content: String) {
        logContent = content
    }

    fun updateAvailableTags(tags: List<String>) {
        availableTags = tags
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun openFilterDialog() {
        showFilterDialog = true
    }

    fun dismissFilterDialog() {
        showFilterDialog = false
    }

    fun updateSelectedTags(tags: Set<String>) {
        selectedTags = tags
    }

    fun removeTag(tag: String) {
        selectedTags = selectedTags - tag
    }

    fun toggleSearch() {
        isSearchActive = !isSearchActive
    }
}

@Composable
internal fun rememberLogViewerState(): LogViewerState = remember { LogViewerState() }

@Composable
internal fun LogViewerToolbar(
    isSearchActive: Boolean,
    selectedTags: Set<String>,
    onToggleSearch: () -> Unit,
    onOpenFilter: () -> Unit,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                contentDescription = LogTexts.LOG_SEARCH_PLACEHOLDER.get(),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        BadgedBox(
            badge = {
                if (selectedTags.isNotEmpty()) {
                    Badge {
                        Text(selectedTags.size.toString())
                    }
                }
            },
        ) {
            IconButton(onClick = onOpenFilter) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = LogTexts.LOG_FILTER_BY_TAG.get(),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        IconButton(onClick = onShare) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = LogTexts.LOG_SHARE_BUTTON.get(),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = CommonTexts.BUTTON_DONE.get(),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun LogViewerContent(
    file: File,
    dateFormat: SimpleDateFormat,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTags: Set<String>,
    onRemoveTag: (String) -> Unit,
    isDarkTheme: Boolean,
    displayLines: List<String>,
) {
    if (isSearchActive) {
        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IosDesignTokens.segmentedControlHeight)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(IosDesignTokens.searchFieldCornerRadius))
                    .padding(horizontal = IosDesignTokens.compactSpacing),
            textStyle =
                TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            singleLine = true,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = LogTexts.LOG_SEARCH_PLACEHOLDER.get(),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(IosDesignTokens.trailingIconSize),
                            )
                        }
                    }
                }
            },
        )
    }

    if (selectedTags.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            selectedTags.forEach { tag ->
                FilterChip(
                    selected = true,
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor =
                                if (isDarkTheme) AppColors.darkIOSSelectedBackground else AppColors.iOSSelectedBackground,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    onClick = { onRemoveTag(tag) },
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(IosDesignTokens.trailingIconSize),
                        )
                    },
                )
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LogMetadataRow(
                label = LogTexts.LOG_FILE_LABEL.get() + "：",
                value = file.name,
            )
            AppDivider()
            LogMetadataRow(
                label = LogTexts.LOG_SIZE_LABEL.get() + "：",
                value = formatFileSize(file.length()),
            )
            AppDivider()
            LogMetadataRow(
                label = LogTexts.LOG_MODIFIED_LABEL.get() + "：",
                value = dateFormat.format(Date(file.lastModified())),
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Card(
        modifier = Modifier.fillMaxWidth().height(400.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
        ) {
            items(displayLines) { line ->
                SelectionContainer {
                    Text(
                        text = rememberColoredLogLine(line),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberColoredLogLine(line: String): AnnotatedString {
    val colorScheme = MaterialTheme.colorScheme
    val textColor = colorScheme.onSurface
    val timestampColor = colorScheme.onSurfaceVariant
    val errorColor = colorScheme.error
    val warningColor = colorScheme.tertiary
    val infoColor = colorScheme.primary
    val debugColor = colorScheme.secondary

    return remember(
        line,
        textColor,
        timestampColor,
        errorColor,
        warningColor,
        infoColor,
        debugColor,
    ) {
        buildAnnotatedString {
            append(line)
            val parts = parseLogLineStyleParts(line) ?: return@buildAnnotatedString
            val levelColor =
                if (parts.level == null) {
                    debugColor
                } else {
                    when (parts.level.uppercase().firstOrNull()) {
                        'E', 'F' -> errorColor
                        'W' -> warningColor
                        'I' -> infoColor
                        'D' -> debugColor
                        else -> timestampColor
                    }
                }

            addStyle(
                SpanStyle(color = timestampColor),
                start = parts.timestampRange.first,
                end = parts.timestampRange.last + 1,
            )
            addStyle(
                SpanStyle(color = levelColor),
                start = parts.prefixRange.first,
                end = parts.prefixRange.last + 1,
            )
            parts.levelRange?.let { levelRange ->
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    start = levelRange.first,
                    end = levelRange.last + 1,
                )
            }
            if (parts.messageStart < line.length) {
                addStyle(
                    SpanStyle(color = textColor),
                    start = parts.messageStart,
                    end = line.length,
                )
            }
        }
    }
}

@Composable
private fun LogMetadataRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val LogLineTagRegex = Regex("""^\d{2}:\d{2}:\d{2}\s+(?:[A-Z]+/)?([A-Za-z0-9_.-]+):""")
private val LogLineStyleRegex =
    Regex("""^(\d{2}:\d{2}:\d{2})(\s+)((?:([A-Za-z]+)/)?([A-Za-z0-9_.-]+):\s?)""")

internal data class LogLineStyleParts(
    val timestampRange: IntRange,
    val prefixRange: IntRange,
    val levelRange: IntRange?,
    val level: String?,
    val messageStart: Int,
)

internal fun parseLogLineStyleParts(line: String): LogLineStyleParts? {
    val match = LogLineStyleRegex.find(line) ?: return null
    val timestamp = match.groups[1] ?: return null
    val prefix = match.groups[3] ?: return null
    val level = match.groups[4]
    return LogLineStyleParts(
        timestampRange = timestamp.range,
        prefixRange = prefix.range,
        levelRange = level?.range,
        level = level?.value,
        messageStart = match.range.last + 1,
    )
}

internal fun extractLogTags(content: String): List<String> =
    content
        .lineSequence()
        .mapNotNull(::extractTagFromLogLine)
        .distinct()
        .sorted()
        .toList()

internal fun filterLogContent(
    content: String,
    query: String,
    tags: Set<String>,
): String {
    return filterLogLines(content, query, tags).joinToString("\n")
}

internal fun filterLogLines(
    content: String,
    query: String,
    tags: Set<String>,
): List<String> =
    content
        .lineSequence()
        .filter { line -> tags.isEmpty() || extractTagFromLogLine(line) in tags }
        .filter { line -> query.isBlank() || line.contains(query, ignoreCase = true) }
        .toList()

internal fun buildLogDisplayLines(
    logContent: String,
    searchQuery: String,
    selectedTags: Set<String>,
    loadingText: String,
    noResultsText: String,
): List<String> {
    if (logContent.isEmpty()) {
        return listOf(loadingText)
    }

    val filtered = filterLogLines(logContent, searchQuery, selectedTags)
    return if (filtered.isEmpty() && (searchQuery.isNotBlank() || selectedTags.isNotEmpty())) {
        listOf(noResultsText)
    } else {
        filtered
    }
}

internal fun buildLogDisplayContent(
    logContent: String,
    searchQuery: String,
    selectedTags: Set<String>,
): String {
    if (logContent.isEmpty()) {
        return CommonTexts.STATUS_CONNECTING.get()
    }

    val filtered = filterLogContent(logContent, searchQuery, selectedTags)
    return if (filtered.isEmpty() && (searchQuery.isNotBlank() || selectedTags.isNotEmpty())) {
        LogTexts.LOG_NO_RESULTS.get()
    } else {
        filtered
    }
}

private fun extractTagFromLogLine(line: String): String? =
    LogLineTagRegex.find(line)?.groupValues?.getOrNull(1)
