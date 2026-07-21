package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagementTerminalHighlightTest {
    @Test
    fun classifiesSemanticOutputLines() {
        val lines =
            parseTerminalOutputLines(
                "Connected and ready\nWarning: retrying\nPermission denied\nplain output",
            )

        assertEquals(TerminalOutputTone.SUCCESS, lines[0].tone)
        assertEquals(TerminalOutputTone.WARNING, lines[1].tone)
        assertEquals(TerminalOutputTone.ERROR, lines[2].tone)
        assertEquals(TerminalOutputTone.NORMAL, lines[3].tone)
    }

    @Test
    fun highlightsMatchesFromGrepPipeline() {
        val lines =
            parseTerminalOutputLines(
                "$ dumpsys battery | grep -Ei 'level|status'\nlevel: 79\nstatus: 4\ntemperature: 357",
            )

        assertEquals(TerminalOutputTone.PROMPT, lines[0].tone)
        assertEquals("level", lines[1].matchedText())
        assertEquals("status", lines[2].matchedText())
        assertTrue(lines[3].grepMatches.isEmpty())
    }

    @Test
    fun supportsRipgrepAndInvalidLiteralPatterns() {
        val ripgrep = extractGrepPattern("rg -i 'screen remote' /data/local/tmp")
        val invalidPattern = extractGrepPattern("grep '[broken' file.txt")

        assertNotNull(ripgrep)
        assertTrue(ripgrep!!.containsMatchIn("SCREEN REMOTE"))
        assertNotNull(invalidPattern)
        assertTrue(invalidPattern!!.containsMatchIn("[broken"))
    }

    @Test
    fun blocksInteractiveFullScreenCommandsButAllowsStreamingCommands() {
        assertEquals("vim", unsupportedInteractiveCommand("vim /sdcard/test.txt"))
        assertEquals("vi", unsupportedInteractiveCommand("busybox vi /sdcard/test.txt"))
        assertEquals("nano", unsupportedInteractiveCommand("FOO=bar env TERM=xterm nano file.txt"))
        assertEquals("less", unsupportedInteractiveCommand("cat file.txt | less"))
        assertEquals("nvim", unsupportedInteractiveCommand("sh -c 'nvim /data/local/tmp/test'"))
        assertEquals(null, unsupportedInteractiveCommand("tail -f /sdcard/log.txt"))
        assertEquals(null, unsupportedInteractiveCommand("cat /sdcard/test.txt | grep error"))
    }

    @Test
    fun commandHistoryKeepsAllUniqueCommandsForManagementSession() {
        var history = emptyList<String>()
        repeat(20) { index ->
            history = nextTerminalCommandHistory(history, "command-$index")
        }
        history = nextTerminalCommandHistory(history, "command-5")

        assertEquals(20, history.size)
        assertEquals("command-5", history.first())
        assertEquals(1, history.count { it == "command-5" })
    }

    private fun TerminalOutputLine.matchedText(): String {
        val range = grepMatches.single()
        return text.substring(range.start, range.endExclusive)
    }
}
