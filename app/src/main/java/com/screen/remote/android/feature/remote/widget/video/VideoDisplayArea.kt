package com.screen.remote.android.feature.remote.widget.video

import android.annotation.SuppressLint
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.feature.remote.presentation.ControlViewModel
import com.screen.remote.android.feature.remote.presentation.VideoDecoderManager
import com.screen.remote.android.feature.remote.widget.touch.RemoteTouchEventDispatcher

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
    onRenderSurfaceChanged: (Surface?) -> Unit,
    videoDecoderManager: VideoDecoderManager,
    overlayContent: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    val touchDispatcher =
        remember(controlViewModel) {
            RemoteTouchEventDispatcher { action, pointerId, x, y, screenWidth, screenHeight, pressure ->
                controlViewModel.sendTouchEvent(
                    action = action,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    pressure = pressure,
                )
            }
        }

    DisposableEffect(touchDispatcher, videoWidth, videoHeight) {
        onDispose {
            touchDispatcher.cancelActivePointers(videoWidth, videoHeight)
        }
    }

    val handleTouch: (View, android.view.MotionEvent) -> Boolean = { view, event ->
        touchDispatcher.handle(view, event, videoWidth, videoHeight)
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

        val useFullScreen = sessionData?.config?.let { it.useFullScreen && !it.gameMode } ?: false

        Box(
            modifier =
                Modifier.fillMaxSize().aspectRatio(
                    videoAspectRatio,
                    matchHeightConstraintsFirst = matchHeightFirst,
                ),
        ) {
            if (useFullScreen) {
                var renderSurface by remember { mutableStateOf<Surface?>(null) }

                DisposableEffect(Unit) {
                    onDispose {
                        // MediaCodec 必须先切回 dummy Surface，再释放 TextureView 的包装 Surface。
                        videoDecoderManager.videoDecoder?.setSurface(null)
                        renderSurface?.release()
                        renderSurface = null
                        onRenderSurfaceChanged(null)
                    }
                }

                VideoTextureView(
                    onSurfaceTextureAvailable = { texture ->
                        val surface =
                            renderSurface?.takeIf { it.isValid }
                                ?: Surface(texture).also { renderSurface = it }
                        onRenderSurfaceChanged(surface)
                        videoDecoderManager.setSurfaceImmediate(surface)
                    },
                    onSurfaceTextureDestroyed = {
                        videoDecoderManager.videoDecoder?.setSurface(null)
                        renderSurface?.release()
                        renderSurface = null
                        onRenderSurfaceChanged(null)
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
