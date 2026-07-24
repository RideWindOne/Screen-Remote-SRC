package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellCommandPresetsTest {
    @Test
    fun customCommandsAppendToDefaultsByDefault() {
        assertEquals(
            listOf("default-1", "default-2", "custom-1"),
            combineShellCommandPresets(
                defaultCommands = listOf("default-1", "default-2"),
                customCommands = listOf("custom-1"),
                replaceDefaultCommands = false,
            ),
        )
    }

    @Test
    fun customCommandsReplaceDefaultsWhenEnabled() {
        assertEquals(
            listOf("custom-1", "custom-2"),
            combineShellCommandPresets(
                defaultCommands = listOf("default-1"),
                customCommands = listOf("custom-1", "custom-2"),
                replaceDefaultCommands = true,
            ),
        )
    }
}
