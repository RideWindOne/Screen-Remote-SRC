package com.screen.remote.android.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AppUpdateModelsTest {
    @Test
    fun apkAssetMatchesDeviceAbiBeforeUniversalFallback() {
        val arm64 = GitHubReleaseAsset("Screen.Remote-arm64-v8a-4.4.3_3.apk", "arm64-url", 10, null)
        val x86 = GitHubReleaseAsset("Screen.Remote-x86_64-4.4.3_3.apk", "x86-url", 10, null)
        val universal = GitHubReleaseAsset("Screen.Remote-universal-4.4.3_3.apk", "universal-url", 20, null)
        val release = GitHubReleaseInfo("4.4.3.3", "4.4.3.3", "url", false, false, listOf(universal, x86, arm64))

        assertEquals(arm64, selectApkAsset(release, listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals(x86, selectApkAsset(release, listOf("x86_64", "x86")))
        assertEquals(universal, selectApkAsset(release, listOf("riscv64")))
    }

    @Test
    fun fourPartReleaseVersionIsNewerThanThreePartAppVersion() {
        val release =
            GitHubReleaseInfo(
                "4.4.3.3",
                "4.4.3.3",
                "release-url",
                prerelease = false,
                draft = false,
            )

        assertEquals(
            "4.4.3.3",
            selectLatestRelease(listOf(release), "4.4.3", UpdateChannel.STABLE)?.tagName,
        )
    }

    @Test
    fun automaticCheckIsDueOnlyOnAnotherLocalCalendarDay() {
        val timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        val previousNight =
            Calendar.getInstance(timeZone).apply {
                clear()
                set(2026, Calendar.JULY, 20, 23, 50)
            }.timeInMillis
        val todayMorning =
            Calendar.getInstance(timeZone).apply {
                clear()
                set(2026, Calendar.JULY, 21, 0, 10)
            }.timeInMillis
        val todayEvening =
            Calendar.getInstance(timeZone).apply {
                clear()
                set(2026, Calendar.JULY, 21, 23, 50)
            }.timeInMillis

        assertTrue(isAutomaticUpdateCheckDue(UpdateCheckCache(), todayMorning, timeZone))
        assertFalse(
            isAutomaticUpdateCheckDue(
                UpdateCheckCache(checkedAtEpochMillis = todayMorning),
                todayEvening,
                timeZone,
            ),
        )
        assertTrue(
            isAutomaticUpdateCheckDue(
                UpdateCheckCache(checkedAtEpochMillis = previousNight),
                todayMorning,
                timeZone,
            ),
        )
    }

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
