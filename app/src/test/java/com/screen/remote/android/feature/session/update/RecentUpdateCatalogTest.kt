package com.screen.remote.android.feature.session.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentUpdateCatalogTest {
    @Test
    fun versionBelowEightHasNoRecentUpdateContent() {
        assertNull(latestRecentUpdateContent("4.4.3.7"))
    }

    @Test
    fun versionEightUsesReleaseContent() {
        assertEquals(
            RecentUpdateContent.RELEASE_4_4_3_8,
            latestRecentUpdateContent("4.4.3.8"),
        )
    }

    @Test
    fun laterBuildDoesNotRepeatVersionEightContent() {
        assertFalse(
            hasUnseenRecentUpdateContent(
                lastSeenVersion = "4.4.3.8",
                currentVersion = "4.4.3.9",
            ),
        )
    }

    @Test
    fun versionEightContentIsNewAfterOlderBuild() {
        assertTrue(
            hasUnseenRecentUpdateContent(
                lastSeenVersion = "4.4.3.5",
                currentVersion = "4.4.3.8",
            ),
        )
    }
}
