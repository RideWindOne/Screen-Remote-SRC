package com.screen.remote.android.app.deeplink

import com.screen.remote.android.core.data.repository.ConnectionCandidateData
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.ScrcpyConfig
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlRuntimeSessionTest {
    private val stored =
        SessionData(
            id = "stored-id",
            name = "Living room",
            connectionCandidates = listOf(ConnectionCandidateData("TCP", "192.168.1.20", 5555)),
            color = "BLUE",
            config = ScrcpyConfig(maxFps = 60, enableAudio = false),
            profileId = "profile-id",
            useProfileDefaults = true,
        )

    @Test
    fun `clones effective profile config and overrides only URL fields`() {
        val effectiveOptions =
            ScrcpyOptions(
                sessionId = stored.id,
                profileId = stored.profileId,
                connectionCandidates =
                    listOf(ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.20", 5555)),
                config = ScrcpyConfig(maxFps = 90, enableAudio = true),
            )

        val runtime =
            stored.toUrlRuntimeSession(
                runtimeId = "url:runtime",
                parameters = mapOf("maxFps" to "120"),
                effectiveOptions = effectiveOptions,
            ).getOrThrow()

        assertEquals("url:runtime", runtime.id)
        assertEquals(120, runtime.config.maxFps)
        assertEquals(true, runtime.config.enableAudio)
        assertEquals("", runtime.profileId)
        assertEquals(false, runtime.useProfileDefaults)
        assertEquals(60, stored.config.maxFps)
    }

    @Test
    fun `host target clones default session config in memory`() {
        val runtime =
            stored.copy(profileId = "", useProfileDefaults = false)
                .toUrlRuntimeSession(
                    runtimeId = "url:host",
                    parameters = mapOf("audio" to "on"),
                ).getOrThrow()

        assertEquals("url:host", runtime.id)
        assertEquals(true, runtime.config.enableAudio)
        assertEquals(60, runtime.config.maxFps)
    }
}
