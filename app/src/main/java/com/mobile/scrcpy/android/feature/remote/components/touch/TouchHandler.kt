package com.mobile.scrcpy.android.feature.remote.components.touch

import android.view.MotionEvent
import com.mobile.scrcpy.android.infrastructure.scrcpy.client.feature.scrcpy.ScrcpyClient
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.TouchAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 触摸事件处理器
 * 支持多指触摸和长按检测
 */
class TouchHandler(
    private val scrcpyClient: ScrcpyClient,
    private val coroutineScope: CoroutineScope,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val remoteWidth: Int,
    private val remoteHeight: Int
) {
    private var longPressJob: Job? = null
    private var isLongPressed = false
    private val longPressDelay = 500L // 长按延迟 500ms

    /**
     * 处理触摸事件
     */
    suspend fun handleTouchEvent(event: MotionEvent): Boolean {
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex).toLong()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 第一个手指按下
                val x = scaleX(event.x)
                val y = scaleY(event.y)
                scrcpyClient.sendTouchEvent(
                    action = TouchAction.ACTION_DOWN,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    screenWidth = remoteWidth,
                    screenHeight = remoteHeight
                )
                
                // 启动长按检测
                startLongPressDetection(x, y, pointerId)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 额外手指按下（多指触摸）
                val x = scaleX(event.getX(actionIndex))
                val y = scaleY(event.getY(actionIndex))
                scrcpyClient.sendTouchEvent(
                    action = TouchAction.ACTION_POINTER_DOWN,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    screenWidth = remoteWidth,
                    screenHeight = remoteHeight
                )
                
                // 取消长按检测（多指操作）
                cancelLongPressDetection()
            }

            MotionEvent.ACTION_MOVE -> {
                // 所有手指移动
                for (i in 0 until event.pointerCount) {
                    val currentPointerId = event.getPointerId(i).toLong()
                    val x = scaleX(event.getX(i))
                    val y = scaleY(event.getY(i))
                    scrcpyClient.sendTouchEvent(
                        action = TouchAction.ACTION_MOVE,
                        pointerId = currentPointerId,
                        x = x,
                        y = y,
                        screenWidth = remoteWidth,
                        screenHeight = remoteHeight
                    )
                }
                
                // 移动时取消长按检测
                if (!isLongPressed) {
                    cancelLongPressDetection()
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 额外手指抬起
                val x = scaleX(event.getX(actionIndex))
                val y = scaleY(event.getY(actionIndex))
                scrcpyClient.sendTouchEvent(
                    action = TouchAction.ACTION_POINTER_UP,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    screenWidth = remoteWidth,
                    screenHeight = remoteHeight
                )
            }

            MotionEvent.ACTION_UP -> {
                // 最后一个手指抬起
                val x = scaleX(event.x)
                val y = scaleY(event.y)
                scrcpyClient.sendTouchEvent(
                    action = TouchAction.ACTION_UP,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    screenWidth = remoteWidth,
                    screenHeight = remoteHeight
                )
                
                // 清理长按状态
                cancelLongPressDetection()
                isLongPressed = false
            }

            MotionEvent.ACTION_CANCEL -> {
                // 取消
                val x = scaleX(event.x)
                val y = scaleY(event.y)
                scrcpyClient.sendTouchEvent(
                    action = TouchAction.ACTION_CANCEL,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    screenWidth = remoteWidth,
                    screenHeight = remoteHeight
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
    private fun startLongPressDetection(x: Int, y: Int, pointerId: Long) {
        cancelLongPressDetection()
        isLongPressed = false
        
        longPressJob = coroutineScope.launch {
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
    private suspend fun onLongPress(x: Int, y: Int, pointerId: Long) {
        // 可以在这里添加长按反馈（如震动）
        // 长按事件已经通过 ACTION_DOWN 发送，这里可以添加额外的处理
    }

    /**
     * 将屏幕坐标转换为远程设备坐标（X 轴）
     */
    private fun scaleX(x: Float): Int {
        return (x * remoteWidth / screenWidth).toInt().coerceIn(0, remoteWidth)
    }

    /**
     * 将屏幕坐标转换为远程设备坐标（Y 轴）
     */
    private fun scaleY(y: Float): Int {
        return (y * remoteHeight / screenHeight).toInt().coerceIn(0, remoteHeight)
    }

    /**
     * 更新屏幕尺寸
     */
    fun updateScreenSize(width: Int, height: Int) {
        // 可以在这里更新 screenWidth 和 screenHeight
        // 但由于是 val，需要重新创建 TouchHandler 实例
    }
}
