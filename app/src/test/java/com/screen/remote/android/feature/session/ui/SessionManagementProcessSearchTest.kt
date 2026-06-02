package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagementProcessSearchTest {
    @Test
    fun `matches app title ignoring case and surrounding whitespace`() {
        assertTrue(
            matchesProcessSearch(
                displayTitle = "Screen Remote",
                packageName = "com.example.remote",
                childNames = emptyList(),
                query = "  SCREEN  ",
            ),
        )
    }

    @Test
    fun `matches package name and child process name`() {
        assertTrue(
            matchesProcessSearch(
                displayTitle = "Remote",
                packageName = "com.example.remote",
                childNames = listOf("com.example.remote:worker"),
                query = "example.remote",
            ),
        )
        assertTrue(
            matchesProcessSearch(
                displayTitle = "Remote",
                packageName = "com.example.remote",
                childNames = listOf("com.example.remote:worker"),
                query = "worker",
            ),
        )
    }

    @Test
    fun `rejects unrelated query`() {
        assertFalse(
            matchesProcessSearch(
                displayTitle = "Remote",
                packageName = "com.example.remote",
                childNames = listOf("com.example.remote:worker"),
                query = "camera",
            ),
        )
    }
}
