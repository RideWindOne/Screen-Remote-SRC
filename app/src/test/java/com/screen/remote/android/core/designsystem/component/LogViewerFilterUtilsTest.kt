package com.screen.remote.android.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class LogViewerFilterUtilsTest {
    @Test
    fun extractLogTags_supportsCurrentLogFormat() {
        val content =
            """
            22:11:35 I/LOG: init
            22:11:37 D/SCLI: session start
            22:11:39 E/SSVR: server crash
            22:11:40 D/SCLI: retry
            """.trimIndent()

        assertEquals(listOf("LOG", "SCLI", "SSVR"), extractLogTags(content))
    }

    @Test
    fun filterLogContent_filtersBySelectedTagsWithLevelPrefix() {
        val content =
            """
            22:11:35 I/LOG: init
            22:11:37 D/SCLI: session start
            22:11:39 E/SSVR: server crash
            """.trimIndent()

        assertEquals(
            "22:11:37 D/SCLI: session start",
            filterLogContent(content, query = "", tags = setOf("SCLI")),
        )
    }

    @Test
    fun extractLogTags_supportsLegacyFormatWithoutLevelPrefix() {
        val content =
            """
            22:11:35 LOG: init
            22:11:37 SCLI: session start
            """.trimIndent()

        assertEquals(listOf("LOG", "SCLI"), extractLogTags(content))
    }
}
