package com.screen.remote.android.infrastructure.scrcpy.connection

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ConnectionSocketOrderTest {
    @Test
    fun `audio enabled opens video audio control strictly one at a time`() = runBlocking {
        val opened = mutableListOf<String>()
        val sockets = linkedMapOf<String, String>()
        var activeOpeners = 0
        var maxActiveOpeners = 0

        openScrcpyChannelsSequentially(enableAudio = true, destination = sockets) { type ->
            activeOpeners++
            maxActiveOpeners = maxOf(maxActiveOpeners, activeOpeners)
            delay(1)
            opened += type
            activeOpeners--
            type
        }

        assertEquals(listOf("video", "audio", "control"), opened)
        assertEquals(listOf("video", "audio", "control"), sockets.keys.toList())
        assertEquals(1, maxActiveOpeners)
    }

    @Test
    fun `audio disabled opens video then control`() = runBlocking {
        val opened = mutableListOf<String>()

        openScrcpyChannelsSequentially(enableAudio = false, destination = linkedMapOf()) { type ->
            opened += type
            type
        }

        assertEquals(listOf("video", "control"), opened)
    }

    @Test
    fun `failure in an earlier channel never opens later channels`() = runBlocking {
        val opened = mutableListOf<String>()

        val failure =
            runCatching {
                openScrcpyChannelsSequentially(enableAudio = true, destination = linkedMapOf()) { type ->
                    opened += type
                    if (type == "audio") throw IOException("audio failed")
                    type
                }
            }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(listOf("video", "audio"), opened)
    }
}
