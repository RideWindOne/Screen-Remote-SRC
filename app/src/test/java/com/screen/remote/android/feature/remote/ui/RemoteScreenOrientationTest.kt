package com.screen.remote.android.feature.remote.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import com.screen.remote.android.core.domain.model.ScreenRotationPolicy
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
    fun `none rotation policy preserves the original orientation request`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_USER,
            requestedOrientationForRotationPolicy(
                policy = ScreenRotationPolicy.NONE,
                remoteOrientation = RemoteScreenOrientation.LANDSCAPE,
                originalRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER,
            ),
        )
    }

    @Test
    fun `local rotation policy removes controller orientation restrictions`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            requestedOrientationForRotationPolicy(
                policy = ScreenRotationPolicy.LOCAL,
                remoteOrientation = RemoteScreenOrientation.LANDSCAPE,
                originalRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER,
            ),
        )
    }

    @Test
    fun `target rotation policy follows the remote screen`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            requestedOrientationForRotationPolicy(
                policy = ScreenRotationPolicy.TARGET,
                remoteOrientation = RemoteScreenOrientation.LANDSCAPE,
                originalRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER,
            ),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            requestedOrientationForRotationPolicy(
                policy = ScreenRotationPolicy.TARGET,
                remoteOrientation = RemoteScreenOrientation.PORTRAIT,
                originalRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER,
            ),
        )
    }

    @Test
    fun `local policy rotates the target only when orientations differ`() {
        assertEquals(RemoteScreenOrientation.LANDSCAPE, localScreenOrientation(Configuration.ORIENTATION_LANDSCAPE))
        assertEquals(RemoteScreenOrientation.PORTRAIT, localScreenOrientation(Configuration.ORIENTATION_PORTRAIT))
        assertEquals(
            true,
            shouldRotateTargetForLocalPolicy(
                ScreenRotationPolicy.LOCAL,
                RemoteScreenOrientation.LANDSCAPE,
                RemoteScreenOrientation.PORTRAIT,
            ),
        )
        assertEquals(
            false,
            shouldRotateTargetForLocalPolicy(
                ScreenRotationPolicy.LOCAL,
                RemoteScreenOrientation.PORTRAIT,
                RemoteScreenOrientation.PORTRAIT,
            ),
        )
    }

    @Test
    fun `leaving remote screen reapplies the user orientation policy`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_USER,
            requestedOrientationAfterRemoteSession(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
        )
    }

    @Test
    fun `leaving remote screen preserves an explicit original orientation`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            requestedOrientationAfterRemoteSession(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
        )
    }
}
