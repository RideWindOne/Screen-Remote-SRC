package com.screen.remote.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrcpyProfilePolicyTest {
    @Test
    fun `default options and profile use four megabits per second`() {
        val candidate = ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.2", 5555)

        assertEquals(4_000_000, ScrcpyOptions("session-1", connectionCandidates = listOf(candidate)).videoBitRate)
        assertEquals(4_000_000, ScrcpyProfile.default().videoBitRate)
    }

    @Test
    fun profileOverridesOnlyScrcpyOptions() {
        val options =
            ScrcpyOptions(
                sessionId = "session-1",
                connectionCandidates = listOf(ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.2", 5555)),
                deviceSerial = "device-a",
                selectedVideoEncoder = "encoder-a",
            )
        val profile =
            ScrcpyProfile(
                id = "profile-1",
                name = "Low bandwidth",
                maxSize = 720,
                videoBitRate = 2_000_000,
                enableAudio = true,
            )

        val applied = options.withProfile(profile)

        assertEquals(720, applied.maxSize)
        assertEquals(2_000_000, applied.videoBitRate)
        assertEquals(true, applied.enableAudio)
        assertEquals("192.168.1.2", applied.connectionCandidates.single().host)
        assertEquals("device-a", applied.deviceSerial)
        assertEquals("encoder-a", applied.selectedVideoEncoder)
    }
}
