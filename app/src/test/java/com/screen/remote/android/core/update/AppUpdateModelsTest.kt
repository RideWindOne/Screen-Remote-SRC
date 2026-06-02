package com.screen.remote.android.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateModelsTest {
    @Test
    fun stableChannelIgnoresPrereleases() {
        val releases =
            listOf(
                GitHubReleaseInfo("v1.1.0-beta.1", "beta", "beta-url", prerelease = true, draft = false),
                GitHubReleaseInfo("v1.0.1", "stable", "stable-url", prerelease = false, draft = false),
            )

        assertEquals(
            "v1.0.1",
            selectLatestRelease(releases, "v1.0.0", UpdateChannel.STABLE)?.tagName,
        )
    }

    @Test
    fun prereleaseChannelAcceptsPrereleases() {
        val releases =
            listOf(
                GitHubReleaseInfo("v1.1.0-beta.1", "beta", "beta-url", prerelease = true, draft = false),
                GitHubReleaseInfo("v1.0.1", "stable", "stable-url", prerelease = false, draft = false),
            )

        assertEquals(
            "v1.1.0-beta.1",
            selectLatestRelease(releases, "v1.0.0", UpdateChannel.PRERELEASE)?.tagName,
        )
    }

    @Test
    fun draftsAndOlderVersionsAreIgnored() {
        val releases =
            listOf(
                GitHubReleaseInfo("v2.0.0", "draft", "draft-url", prerelease = false, draft = true),
                GitHubReleaseInfo("v0.9.9", "old", "old-url", prerelease = false, draft = false),
            )

        assertNull(selectLatestRelease(releases, "v1.0.0", UpdateChannel.PRERELEASE))
    }
}
