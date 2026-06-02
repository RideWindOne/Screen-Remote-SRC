package com.screen.remote.android.feature.session.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionOnboardingStateTest {
    @Test
    fun missingVersionShowsIntroduction() {
        assertEquals(
            SessionOnboardingState.INTRODUCTION,
            resolveSessionOnboardingState(lastSeenVersion = null, currentVersion = "v4.4.3"),
        )
    }

    @Test
    fun differentVersionShowsRecentUpdates() {
        assertEquals(
            SessionOnboardingState.RECENT_UPDATES,
            resolveSessionOnboardingState(lastSeenVersion = "v4.4.2", currentVersion = "v4.4.3"),
        )
    }

    @Test
    fun currentVersionStaysHidden() {
        assertEquals(
            SessionOnboardingState.HIDDEN,
            resolveSessionOnboardingState(lastSeenVersion = "v4.4.3", currentVersion = "v4.4.3"),
        )
    }
}
