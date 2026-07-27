package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionManagementAppSizeTest {
    @Test
    fun `loading detail keeps inventory values as field fallbacks`() {
        val detail =
            AppDetailSnapshot.loading(
                AppInventoryEntry(
                    packageName = "com.example.app",
                    appTitle = "Example",
                    isSystemApp = false,
                    apkPath = "/data/app/com.example.app/base.apk",
                    isEnabled = false,
                    versionCode = 42L,
                    versionName = "4.2",
                    apkSizeBytes = 20L * 1024 * 1024,
                ),
            )

        assertEquals("20.00 M", detail.apkSize)
        assertEquals("4.2", detail.versionName)
        assertEquals("42", detail.versionCode)
        assertEquals("/data/app/com.example.app/base.apk", detail.apkPath)
        assertFalse(detail.isEnabled)
    }

    @Test
    fun `sizes below one gibibyte use megabytes`() {
        assertEquals("20.00 M", formatAppSize(20L * 1024 * 1024))
        assertEquals("1023.00 M", formatAppSize(1023L * 1024 * 1024))
    }

    @Test
    fun `sizes at or above one gibibyte use gigabytes`() {
        assertEquals("1.00 G", formatAppSize(1024L * 1024 * 1024))
        assertEquals("1.50 G", formatAppSize(1536L * 1024 * 1024))
    }

    @Test
    fun `app detail fields are parsed when sdk values share the version code line`() {
        val fields =
            parseAppDetailFields(
                """
                versionCode=123 minSdk=23 targetSdk=35
                versionName=2.4.1
                firstInstallTime=2026-07-01T10:20:30
                lastUpdateTime=2026-07-19T11:22:33
                """.trimIndent(),
            )

        assertEquals("23", fields["minSdk"])
        assertEquals("35", fields["targetSdk"])
        assertEquals("2.4.1", fields["versionName"])
    }
}
