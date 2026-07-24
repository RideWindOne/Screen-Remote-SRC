package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomShellCommandPayloadTest {
    @Test
    fun specialShellSyntaxIsWrittenWithoutEscapingOrRewriting() {
        val commands =
            listOf(
                "getprop | grep 'product' | head -n 2",
                $$"echo \"$HOME\" && printf '%s\\n' \"$(getprop ro.product.model)\"",
                "cat /proc/meminfo > /sdcard/mem.txt 2>&1; tail -n 3 /sdcard/mem.txt",
                "FLAG=on; [ \"\$FLAG\" = on ] || exit 1",
                "printf 'a\\nb\\n' | grep -E 'a|b'",
                "cat <<'EOF'\npipe | dollar \$HOME && semicolon ;\nEOF",
            )

        commands.forEach { command ->
            val payload = buildManagementShellPayload(command)
            assertTrue(payload.startsWith("$command\n"))
            assertEquals(command, payload.substringBefore("\nprintf '\\n__SCREEN_REMOTE_COMMAND_DONE__"))
            assertTrue(payload.endsWith("printf '\\n__SCREEN_REMOTE_COMMAND_DONE__:%s\\n' \"\$?\"\n"))
        }
    }

    @Test
    fun multilineCommandKeepsEveryLineInOrder() {
        val command = "echo first\necho second | grep second\necho third > /sdcard/result.txt"

        val payload = buildManagementShellPayload(command)

        assertEquals(command, payload.lines().take(3).joinToString("\n"))
    }
}
