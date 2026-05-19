package com.mobile.scrcpy.android.feature.remote.widget.video

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mobile.scrcpy.android.core.data.repository.SessionData
import com.mobile.scrcpy.android.feature.remote.presentation.ConnectionViewModel
import com.mobile.scrcpy.android.feature.remote.presentation.ControlViewModel
import com.mobile.scrcpy.android.feature.remote.presentation.VideoDecoderManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.TouchAction

private data class RemoteTouchPoint(
    val x: Int,
    val y: Int,
)

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun VideoDisplayArea(
    controlViewModel: ControlViewModel,
    sessionData: SessionData?,
    videoAspectRatio: Float,
    videoWidth: Int,
    videoHeight: Int,
    configuration: android.content.res.Configuration,
    onSurfaceHolderChanged: (SurfaceHolder?) -> Unit,
    videoDecoderManager: VideoDecoderManager,
    overlayContent: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    val activeRemotePoints = remember { linkedMapOf<Int, RemoteTouchPoint>() }

    val handleTouch: (View, android.view.MotionEvent) -> Boolean = { view, event ->
        if (videoWidth <= 0 || videoHeight <= 0 || view.width <= 0 || view.height <= 0) {
            false
        } else {
            val deviceWidth = videoWidth
            val deviceHeight = videoHeight

            fun dispatchTouchEvent(
                action: Int,
                pointerId: Int,
                remotePoint: RemoteTouchPoint,
                pressure: Float,
            ) {
                controlViewModel.sendTouchEvent(
                    action = action,
                    pointerId = pointerId.toLong(),
                    x = remotePoint.x,
                    y = remotePoint.y,
                    screenWidth = deviceWidth,
                    screenHeight = deviceHeight,
                    pressure = pressure,
                )
            }

            fun toRemotePoint(pointerIndex: Int): RemoteTouchPoint =
                RemoteTouchPoint(
                    x = (event.getX(pointerIndex) / view.width * deviceWidth).toInt().coerceIn(0, deviceWidth - 1),
                    y = (event.getY(pointerIndex) / view.height * deviceHeight).toInt().coerceIn(0, deviceHeight - 1),
                )

            fun releasePointer(
                pointerId: Int,
                remotePoint: RemoteTouchPoint,
            ) {
                dispatchTouchEvent(TouchAction.ACTION_UP, pointerId, remotePoint, 0f)
                activeRemotePoints.remove(pointerId)
            }

            val eventPointerIds = HashSet<Int>(event.pointerCount)
            val eventRemotePoints = HashMap<Int, RemoteTouchPoint>(event.pointerCount)
            val eventPressures = HashMap<Int, Float>(event.pointerCount)
            for (index in 0 until event.pointerCount) {
                val pointerId = event.getPointerId(index)
                eventPointerIds += pointerId
                eventRemotePoints[pointerId] = toRemotePoint(index)
                eventPressures[pointerId] = event.getPressure(index)
            }

            val disappearedPointerIds = activeRemotePoints.keys.filter { it !in eventPointerIds }
            for (pointerId in disappearedPointerIds) {
                val remotePoint = activeRemotePoints[pointerId] ?: continue
                releasePointer(pointerId, remotePoint)
            }

            if (event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) {
                val pointersToRelease = activeRemotePoints.keys.toList()
                pointersToRelease.forEach { pointerId ->
                    val remotePoint = activeRemotePoints[pointerId] ?: return@forEach
                    releasePointer(pointerId, remotePoint)
                }
                true
            } else {
                val endedPointerId =
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_POINTER_UP,
                        -> event.getPointerId(event.actionIndex)
                        else -> null
                    }

                val justPressed = HashSet<Int>()
                for (index in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(index)
                    if (pointerId == endedPointerId) {
                        continue
                    }
                    if (activeRemotePoints.containsKey(pointerId)) {
                        continue
                    }
                    val remotePoint = eventRemotePoints[pointerId] ?: continue
                    val pressure = eventPressures[pointerId] ?: 0f

                    activeRemotePoints[pointerId] = remotePoint
                    justPressed += pointerId

                    dispatchTouchEvent(TouchAction.ACTION_DOWN, pointerId, remotePoint, pressure)
                }

                for (index in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(index)
                    if (!activeRemotePoints.containsKey(pointerId)) {
                        continue
                    }
                    if (pointerId == endedPointerId) {
                        continue
                    }
                    if (pointerId in justPressed) {
                        continue
                    }

                    val remotePoint = eventRemotePoints[pointerId] ?: continue
                    val lastRemotePoint = activeRemotePoints[pointerId]
                    if (lastRemotePoint == remotePoint) {
                        continue
                    }
                    activeRemotePoints[pointerId] = remotePoint

                    dispatchTouchEvent(TouchAction.ACTION_MOVE, pointerId, remotePoint, eventPressures[pointerId] ?: 0f)
                }

                if (endedPointerId != null) {
                    val remotePoint =
                        eventRemotePoints[endedPointerId]
                            ?: activeRemotePoints[endedPointerId]
                            ?: toRemotePoint(event.actionIndex)

                    if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                        view.performClick()
                    }

                    releasePointer(endedPointerId, remotePoint)
                }

                true
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val containerAspectRatio =
            configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
        val matchHeightFirst = videoAspectRatio < containerAspectRatio

        val useFullScreen = sessionData?.useFullScreen ?: false

        Box(
            modifier =
                Modifier.fillMaxSize().aspectRatio(
                    videoAspectRatio,
                    matchHeightConstraintsFirst = matchHeightFirst,
                ),
        ) {
            if (useFullScreen) {
                var surfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }

                VideoTextureView(
                    onSurfaceTextureAvailable = { texture ->
                        surfaceTexture = texture
                        val surface = Surface(texture)
                        videoDecoderManager.videoDecoder?.setSurface(surface)
                    },
                    onSurfaceTextureDestroyed = {
                        surfaceTexture = null
                    },
                    onTouch = handleTouch,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                VideoSurfaceView(
                    modifier = Modifier.fillMaxSize(),
                    onSurfaceCreated = { holder ->
                        onSurfaceHolderChanged(holder)
                        videoDecoderManager.setSurfaceImmediate(holder)
                    },
                    onSurfaceChanged = { holder, _, _ ->
                        onSurfaceHolderChanged(holder)
                        videoDecoderManager.setSurfaceImmediate(holder)
                    },
                    onSurfaceDestroyed = { _ ->
                        videoDecoderManager.videoDecoder?.setSurface(null)
                        onSurfaceHolderChanged(null)
                    },
                    onTouch = handleTouch,
                )
            }

            overlayContent()
        }
    }
}
