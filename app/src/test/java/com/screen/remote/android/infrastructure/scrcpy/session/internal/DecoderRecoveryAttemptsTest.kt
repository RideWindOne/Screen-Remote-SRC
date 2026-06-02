package com.screen.remote.android.infrastructure.scrcpy.session.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderRecoveryAttemptsTest {
    @Test
    fun decoderRecoveryStopsAtItsIndependentLimit() {
        val bindings = SessionRuntimeBindings()

        assertTrue(bindings.tryConsumeDecoderRecoveryAttempt(maxAttempts = 2))
        assertTrue(bindings.tryConsumeDecoderRecoveryAttempt(maxAttempts = 2))
        assertFalse(bindings.tryConsumeDecoderRecoveryAttempt(maxAttempts = 2))
    }
}
