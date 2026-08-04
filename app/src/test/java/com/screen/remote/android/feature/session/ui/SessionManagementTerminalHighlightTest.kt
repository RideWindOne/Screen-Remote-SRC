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
    fun rendersCommandDoneMarkerAsSeparator() {
        val lines =
            parseTerminalOutputLines(
                "$ echo sample\nsample\n\n__SCREEN_REMOTE_COMMAND_DONE__:0\n$ pwd",
            )

        assertEquals(TerminalOutputTone.PROMPT, lines[0].tone)
        assertEquals(TerminalOutputTone.NORMAL, lines[1].tone)
        assertEquals(TerminalOutputTone.COMMAND_SEPARATOR, lines[2].tone)
        assertEquals("", lines[2].text)
        assertEquals(TerminalOutputTone.PROMPT, lines[3].tone)
    }

    @Test
    fun buffersFragmentedCommandDoneMarkerUntilItCanBeHidden() {
        val first = partitionTerminalMarkerTail("output\n__SCREEN_REMOTE_COMMAND")
        assertEquals("output\n", first.visible)
        assertEquals("__SCREEN_REMOTE_COMMAND", first.pending)

        val complete = partitionTerminalMarkerTail(first.visible + first.pending + "_DONE__:0\n")
        assertTrue(complete.pending.isEmpty())
        assertTrue(complete.visible.contains("__SCREEN_REMOTE_COMMAND_DONE__:0"))
        assertTrue(parseTerminalOutputLines(complete.visible).any { it.tone == TerminalOutputTone.COMMAND_SEPARATOR })
    }

    @Test
    fun preservesPtyOutputThatUsesCrLfLineEndings() {
        val result =
            appendTerminalTextChunk(
                currentOutput = "$ ls\n",
                incomingText = "Download\r\nPictures\r\n",
            )

        assertEquals("$ ls\nDownload\nPictures\n", result.output)
        assertEquals(false, result.pendingCarriageReturn)
    }

    @Test
    fun preservesCrLfSplitAcrossShellPackets() {
        val first =
            appendTerminalTextChunk(
                currentOutput = "$ getprop\n",
                incomingText = "value\r",
            )
        val second =
            appendTerminalTextChunk(
                currentOutput = first.output,
                incomingText = "\nnext\r\n",
                pendingCarriageReturn = first.pendingCarriageReturn,
            )

        assertEquals("$ getprop\nvalue\nnext\n", second.output)
        assertEquals(false, second.pendingCarriageReturn)
    }

    @Test
    fun standaloneCarriageReturnStillReplacesCurrentLine() {
        val result =
            appendTerminalTextChunk(
                currentOutput = "$ task\nprogress 10%",
                incomingText = "\rprogress 20%",
            )

        assertEquals("$ task\nprogress 20%", result.output)
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

    @Test
    fun completesCommandAndPathTokensWithoutInsertingTab() {
        val commandTarget = shellCompletionTarget("getpr")!!
        assertTrue(commandTarget.commandToken)
        assertEquals("getprop ", applyShellCompletion("getpr", commandTarget, listOf("getprop")))

        val pathTarget = shellCompletionTarget("ls Down")!!
        assertEquals(false, pathTarget.commandToken)
        assertEquals(
            "ls Download/",
            applyShellCompletion("ls Down", pathTarget, listOf("Download/")),
        )
    }

    @Test
    fun completionMatchesCommandAndPathPrefixesIgnoringCase() {
        val commandTarget = shellCompletionTarget("GETP")!!
        assertEquals("getprop ", applyShellCompletion("GETP", commandTarget, listOf("getprop")))

        val pathTarget = shellCompletionTarget("ls down")!!
        assertEquals(
            "ls Download/",
            applyShellCompletion("ls down", pathTarget, listOf("Download/")),
        )
    }

    @Test
    fun selectsOneCompletionCandidateAtATime() {
        val target = shellCompletionTarget("pm list pack")!!
        assertEquals(
            "pm list package ",
            applyShellCompletion(
                "pm list pack",
                target,
                listOf("package", "packages.xml"),
            ),
        )
        assertEquals(
            "pm list packages.xml ",
            applyShellCompletion(
                "pm list pack",
                target,
                listOf("package", "packages.xml"),
                candidateIndex = 1,
            ),
        )
        assertEquals(
            "pm list package ",
            applyShellCompletion(
                "pm list pack",
                target,
                listOf("package", "packages.xml"),
                candidateIndex = 2,
            ),
        )
    }

    @Test
    fun advancesToDirectoryChildrenOnlyAfterAUniqueDirectoryMatch() {
        val rootTarget = shellCompletionTarget("cd /sd")!!
        val completedDirectory = applyShellCompletion("cd /sd", rootTarget, listOf("sdcard/"))
        assertEquals("cd /sdcard/", completedDirectory)
        assertTrue(shouldLoadNextShellCompletionLevel(completedDirectory, listOf("sdcard/")))

        val childTarget = shellCompletionTarget(completedDirectory)!!
        assertEquals(
            "cd /sdcard/Download/",
            applyShellCompletion(completedDirectory, childTarget, listOf("Download/", "Pictures/")),
        )
        assertEquals(
            "cd /sdcard/Pictures/",
            applyShellCompletion(completedDirectory, childTarget, listOf("Download/", "Pictures/"), candidateIndex = 1),
        )
        assertEquals(
            false,
            shouldLoadNextShellCompletionLevel("cd /sdcard/Download/", listOf("Download/", "Pictures/"))
        )
    }

    private fun TerminalOutputLine.matchedText(): String {
        val range = grepMatches.single()
        return text.substring(range.start, range.endExclusive)
    }
}
