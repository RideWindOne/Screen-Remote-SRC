package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun rememberCompactGroupSelectorState(): CompactGroupSelectorState =
    remember { CompactGroupSelectorState() }

@Stable
internal class CompactGroupSelectorState {
    var showDropdown by mutableStateOf(false)
    var clickedLevel by mutableStateOf<CompactGroupSelectorLevel?>(null)

    fun open(level: CompactGroupSelectorLevel) {
        clickedLevel = level
        showDropdown = true
    }

    fun close() {
        showDropdown = false
        clickedLevel = null
    }

    fun isExpanded(level: CompactGroupSelectorLevel): Boolean = showDropdown && clickedLevel == level
}

internal enum class CompactGroupSelectorLevel {
    Parent,
    Current,
}
