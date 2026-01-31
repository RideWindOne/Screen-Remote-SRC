package com.mobile.scrcpy.android.core.designsystem.component

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.LogTexts
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
    val dateFormat = remember { SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault()) }

    val maxFileSize = 1024 * 1024L
    var showFileTooLargeDialog by remember { mutableStateOf(false) }

    var logContent by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var availableTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSearchActive by remember { mutableStateOf(false) }
    val isDarkTheme = isSystemInDarkTheme()

    fun extractTags(content: String): List<String> {
        val tagRegex = Regex("""\d{2}:\d{2}:\d{2}\s+([A-Za-z0-9_]+):""")
        return tagRegex
            .findAll(content)
            .map { it.groupValues[1] }
            .distinct()
            .sorted()
            .toList()
    }

    fun filterLogContent(
        content: String,
        query: String,
        tags: Set<String>,
    ): String {
        var lines = content.lines()

        if (tags.isNotEmpty()) {
            val tagPattern = tags.joinToString("|") { Regex.escape(it) }
            val tagRegex = Regex("""\d{2}:\d{2}:\d{2}\s+($tagPattern):""")
            lines = lines.filter { line -> tagRegex.containsMatchIn(line) }
        }

        if (query.isNotBlank()) {
            lines =
                lines.filter { line ->
                    line.contains(query, ignoreCase = true)
                }
        }

        return lines.joinToString("\n")
    }

    fun loadLogContent() {
        scope.launch {
            if (file.length() > maxFileSize) {
                showFileTooLargeDialog = true
                return@launch
            }

            val content =
                withContext(Dispatchers.IO) {
                    LogManager.readLogFile(file)
                }
            logContent = content
            availableTags = extractTags(content)
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
            e.printStackTrace()
        }
    }

    LaunchedEffect(file) {
        loadLogContent()
    }

    val displayContent =
        remember(logContent, searchQuery, selectedTags) {
            if (logContent.isEmpty()) {
                CommonTexts.STATUS_CONNECTING.get()
            } else {
                val filtered = filterLogContent(logContent, searchQuery, selectedTags)
                if (filtered.isEmpty() && (searchQuery.isNotBlank() || selectedTags.isNotEmpty())) {
                    LogTexts.LOG_NO_RESULTS.get()
                } else {
                    filtered
                }
            }
        }

    if (showFileTooLargeDialog) {
        AlertDialog(
            onDismissRequest = { showFileTooLargeDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(LogTexts.LOG_FILE_TOO_LARGE_TITLE.get()) },
            text = { Text(LogTexts.LOG_FILE_TOO_LARGE_MESSAGE.get()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFileTooLargeDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                LogManager.clearAllLogs()
                            }
                            onDismiss()
                        }
                    },
                ) {
                    Text(LogTexts.LOG_CLEAR_AND_RETRY.get())
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFileTooLargeDialog = false
                    onDismiss()
                }) {
                    Text(CommonTexts.BUTTON_CANCEL.get())
                }
            },
        )
    }

    if (showFilterDialog) {
        TagFilterDialog(
            availableTags = availableTags,
            selectedTags = selectedTags,
            onTagsSelected = { selectedTags = it },
            onDismiss = { showFilterDialog = false },
        )
    }

    DialogPage(
        title = LogTexts.LOG_DETAIL_TITLE.get(),
        onDismiss = onDismiss,
        showBackButton = true,
        enableScroll = true,
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { isSearchActive = !isSearchActive }) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = LogTexts.LOG_SEARCH_PLACEHOLDER.get() as String?,
                        tint = Color(0xFF007AFF),
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
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = LogTexts.LOG_FILTER_BY_TAG.get(),
                            tint =
                                if (selectedTags.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    AppColors.iOSBlue
                                },
                        )
                    }
                }

                IconButton(onClick = { shareLogFile() }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = LogTexts.LOG_SHARE_BUTTON.get(),
                        tint = Color(0xFF007AFF),
                    )
                }

                IconButton(onClick = { loadLogContent() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = CommonTexts.BUTTON_DONE.get(),
                        tint = Color(0xFF007AFF),
                    )
                }
            }
        },
    ) {
        if (isSearchActive) {
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp),
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
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                },
            )
        }

        if (selectedTags.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectedTags.forEach { tag ->
                    FilterChip(
                        selected = true,
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor =
                                    if (isDarkTheme) {
                                        AppColors.darkIOSSelectedBackground
                                    } else {
                                        AppColors.iOSSelectedBackground
                                    },
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        onClick = { selectedTags = selectedTags - tag },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        text = LogTexts.LOG_FILE_LABEL.get() + "：",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppDivider()
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
                        text = LogTexts.LOG_SIZE_LABEL.get() + "：",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = formatFileSize(file.length()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppDivider()
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
                        text = LogTexts.LOG_MODIFIED_LABEL.get() + "：",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = dateFormat.format(Date(file.lastModified())),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(400.dp),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            SelectionContainer {
                Text(
                    text = displayContent,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
