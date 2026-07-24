package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionManagementDeviceInfoSupportTest {
    @Test
    fun `proc uptime parser accepts the complete proc line`() {
        assertEquals(12_345L, parseProcUptimeSeconds("12345.67 98765.43"))
    }

    @Test
    fun `proc uptime parser rejects unavailable values`() {
        assertNull(parseProcUptimeSeconds(""))
        assertNull(parseProcUptimeSeconds("unavailable"))
    }
}
