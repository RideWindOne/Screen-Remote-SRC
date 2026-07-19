package com.screen.remote.android.feature.remote.widget.touch

import com.screen.remote.android.infrastructure.scrcpy.connection.TouchAction
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteTouchPressureTest {
    @Test
    fun `active virtual fingers use full pressure`() {
        assertEquals(1f, remoteFingerPressure(TouchAction.ACTION_DOWN))
        assertEquals(1f, remoteFingerPressure(TouchAction.ACTION_MOVE))
    }

    @Test
    fun `released virtual finger uses zero pressure`() {
        assertEquals(0f, remoteFingerPressure(TouchAction.ACTION_UP))
    }
}
