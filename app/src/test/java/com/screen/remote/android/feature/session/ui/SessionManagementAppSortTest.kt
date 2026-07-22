package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionManagementAppSortTest {
    private val alpha = app(title = "Alpha", packageName = "com.example.alpha", enabled = true, size = 20L)
    private val beta = app(title = "Beta", packageName = "com.example.beta", enabled = false, size = 10L)

    @Test
    fun `selecting the same sort toggles direction`() {
        val descending = AppListSortSelection().select(AppListSort.Title)
        val ascending = descending.select(AppListSort.Title)

        assertEquals(false, descending.ascending)
        assertEquals(true, ascending.ascending)
    }

    @Test
    fun `selecting a different sort starts ascending`() {
        val selection =
            AppListSortSelection(AppListSort.Title, ascending = false)
                .select(AppListSort.Size)

        assertEquals(AppListSort.Size, selection.sort)
        assertEquals(true, selection.ascending)
    }

    @Test
    fun `ascending and descending title sorts use opposite directions`() {
        assertEquals(
            listOf(alpha, beta),
            listOf(beta, alpha).sortedWith(appListComparator(AppListSort.Title, ascending = true)),
        )
        assertEquals(
            listOf(beta, alpha),
            listOf(alpha, beta).sortedWith(appListComparator(AppListSort.Title, ascending = false)),
        )
    }

    @Test
    fun `size and enabled state respect ascending direction`() {
        assertEquals(
            listOf(beta, alpha),
            listOf(alpha, beta).sortedWith(appListComparator(AppListSort.Size, ascending = true)),
        )
        assertEquals(
            listOf(beta, alpha),
            listOf(alpha, beta).sortedWith(appListComparator(AppListSort.EnabledState, ascending = true)),
        )
    }

    private fun app(
        title: String,
        packageName: String,
        enabled: Boolean,
        size: Long,
    ) = AppInventoryEntry(
        packageName = packageName,
        appTitle = title,
        isSystemApp = false,
        apkPath = "/data/app/$packageName/base.apk",
        isEnabled = enabled,
        apkSizeBytes = size,
    )
}
