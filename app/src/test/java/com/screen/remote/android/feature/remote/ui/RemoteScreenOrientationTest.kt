package com.screen.remote.android.feature.remote.ui

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteScreenOrientationTest {
    @Test
    fun `landscape video requests landscape screen`() {
        assertEquals(RemoteScreenOrientation.LANDSCAPE, remoteScreenOrientation(2400, 1080))
    }

    @Test
    fun `portrait video requests portrait screen`() {
        assertEquals(RemoteScreenOrientation.PORTRAIT, remoteScreenOrientation(1080, 2400))
    }

    @Test
    fun `unknown or square video keeps current screen orientation`() {
        assertNull(remoteScreenOrientation(0, 2400))
        assertNull(remoteScreenOrientation(1080, 0))
        assertNull(remoteScreenOrientation(1080, 1080))
    }

    @Test
    fun `landscape follows local sensor while portrait stays upright`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            requestedOrientationForRemoteScreen(RemoteScreenOrientation.LANDSCAPE),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            requestedOrientationForRemoteScreen(RemoteScreenOrientation.PORTRAIT),
        )
        assertNull(requestedOrientationForRemoteScreen(null))
    }

    @Test
    fun `local rotation policy removes orientation restrictions`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            requestedOrientationForRotationPolicy(
                followRemoteOrientation = false,
                remoteOrientation = RemoteScreenOrientation.LANDSCAPE,
            ),
        )
    }

    @Test
    fun `target rotation policy follows the remote screen`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            requestedOrientationForRotationPolicy(
                followRemoteOrientation = true,
                remoteOrientation = RemoteScreenOrientation.LANDSCAPE,
            ),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            requestedOrientationForRotationPolicy(
                followRemoteOrientation = true,
                remoteOrientation = RemoteScreenOrientation.PORTRAIT,
            ),
        )
    }
}
