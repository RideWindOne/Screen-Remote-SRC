package com.screen.remote.android.feature.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomCommandsTest {
    @Test
    fun moveListItemReordersInBothDirections() {
        assertEquals(listOf("b", "c", "a"), moveListItem(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), moveListItem(listOf("a", "b", "c"), 2, 0))
    }

    @Test
    fun moveListItemIgnoresInvalidIndexes() {
        val items = listOf("a", "b")
        assertEquals(items, moveListItem(items, -1, 1))
        assertEquals(items, moveListItem(items, 0, 2))
    }
}
