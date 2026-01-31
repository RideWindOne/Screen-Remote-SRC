package com.mobile.scrcpy.android.feature.remote.components.touch

import android.view.MotionEvent
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.client.feature.scrcpy.ScrcpyClient
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.TouchAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 触摸事件处理器
 * 支持多指触摸和长按检测
 *
 * 核心原则（稳健判定模板）：
 * - DOWN/POINTER_DOWN：初始化，记录坐标，重置手势方向
 * - MOVE：唯一的位移来源，但需要验证手势语义
 *   ✅ 方向与手势一致
 *   ✅ 幅度 >= minValidDelta（过滤微抖）
 *   ❌ 方向突变 → 丢弃（抬手噪声）
 *   ❌ 幅度过小 → 丢弃（1px 级抖动）
 * - UP/POINTER_UP：终止信号，使用最后有效坐标，不参与位移计算
 *
 * 这样可以避免 UP 事件坐标异常和抬手抖动导致的方向反转问题（scrcpy 经典坑点）
 */
class TouchHandler(
    private val scrcpyClient: ScrcpyClient,
    private val coroutineScope: CoroutineScope,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val remoteWidth: Int,
    private val remoteHeight: Int,
) {
    private var longPressJob: Job? = null
    private var isLongPressed = false
    private val longPressDelay = 500L // 长按延迟 500ms

    // 记录每个指针的最后位置（屏幕坐标，用于抖动检测）
    private val lastScreenX = IntArray(10)
    private val lastScreenY = IntArray(10)

    // 记录每个指针最后有效的远程坐标（用于 UP 事件）
    private val lastValidRemoteX = IntArray(10)
    private val lastValidRemoteY = IntArray(10)

    // 记录每个指针的手势方向（0=未确定, 1=正向, -1=负向）
    private val gestureDirectionX = IntArray(10)
    private val gestureDirectionY = IntArray(10)

    private val moveThreshold = 4 // 移动阈值（像素），小于此值不发送 MOVE 事件
    private val minValidDelta = 1 // 最小有效位移（远程坐标），小于此值视为微抖

    companion object {
        private const val ENABLE_DEBUG_LOG = true // 开启调试日志
    }

    /**
     * 处理触摸事件
     */
    suspend fun handleTouchEvent(event: MotionEvent): Boolean { // TODO
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex).toLong()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 第一个手指按下
                val screenX = event.x.toInt()
                val screenY = event.y.toInt()
                val x = scaleX(event.x)
                val y = scaleY(event.y)

                val pid = pointerId.toInt()

                // 记录屏幕坐标（用于抖动检测）
                lastScreenX[pid] = screenX
                lastScreenY[pid] = screenY

                // 记录远程坐标（用于 UP 事件）
                lastValidRemoteX[pid] = x
                lastValidRemoteY[pid] = y

                // 重置手势方向
                gestureDirectionX[pid] = 0
                gestureDirectionY[pid] = 0

                if (ENABLE_DEBUG_LOG) {
                    LogManager.d(
                        LogTags.SCRCPY_CLIENT,
                        "🔵 DOWN: pid=$pointerId, screen=($screenX,$screenY), remote=($x,$y)",
                    )
                }

                scrcpyClient.sendTouchEvent(
                    action = TouchAction.ACTION_DOWN,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    screenWidth = remoteWidth,
                    screenHeight = remoteHeight,
                )

                // 启动长按检测
                startLongPressDetection(x, y, pointerId)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 额外手指按下（多指触摸）
                val screenX = event.getX(actionIndex).toInt()
                val screenY = event.getY(actionIndex).toInt()
                val x = scaleX(event.getX(actionIndex))
                val y = scaleY(event.getY(actionIndex))

                val pid = pointerId.toInt()

                // 记录屏幕坐标
                lastScreenX[pid] = screenX
                lastScreenY[pid] = screenY

                // 记录远程坐标
                lastValidRemoteX[pid] = x
                lastValidRemoteY[pid] = y

                // 重置手势方向
                gestureDirectionX[pid] = 0
                gestureDirectionY[pid] = 0

                if (ENABLE_DEBUG_LOG) {
                    LogManager.d(
                        LogTags.SCRCPY_CLIENT,
                        "🔵 POINTER_DOWN: pid=$pointerId, screen=($screenX,$screenY), remote=($x,$y)",
                    )
                }

                scrcpyClient.sendTouchEvent(
                    action = TouchAction.ACTION_POINTER_DOWN,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    screenWidth = remoteWidth,
                    screenHeight = remoteHeight,
                )

                // 取消长按检测（多指操作）
                cancelLongPressDetection()
            }

            MotionEvent.ACTION_MOVE -> {
                // 所有手指移动
                for (i in 0 until event.pointerCount) {
                    val currentPointerId = event.getPointerId(i).toLong()
                    val screenX = event.getX(i).toInt()
                    val screenY = event.getY(i).toInt()
                    val pid = currentPointerId.toInt()

                    // 使用屏幕坐标进行抖动检测
                    val screenDeltaX = screenX - lastScreenX[pid]
                    val screenDeltaY = screenY - lastScreenY[pid]

                    // 只有移动距离超过阈值才处理
                    if (screenDeltaY < -moveThreshold || screenDeltaY > moveThreshold ||
                        screenDeltaX < -moveThreshold || screenDeltaX > moveThreshold
                    ) {
                        // 转换为远程设备坐标
                        val x = scaleX(event.getX(i))
                        val y = scaleY(event.getY(i))

                        // 计算远程坐标位移
                        val remoteDeltaX = x - lastValidRemoteX[pid]
                        val remoteDeltaY = y - lastValidRemoteY[pid]

                        // ✅ 稳健判定：确定手势方向（首次有效移动）
                        if (gestureDirectionX[pid] == 0 && kotlin.math.abs(remoteDeltaX) > minValidDelta) {
                            gestureDirectionX[pid] = if (remoteDeltaX > 0) 1 else -1
                        }
                        if (gestureDirectionY[pid] == 0 && kotlin.math.abs(remoteDeltaY) > minValidDelta) {
                            gestureDirectionY[pid] = if (remoteDeltaY > 0) 1 else -1
                        }

                        // ✅ 稳健判定：检查 MOVE 是否有效
                        val validX =
                            gestureDirectionX[pid] == 0 ||
                                (
                                    remoteDeltaX * gestureDirectionX[pid] >= 0 &&
                                        kotlin.math.abs(remoteDeltaX) >= minValidDelta
                                )
                        val validY =
                            gestureDirectionY[pid] == 0 ||
                                (
                                    remoteDeltaY * gestureDirectionY[pid] >= 0 &&
                                        kotlin.math.abs(remoteDeltaY) >= minValidDelta
                                )

                        if (validX && validY) {
                            // 更新记录的屏幕坐标
                            lastScreenX[pid] = screenX
                            lastScreenY[pid] = screenY

                            // 更新最后有效的远程坐标
                            lastValidRemoteX[pid] = x
                            lastValidRemoteY[pid] = y

                            if (ENABLE_DEBUG_LOG) {
                                LogManager.d(
                                    LogTags.SCRCPY_CLIENT,
                                    "🟢 MOVE: pid=$currentPointerId, screen=($screenX,$screenY), delta=($screenDeltaX,$screenDeltaY), remote=($x,$y), remoteDelta=($remoteDeltaX,$remoteDeltaY)",
                                )
                            }

                            scrcpyClient.sendTouchEvent(
                                action = TouchAction.ACTION_MOVE,
                                pointerId = currentPointerId,
                                x = x,
                                y = y,
                                screenWidth = remoteWidth,
                                screenHeight = remoteHeight,
                            )
                        } else {
                            if (ENABLE_DEBUG_LOG) {
                                LogManager.d(
                                    LogTags.SCRCPY_CLIENT,
                                    "⚠️ MOVE DISCARDED: pid=$currentPointerId, remoteDelta=($remoteDeltaX,$remoteDeltaY), direction=(${gestureDirectionX[pid]},${gestureDirectionY[pid]})",
                                )
                            }
                        }
                    }
                }

                // 移动时取消长按检测
                if (!isLongPressed) {
                    cancelLongPressDetection()
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 额外手指抬起
                // ✅ 方案 1：UP 只表示"手没了"，使用最后有效坐标，不参与位移计算
                val pid = pointerId.toInt()
                val x = lastValidRemoteX[pid]
                val y = lastValidRemoteY[pid]

                if (ENABLE_DEBUG_LOG) {
                    LogManager.e(LogTags.SCRCPY_CLIENT, "🔴 POINTER_UP: pid=$pointerId, remote=($x,$y) [终止信号，不参与位移]")
                }

                scrcpyClient.sendTouchEvent(
                    action = TouchAction.ACTION_POINTER_UP,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    screenWidth = remoteWidth,
                    screenHeight = remoteHeight,
                )
            }

            MotionEvent.ACTION_UP -> {
                // 最后一个手指抬起
                // ✅ 方案 1：UP 只表示"手没了"，使用最后有效坐标，不参与位移计算
                val pid = pointerId.toInt()
                val x = lastValidRemoteX[pid]
                val y = lastValidRemoteY[pid]

                if (ENABLE_DEBUG_LOG) {
                    LogManager.e(LogTags.SCRCPY_CLIENT, "🔴 UP: pid=$pointerId, remote=($x,$y) [终止信号，不参与位移]")
                }

                scrcpyClient.sendTouchEvent(
                    action = TouchAction.ACTION_UP,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    screenWidth = remoteWidth,
                    screenHeight = remoteHeight,
                )

                // 清理长按状态
                cancelLongPressDetection()
                isLongPressed = false
            }

            MotionEvent.ACTION_CANCEL -> {
                // 取消
                val x = scaleX(event.x)
                val y = scaleY(event.y)

                if (ENABLE_DEBUG_LOG) {
                    LogManager.e(LogTags.SCRCPY_CLIENT, "⚫ CANCEL: pid=$pointerId, remote=($x,$y)")
                }

                scrcpyClient.sendTouchEvent(
                    action = TouchAction.ACTION_CANCEL,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    screenWidth = remoteWidth,
                    screenHeight = remoteHeight,
                )

                cancelLongPressDetection()
                isLongPressed = false
            }
        }

        return true
    }

    /**
     * 启动长按检测
     */
    private fun startLongPressDetection(
        x: Int,
        y: Int,
        pointerId: Long,
    ) {
        cancelLongPressDetection()
        isLongPressed = false

        longPressJob =
            coroutineScope.launch {
                delay(longPressDelay)
                // 长按触发
                isLongPressed = true
                onLongPress(x, y, pointerId)
            }
    }

    /**
     * 取消长按检测
     */
    private fun cancelLongPressDetection() {
        longPressJob?.cancel()
        longPressJob = null
    }

    /**
     * 长按回调
     */
    private suspend fun onLongPress( // TODO
        x: Int,
        y: Int,
        pointerId: Long,
    ) {
        // 可以在这里添加长按反馈（如震动）
        // 长按事件已经通过 ACTION_DOWN 发送，这里可以添加额外的处理
    }

    /**
     * 将屏幕坐标转换为远程设备坐标（X 轴）
     */
    private fun scaleX(x: Float): Int = (x * remoteWidth / screenWidth).toInt().coerceIn(0, remoteWidth)

    /**
     * 将屏幕坐标转换为远程设备坐标（Y 轴）
     */
    private fun scaleY(y: Float): Int = (y * remoteHeight / screenHeight).toInt().coerceIn(0, remoteHeight)

    /**
     * 更新屏幕尺寸
     */
    fun updateScreenSize( // TODO
        width: Int,
        height: Int,
    ) {
        // 可以在这里更新 screenWidth 和 screenHeight
        // 但由于是 val，需要重新创建 TouchHandler 实例
    }
}
