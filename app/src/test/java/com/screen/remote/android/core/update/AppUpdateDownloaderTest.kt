package com.screen.remote.android.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdateDownloaderTest {
    @Test
    fun updateDownloadUrlUsesGitHubProxy() {
        val original = "https://github.com/XRSec/Screen-Remote/releases/download/1.0.0/update.apk"

        assertEquals(
            "https://v4.gh-proxy.org/$original",
            proxiedUpdateDownloadUrl(original),
        )
    }

    @Test
    fun updateDownloadUrlDoesNotDuplicateGitHubProxy() {
        val proxied =
            "https://v4.gh-proxy.org/https://github.com/XRSec/Screen-Remote/releases/download/1.0.0/update.apk"

        assertEquals(proxied, proxiedUpdateDownloadUrl(proxied))
    }
}
