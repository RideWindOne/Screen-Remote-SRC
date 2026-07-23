package com.screen.remote.android.infrastructure.scrcpy.client

import org.junit.Assert.assertSame
import org.junit.Test

class ScrcpyDiagnosticsRegistryTest {
    @Test
    fun `registry prefers client selected as active`() {
        val inactive = Any()
        val active = Any()
        val newestInactive = Any()

        assertSame(
            active,
            selectDiagnosticsCandidate(
                candidates = listOf(inactive, active, newestInactive),
                isActive = { it === active },
            ),
        )
    }

    @Test
    fun `registry falls back to newest client when no session is active`() {
        val older = Any()
        val newest = Any()

        assertSame(
            newest,
            selectDiagnosticsCandidate(
                candidates = listOf(older, newest),
                isActive = { false },
            ),
        )
    }
}
