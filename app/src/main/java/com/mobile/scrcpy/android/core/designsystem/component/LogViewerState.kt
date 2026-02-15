package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
