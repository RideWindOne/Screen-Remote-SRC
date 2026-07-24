package com.screen.remote.android.feature.settings.ui

import com.screen.remote.android.core.domain.model.AppSettings
import com.screen.remote.android.core.domain.model.CustomShellCommand
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomCommandsBackupTest {
    @Test
    fun customCommandsAndReplacementModeSurviveBackupRoundTrip() {
        val commands =
            listOf(
                CustomShellCommand("first", "Battery", "dumpsys battery"),
                CustomShellCommand("second", "Display", "wm size && wm density"),
            )
        val backup =
            BackupData(
                version = 5,
                sessions = emptyList(),
                groups = emptyList(),
                settings =
                    AppSettings(
                        customShellCommands = commands,
                        replaceDefaultShellCommands = true,
                    ),
                adbKeys = AdbKeysData(),
            )

        val encoded = Json.encodeToString(BackupData.serializer(), backup)
        val restored = Json.decodeFromString(BackupData.serializer(), encoded)

        assertEquals(commands, restored.settings.customShellCommands)
        assertTrue(restored.settings.replaceDefaultShellCommands)
    }
}
