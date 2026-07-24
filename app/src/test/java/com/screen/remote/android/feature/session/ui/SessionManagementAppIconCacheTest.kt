package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagementAppIconCacheTest {
    private val metadata =
        AppIconCacheMetadata(
            versionCode = 42L,
            versionName = "4.2",
            lastUpdateTime = 123456789L,
        )

    @Test
    fun `matching version metadata reuses a global icon`() {
        assertTrue(metadata.matches(app(versionCode = 42L, lastUpdateTime = 123456789L)))
    }

    @Test
    fun `version or update time changes invalidate a global icon`() {
        assertFalse(metadata.matches(app(versionCode = 43L, lastUpdateTime = 123456789L)))
        assertFalse(metadata.matches(app(versionCode = 42L, versionName = "4.2.1", lastUpdateTime = 123456789L)))
        assertFalse(metadata.matches(app(versionCode = 42L, lastUpdateTime = 987654321L)))
    }

    @Test
    fun `missing update time never validates a global icon`() {
        assertFalse(metadata.matches(app(versionCode = 42L, lastUpdateTime = 0L)))
    }

    private fun app(
        versionCode: Long,
        versionName: String = "4.2",
        lastUpdateTime: Long,
    ) = AppInventoryEntry(
        packageName = "com.example.app",
        appTitle = "Example",
        isSystemApp = false,
        apkPath = "/data/app/com.example.app/base.apk",
        isEnabled = true,
        versionCode = versionCode,
        versionName = versionName,
        lastUpdateTime = lastUpdateTime,
    )
}
