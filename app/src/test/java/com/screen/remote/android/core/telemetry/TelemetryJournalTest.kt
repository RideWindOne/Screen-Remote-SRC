package com.screen.remote.android.core.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryJournalTest {
    @Test
    fun stripsEndpointAndSessionIdentityFromSessionStart() {
        assertEquals(
            "event=session_start transport=tcp reconnecting=false",
            TelemetryJournal.normalizeDiagnostic(
                "DIAG session-start session=private-session device=tcp:example.test:5555 reconnecting=false",
            ),
        )
    }

    @Test
    fun keepsOnlyCandidateTransport() {
        assertEquals(
            "event=candidate_failure transport=usb",
            TelemetryJournal.normalizeDiagnostic(
                "ADB candidate failed: USB:private-serial 12ms USB device not found",
            ),
        )
    }

    @Test
    fun ignoresOrdinaryVerboseLogMessages() {
        assertNull(
            TelemetryJournal.normalizeDiagnostic(
                "Decoder output frame #60: size=1 render=true",
            ),
        )
    }
}
