package com.screen.remote.android.feature.session.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionOnboardingStateTest {
    @Test
    fun missingVersionShowsIntroduction() {
        assertEquals(
            SessionOnboardingState.INTRODUCTION,
            resolveSessionOnboardingState(lastSeenVersion = null, currentVersion = "4.4.3.6"),
        )
    }

    @Test
    fun differentVersionShowsRecentUpdates() {
        assertEquals(
            SessionOnboardingState.RECENT_UPDATES,
            resolveSessionOnboardingState(lastSeenVersion = "4.4.3.5", currentVersion = "4.4.3.6"),
        )
    }

    @Test
    fun legacyVersionWithPrefixShowsRecentUpdates() {
        assertEquals(
            SessionOnboardingState.RECENT_UPDATES,
            resolveSessionOnboardingState(lastSeenVersion = "v4.4.3", currentVersion = "4.4.3.6"),
        )
    }

    @Test
    fun currentVersionStaysHidden() {
        assertEquals(
            SessionOnboardingState.HIDDEN,
            resolveSessionOnboardingState(lastSeenVersion = "4.4.3.6", currentVersion = "4.4.3.6"),
        )
    }
}
