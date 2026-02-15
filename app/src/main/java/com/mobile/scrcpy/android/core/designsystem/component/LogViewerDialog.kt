package com.mobile.scrcpy.android.core.designsystem.component

import android.annotation.SuppressLint
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.mobile.scrcpy.android.core.i18n.LogTexts
import java.io.File
import java.text.SimpleDateFormat
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
            displayContent =
                buildLogDisplayContent(
                    logContent = state.logContent,
                    searchQuery = state.searchQuery,
                    selectedTags = state.selectedTags,
                ),
        )
    }

    LogViewerDialogs(
        state = state,
        onDismiss = onDismiss,
        onClearAndRetry = actions::clearAndRetry,
    )
}
