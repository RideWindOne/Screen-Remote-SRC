package com.screen.remote.android.feature.remote.widget.touch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardClipboardRoutingTest {
    @Test
    fun `single basic ASCII character keeps low latency text injection`() {
        assertFalse(shouldUseClipboardPaste("a"))
        assertFalse(shouldUseClipboardPaste("!"))
    }

    @Test
    fun `Chinese emoji special whitespace and multi-character paste use clipboard`() {
        assertTrue(shouldUseClipboardPaste("中"))
        assertTrue(shouldUseClipboardPaste("😀"))
        assertTrue(shouldUseClipboardPaste("\n"))
        assertTrue(shouldUseClipboardPaste("a\tb"))
        assertTrue(shouldUseClipboardPaste("pasted text with 'quotes' & symbols"))
    }

    @Test
    fun `large input uses clipboard instead of 300-byte inject-text packet`() {
        assertTrue(shouldUseClipboardPaste("a".repeat(301)))
    }
}
