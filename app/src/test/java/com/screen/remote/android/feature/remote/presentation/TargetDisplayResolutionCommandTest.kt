package com.screen.remote.android.feature.remote.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TargetDisplayResolutionCommandTest {
    @Test
    fun `adapt command applies local size before density`() {
        assertEquals(
            "wm size 1920x1080 && wm density 400",
            buildTargetDisplayResolutionCommand(
                width = 1920,
                height = 1080,
                densityDpi = 400,
                adapted = true,
            ),
        )
    }

    @Test
    fun `restore command resets both size and density`() {
        assertEquals(
            "wm size reset && wm density reset",
            buildTargetDisplayResolutionCommand(
                width = 0,
                height = 0,
                densityDpi = 0,
                adapted = false,
            ),
        )
    }

    @Test
    fun `invalid display values are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildTargetDisplayResolutionCommand(
                width = 0,
                height = 1080,
                densityDpi = 400,
                adapted = true,
            )
        }
    }
}
