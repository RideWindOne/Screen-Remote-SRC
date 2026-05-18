package com.mobile.scrcpy.android.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointUtilsTest {
    @Test
    fun parseHostPort_acceptsIpv4Authority() {
        assertEquals(HostPort("192.168.1.8", 5555), parseHostPort("192.168.1.8:5555"))
    }

    @Test
    fun parseHostPort_acceptsBracketedIpv6Authority() {
        assertEquals(HostPort("fd7a:115c:a1e0::1", 5555), parseHostPort("[fd7a:115c:a1e0::1]:5555"))
    }

    @Test
    fun parseHostPort_rejectsUnbracketedIpv6Authority() {
        assertNull(parseHostPort("fd7a:115c:a1e0::1:5555"))
    }

    @Test
    fun parseHostPort_canAcceptUnbracketedIpv6AuthorityWhenExplicitlyAllowed() {
        assertEquals(
            HostPort("fd7a:115c:a1e0::1", 5555),
            parseHostPort("fd7a:115c:a1e0::1:5555", allowUnbracketedIpv6 = true),
        )
    }

    @Test
    fun formatHostPort_wrapsIpv6Host() {
        assertEquals("[fd7a:115c:a1e0::1]:5555", formatHostPort("fd7a:115c:a1e0::1", 5555))
    }

    @Test
    fun normalizeEndpointHost_removesAuthorityBrackets() {
        assertEquals("fd7a:115c:a1e0::1", normalizeEndpointHost("[fd7a:115c:a1e0::1]"))
    }
}
