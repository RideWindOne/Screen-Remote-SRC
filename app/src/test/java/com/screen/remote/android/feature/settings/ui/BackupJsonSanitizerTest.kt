package com.screen.remote.android.feature.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupJsonSanitizerTest {
    @Test
    fun extractsCompleteBackupAndDropsStaleFileTail() {
        val backup = """{"version":2,"value":"escaped \\\" } text","nested":{"items":[]}}"""
        val staleTail = """: "", "enableAudio": true }"""

        assertEquals(backup, extractFirstJsonObject("\uFEFF  $backup$staleTail"))
    }

    @Test
    fun rejectsIncompleteBackup() {
        assertThrows(IllegalArgumentException::class.java) {
            extractFirstJsonObject("""{"version":2,"sessions":[""")
        }
    }

    @Test
    fun dropsOnlyMalformedRuntimeCodecCaches() {
        val sanitized =
            sanitizeBackupRuntimeCaches(
                """{"version":2,"sessions":[{"id":"one","name":"Phone","remoteVideoEncoders":["legacy.encoder"],"remoteAudioEncoders":[{"name":"valid"}],"selectedVideoDecoder":42,"host":"phone"}]}""",
            )

        assertFalse(sanitized.contains("remoteVideoEncoders"))
        assertFalse(sanitized.contains("selectedVideoDecoder"))
        assertFalse(sanitized.contains("remoteAudioEncoders"))
        assertTrue(sanitized.contains("\"host\":\"phone\""))
    }

}
