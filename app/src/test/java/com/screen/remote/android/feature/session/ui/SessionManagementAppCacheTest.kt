package com.screen.remote.android.feature.session.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionManagementAppCacheTest {
    private val scopeKey = "app-cache-test"

    @After
    fun tearDown() {
        SessionManagementAppCache.releaseScope(scopeKey)
    }

    @Test
    fun `refresh keeps the current list until replacement data is ready`() {
        SessionManagementAppCache.selectScope(scopeKey)
        val cached = snapshot("com.example.cached")
        SessionManagementAppCache.updateFilteredSnapshot(AppListFilter.defaultSelection, cached)

        SessionManagementAppCache.markPipelineLoading(scopeKey)

        assertEquals(cached, SessionManagementAppCache.snapshot())
    }

    @Test
    fun `successful refresh atomically replaces current filters and invalidates other variants`() {
        SessionManagementAppCache.selectScope(scopeKey)
        val otherFilters = AppListFilter.defaultSelection + AppListFilter.ShowSystemApps
        SessionManagementAppCache.updateFilteredSnapshot(otherFilters, snapshot("com.example.system"))
        SessionManagementAppCache.updateFilteredSnapshot(AppListFilter.defaultSelection, snapshot("com.example.old"))
        val refreshed = snapshot("com.example.new")

        SessionManagementAppCache.replaceFilteredSnapshotAfterRefresh(AppListFilter.defaultSelection, refreshed)

        assertEquals(refreshed, SessionManagementAppCache.snapshot())
        assertEquals(refreshed, SessionManagementAppCache.filteredSnapshot(AppListFilter.defaultSelection))
        assertNull(SessionManagementAppCache.filteredSnapshot(otherFilters))
    }

    private fun snapshot(packageName: String) =
        AppInventorySnapshot(
            isLoading = false,
            apps =
                listOf(
                    AppInventoryEntry(
                        packageName = packageName,
                        appTitle = packageName,
                        isSystemApp = false,
                        apkPath = "/data/app/$packageName/base.apk",
                        isEnabled = true,
                    ),
                ),
            shizukuInstalled = false,
        )
}
