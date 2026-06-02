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

    @Test
    fun parseLogLineStyleParts_supportsCurrentLevelAndTagFormat() {
        val line = "22:11:39 E/SSVR: server crash"

        val parts = requireNotNull(parseLogLineStyleParts(line))

        assertEquals("22:11:39", line.substring(parts.timestampRange))
        assertEquals("E/SSVR: ", line.substring(parts.prefixRange))
        assertEquals("E", parts.level)
        assertEquals("server crash", line.substring(parts.messageStart))
    }

    @Test
    fun parseLogLineStyleParts_supportsLegacyTagOnlyFormat() {
        val line = "22:11:35 LOG: init"

        val parts = requireNotNull(parseLogLineStyleParts(line))

        assertEquals("LOG: ", line.substring(parts.prefixRange))
        assertEquals(null, parts.level)
        assertEquals("init", line.substring(parts.messageStart))
    }

    @Test
    fun buildLogDisplayLines_filtersWithoutJoiningTheWholeLogAgain() {
        val content =
            """
            22:11:35 I/LOG: init
            22:11:37 D/SCLI: session start
            22:11:39 E/SSVR: server crash
            """.trimIndent()

        assertEquals(
            listOf("22:11:39 E/SSVR: server crash"),
            buildLogDisplayLines(
                logContent = content,
                searchQuery = "crash",
                selectedTags = setOf("SSVR"),
                loadingText = "Loading",
                noResultsText = "No results",
            ),
        )
    }
}
