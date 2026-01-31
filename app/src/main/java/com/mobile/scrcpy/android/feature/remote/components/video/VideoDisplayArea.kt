package com.mobile.scrcpy.android.feature.remote.components.video

import android.annotation.SuppressLint
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.feature.remote.viewmodel.ConnectionViewModel
import com.mobile.scrcpy.android.feature.remote.viewmodel.ControlViewModel
import com.mobile.scrcpy.android.core.data.repository.SessionData

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun VideoDisplayArea(
    controlViewModel: ControlViewModel,
    connectionViewModel: ConnectionViewModel,
    sessionData: SessionData?,
    videoAspectRatio: Float,
    configuration: android.content.res.Configuration,
    onSurfaceHolderChanged: (SurfaceHolder?) -> Unit,
    videoDecoderManager: VideoDecoderManager,
) {
    // 记录每个指针的最后位置（远程坐标），用于抖动过滤
    val lastRemoteX = remember { IntArray(10) }
    val lastRemoteY = remember { IntArray(10) }
    val moveThreshold = 8 // 移动阈值（远程设备像素）

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
                    Modifier.fillMaxSize().aspectRatio(
                        videoAspectRatio,
                        matchHeightConstraintsFirst = matchHeightFirst,
                    ),
            )
        } else {
            VideoSurfaceView(
                modifier =
                    Modifier.fillMaxSize().aspectRatio(
                        videoAspectRatio,
                        matchHeightConstraintsFirst = matchHeightFirst,
                    ),
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

                        kotlinx.coroutines.runBlocking {
                            when (event.actionMasked) {
                                android.view.MotionEvent.ACTION_DOWN,
                                android.view.MotionEvent.ACTION_POINTER_DOWN,
                                -> {
                                    val actionIndex = event.actionIndex
                                    val pointerId = event.getPointerId(actionIndex)

                                    val x = (event.getX(actionIndex) / view.width * deviceWidth).toInt()
                                    val y = (event.getY(actionIndex) / view.height * deviceHeight).toInt()

                                    // 记录远程坐标
                                    lastRemoteX[pointerId] = x
                                    lastRemoteY[pointerId] = y

                                    LogManager.e(LogTags.SCRCPY_CLIENT, "🔵 DOWN: pid=$pointerId, remote=($x,$y)")

                                    controlViewModel.sendTouchEvent(
                                        action = 0, // DOWN
                                        pointerId = pointerId.toLong(),
                                        x = x,
                                        y = y,
                                        screenWidth = deviceWidth,
                                        screenHeight = deviceHeight,
                                        pressure = event.pressure,
                                    )
                                }

                                android.view.MotionEvent.ACTION_MOVE -> {
                                    for (i in 0 until event.pointerCount) {
                                        val pointerId = event.getPointerId(i)

                                        val x = (event.getX(i) / view.width * deviceWidth).toInt()
                                        val y = (event.getY(i) / view.height * deviceHeight).toInt()

                                        val deltaX = x - lastRemoteX[pointerId]
                                        val deltaY = y - lastRemoteY[pointerId]

                                        // 使用远程坐标的 delta 判断是否超过阈值
                                        if (deltaY < -moveThreshold || deltaY > moveThreshold ||
                                            deltaX < -moveThreshold || deltaX > moveThreshold
                                        ) {
                                            lastRemoteX[pointerId] = x
                                            lastRemoteY[pointerId] = y

                                            LogManager.e(
                                                LogTags.SCRCPY_CLIENT,
                                                "🟢 MOVE: pid=$pointerId, remote=($x,$y), delta=($deltaX,$deltaY)",
                                            )

                                            controlViewModel.sendTouchEvent(
                                                action = 2, // MOVE
                                                pointerId = pointerId.toLong(),
                                                x = x,
                                                y = y,
                                                screenWidth = deviceWidth,
                                                screenHeight = deviceHeight,
                                                pressure = event.pressure,
                                            )
                                        }
                                    }
                                }

                                android.view.MotionEvent.ACTION_UP,
                                android.view.MotionEvent.ACTION_POINTER_UP,
                                -> {
                                    val actionIndex = event.actionIndex
                                    val pointerId = event.getPointerId(actionIndex)

                                    // 使用最后记录的远程坐标，避免 UP 事件的坐标抖动
                                    val x = lastRemoteX[pointerId]
                                    val y = lastRemoteY[pointerId]

                                    LogManager.e(LogTags.SCRCPY_CLIENT, "🔴 UP: pid=$pointerId, remote=($x,$y)")

                                    if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                                        view.performClick()
                                    }

                                    controlViewModel.sendTouchEvent(
                                        action = 1, // UP
                                        pointerId = pointerId.toLong(),
                                        x = x,
                                        y = y,
                                        screenWidth = deviceWidth,
                                        screenHeight = deviceHeight,
                                        pressure = 0f,
                                    )
                                }

                                else -> {
                                    return@runBlocking false
                                }
                            }
                        }
                    }
                    true
                },
            )
        }
    }
}
