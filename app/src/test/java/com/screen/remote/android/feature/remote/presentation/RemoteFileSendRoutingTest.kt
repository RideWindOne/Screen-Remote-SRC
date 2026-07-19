package com.screen.remote.android.feature.remote.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFileSendRoutingTest {
    @Test
    fun `apk extension routes to install regardless of case`() {
        assertTrue(isApkFile("Screen-Remote.APK", "application/octet-stream"))
    }

    @Test
    fun `apk mime routes to install when provider hides extension`() {
        assertTrue(isApkFile("download", "application/vnd.android.package-archive"))
    }

    @Test
    fun `ordinary files remain uploads`() {
        assertFalse(isApkFile("photo.jpg", "image/jpeg"))
        assertFalse(isApkFile("archive.zip", "application/zip"))
    }
}
