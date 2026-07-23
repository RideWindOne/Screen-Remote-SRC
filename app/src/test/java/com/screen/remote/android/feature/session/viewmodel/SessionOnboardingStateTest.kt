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
    fun oldBuildWithoutContentStaysHidden() {
        assertEquals(
            SessionOnboardingState.HIDDEN,
            resolveSessionOnboardingState(lastSeenVersion = "4.4.3.3", currentVersion = "4.4.3.6"),
        )
    }

    @Test
    fun buildWithoutNewContentStaysHidden() {
        assertEquals(
            SessionOnboardingState.HIDDEN,
            resolveSessionOnboardingState(lastSeenVersion = "4.4.3.6", currentVersion = "4.4.3.7"),
        )
    }

    @Test
    fun versionEightShowsUrlSchemeAiMcpContent() {
        assertEquals(
            SessionOnboardingState.RECENT_UPDATES,
            resolveSessionOnboardingState(lastSeenVersion = "4.4.3.6", currentVersion = "4.4.3.8"),
        )
    }

    @Test
    fun laterBuildWithoutNewContentStaysHidden() {
        assertEquals(
            SessionOnboardingState.HIDDEN,
            resolveSessionOnboardingState(lastSeenVersion = "4.4.3.8", currentVersion = "4.4.3.9"),
        )
    }
}
