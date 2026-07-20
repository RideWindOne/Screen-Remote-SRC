package com.screen.remote.android.feature.session.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDialogGameVideoSettingsTest {
    @Test
    fun enablingGameModeRaisesValuesBelowGameMinimums() {
        val state = SessionDialogState().apply {
            maxSize = "640"
            videoBitrate = "500K"
            maxFps = "30"
        }

        state.updateGameMode(true)

        assertEquals("720", state.maxSize)
        assertEquals("8M", state.videoBitrate)
        assertEquals("60", state.maxFps)
    }

    @Test
    fun enablingGameModeUsesCodeDefaultsForEmptyValues() {
        val state = SessionDialogState()

        state.updateGameMode(true)

        assertEquals("720", state.maxSize)
        assertEquals("8M", state.videoBitrate)
        assertEquals("60", state.maxFps)
    }

    @Test
    fun enablingGameModeDisablesFullScreenMode() {
        val state = SessionDialogState().apply { updateConfig { copy(useFullScreen = true) } }

        state.updateGameMode(true)

        assertEquals(false, state.config.useFullScreen)
        assertEquals(false, state.toSessionData().config.useFullScreen)
    }

    @Test
    fun savedGameSessionCannotKeepFullScreenMode() {
        val state = SessionDialogState().apply { updateConfig { copy(gameMode = true, useFullScreen = true) } }

        assertEquals(false, state.toSessionData().config.useFullScreen)
    }
}
