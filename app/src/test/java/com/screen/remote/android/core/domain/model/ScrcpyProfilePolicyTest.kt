package com.screen.remote.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrcpyProfilePolicyTest {
    @Test
    fun `default options and profile use four megabits per second`() {
        val candidate = ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.2", 5555)

        assertEquals(4_000_000, ScrcpyOptions("session-1", connectionCandidates = listOf(candidate)).config.videoBitRate)
        assertEquals(4_000_000, ScrcpyProfile.default().config.videoBitRate)
    }

    @Test
    fun profileReplacesTheCompleteConfigOnly() {
        val options =
            ScrcpyOptions(
                sessionId = "session-1",
                connectionCandidates = listOf(ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.2", 5555)),
                config = ScrcpyConfig(gameMode = true),
                capabilityCache = DeviceCapabilityCache(deviceSerial = "device-a", selectedVideoEncoder = "encoder-a"),
            )
        val profile =
            ScrcpyProfile(
                id = "profile-1",
                name = "Low bandwidth",
                config = ScrcpyConfig(maxSize = 720, videoBitRate = 2_000_000, enableAudio = true),
            )

        val applied = options.withProfile(profile)

        assertEquals(profile.config, applied.config)
        assertEquals("192.168.1.2", applied.connectionCandidates.single().host)
        assertEquals("device-a", applied.capabilityCache.deviceSerial)
        assertEquals("encoder-a", applied.capabilityCache.selectedVideoEncoder)
    }
}
