package com.mobile.scrcpy.android.feature.remote.components.video

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mobile.scrcpy.android.feature.remote.viewmodel.ConnectionViewModel
import com.mobile.scrcpy.android.feature.remote.viewmodel.ControlViewModel
import com.mobile.scrcpy.android.feature.session.data.repository.SessionData
import kotlinx.coroutines.launch

@Composable
fun VideoDisplayArea(
    controlViewModel: ControlViewModel,
    connectionViewModel: ConnectionViewModel,
    sessionData: SessionData?,
    videoAspectRatio: Float,
    configuration: android.content.res.Configuration,
    surfaceHolder: SurfaceHolder?,
    onSurfaceHolderChanged: (SurfaceHolder?) -> Unit,
    videoDecoderManager: VideoDecoderManager,
) {
    val scope = rememberCoroutineScope()

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
                modifier =
                    Modifier
                        .fillMaxSize()
                        .aspectRatio(
                            videoAspectRatio,
                            matchHeightConstraintsFirst = matchHeightFirst,
                        ),
            )
        } else {
            VideoSurfaceView(
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
                onTouch = { view, event ->
                    val resolution = connectionViewModel.getVideoResolution().value
                    if (resolution != null) {
                        val (deviceWidth, deviceHeight) = resolution

                        val x = (event.x / view.width * deviceWidth).toInt()
                        val y = (event.y / view.height * deviceHeight).toInt()

                        val action =
                            when (event.actionMasked) {
                                android.view.MotionEvent.ACTION_DOWN -> {
                                    0
                                }

                                android.view.MotionEvent.ACTION_UP -> {
                                    view.performClick()
                                    1
                                }

                                android.view.MotionEvent.ACTION_MOVE -> {
                                    2
                                }

                                else -> {
                                    return@VideoSurfaceView false
                                }
                            }

                        scope.launch {
                            controlViewModel.sendTouchEvent(
                                action = action,
                                pointerId = event.getPointerId(0).toLong(),
                                x = x,
                                y = y,
                                screenWidth = deviceWidth,
                                screenHeight = deviceHeight,
                                pressure = event.pressure,
                            )
                        }
                    }
                    true
                },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .aspectRatio(
                            videoAspectRatio,
                            matchHeightConstraintsFirst = matchHeightFirst,
                        ),
            )
        }
    }
}
