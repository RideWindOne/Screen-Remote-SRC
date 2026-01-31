package com.mobile.scrcpy.android.feature.remote.components.floating

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.mobile.scrcpy.android.core.common.LogTags
import kotlin.math.hypot

/**
 * 球体移动控制器
 * 负责处理普通拖动和长按拖动时的球体位置更新
 */
internal class FloatingMenuBallMovement(
    private val context: Context,
    private val ballA: View,
    private val ballB: View,
    private val windowManager: WindowManager,
    private val paramsA: WindowManager.LayoutParams,
    private val paramsB: WindowManager.LayoutParams,
    private val state: FloatingMenuGestureState,
    private val edgeSnap: FloatingMenuEdgeSnap,
    private val menuManager: FloatingMenuViewManager,
) {
    private val density = context.resources.displayMetrics.density
    private val displayMetrics = context.resources.displayMetrics

    /**
     * 普通拖动：A 和 B 一起移动，菜单跟随，检测拖出贴边和到达边缘
     * 确保A和B始终保持中心对齐
     */
    fun moveAAndBTogether(event: MotionEvent) {
        val deltaX = event.rawX - state.lastRawX
        val deltaY = event.rawY - state.lastRawY

        // 检测拖出贴边
        edgeSnap.checkDragOut(deltaX, deltaY)

        // 计算A球的新中心位置
        val ballARadius = ballA.width / 2f
        val ballBRadius = ballB.width / 2f
        val currentACenterX = paramsA.x + ballARadius
        val currentACenterY = paramsA.y + ballARadius
        val newACenterX = currentACenterX + deltaX
        val newACenterY = currentACenterY + deltaY

        // 计算边界限制
        val minX = ballARadius.coerceAtLeast(ballBRadius)
        val maxX = displayMetrics.widthPixels - ballARadius.coerceAtLeast(ballBRadius)
        val minY = ballARadius.coerceAtLeast(ballBRadius)
        val maxY = displayMetrics.heightPixels - ballARadius.coerceAtLeast(ballBRadius)

        // 检测边缘触感
        edgeSnap.checkEdgeHaptic(newACenterX, newACenterY, ballARadius)

        // 限制A球中心位置
        val clampedACenterX = newACenterX.coerceIn(minX, maxX)
        val clampedACenterY = newACenterY.coerceIn(minY, maxY)

        // 计算A球的左上角位置
        val newAX = (clampedACenterX - ballARadius).toInt()
        val newAY = (clampedACenterY - ballARadius).toInt()

        // B球中心与A球中心对齐
        val newBX = (clampedACenterX - ballBRadius).toInt() // TODO
        val newBY = (clampedACenterY - ballBRadius).toInt() // TODO

        // 计算实际移动距离
        val finalDeltaX = newAX - paramsA.x
        var finalDeltaY = newAY - paramsA.y

        // 如果菜单显示，需要检查菜单边界
        finalDeltaY = menuManager.constrainMovementWithMenu(finalDeltaY, paramsA, ballA)

        // 重新计算位置（考虑菜单限制）
        val adjustedACenterX = (paramsA.x + ballARadius) + finalDeltaX
        val adjustedACenterY = (paramsA.y + ballARadius) + finalDeltaY
        val adjustedAX = (adjustedACenterX - ballARadius).toInt()
        val adjustedAY = (adjustedACenterY - ballARadius).toInt()
        val adjustedBX = (adjustedACenterX - ballBRadius).toInt()
        val adjustedBY = (adjustedACenterY - ballBRadius).toInt()

        // 应用位置
        paramsA.x = adjustedAX
        paramsA.y = adjustedAY
        paramsB.x = adjustedBX
        paramsB.y = adjustedBY

        windowManager.updateViewLayout(ballA, paramsA)
        windowManager.updateViewLayout(ballB, paramsB)

        // 更新 B 球中心位置
        state.ballBCenterX = paramsB.x + ballB.width / 2f
        state.ballBCenterY = paramsB.y + ballB.height / 2f

        // 菜单跟随移动
        menuManager.updateMenuPosition(finalDeltaX, finalDeltaY)

        state.lastRawX = event.rawX
        state.lastRawY = event.rawY

        Log.d(
            LogTags.FLOATING_CONTROLLER,
            "拖动 A+B: Δ($finalDeltaX, $finalDeltaY), A中心=(${paramsA.x + ballARadius}, ${paramsA.y + ballARadius}), B中心=(${state.ballBCenterX}, ${state.ballBCenterY})",
        )
    }

    /**
     * 长按拖动：A 球跟随手指移动，B 球不动，识别方向
     * 保持按下时手指相对于A球中心的偏移量，确保跟手
     */
    fun moveAAroundB(
        event: MotionEvent,
        detector: FloatingMenuGestureDetector,
    ) {
        val fingerX = event.rawX
        val fingerY = event.rawY

        // 计算小球中心应该在的位置（保持按下时的相对位置）
        val ballARadius = ballA.width / 2f
        val newACenterX = fingerX - state.downOffsetX
        val newACenterY = fingerY - state.downOffsetY

        // 计算边界限制
        val minX = ballARadius
        val maxX = displayMetrics.widthPixels - ballARadius
        val maxY = displayMetrics.heightPixels - ballARadius

        // 限制小球中心位置
        var clampedACenterX = newACenterX.coerceIn(minX, maxX)
        var clampedACenterY = newACenterY.coerceIn(ballARadius, maxY)

        // 计算相对于B球的偏移和距离
        val dx = clampedACenterX - state.ballBCenterX
        val dy = clampedACenterY - state.ballBCenterY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        // 处理扇形区域触感反馈
        detector.handleDirectionHaptic(dx, dy, distance)

        // 限制最大距离
        val maxDistancePx = MAX_DISTANCE_FROM_B_DP * density
        if (distance > maxDistancePx) {
            val scale = maxDistancePx / distance
            clampedACenterX = state.ballBCenterX + dx * scale
            clampedACenterY = state.ballBCenterY + dy * scale

            // 再次检查屏幕边界
            clampedACenterX = clampedACenterX.coerceIn(minX, maxX)
            clampedACenterY = clampedACenterY.coerceIn(ballARadius, maxY)
        }

        // 计算小球左上角位置
        val newAX = (clampedACenterX - ballARadius).toInt()
        val newAY = (clampedACenterY - ballARadius).toInt()

        // 应用位置
        paramsA.x = newAX
        paramsA.y = newAY
        windowManager.updateViewLayout(ballA, paramsA)

        // 计算最终距离
        val finalDx = clampedACenterX - state.ballBCenterX
        val finalDy = clampedACenterY - state.ballBCenterY
        val finalDistance = hypot(finalDx.toDouble(), finalDy.toDouble()).toFloat()

        state.lastRawX = event.rawX
        state.lastRawY = event.rawY

        Log.d(
            LogTags.FLOATING_CONTROLLER,
            "🔁 长按拖动: 手指=(${fingerX.toInt()}, ${fingerY.toInt()}), " +
                "偏移=(${state.downOffsetX.toInt()}, ${state.downOffsetY.toInt()}), " +
                "A中心=(${clampedACenterX.toInt()}, ${clampedACenterY.toInt()}), " +
                "A左上角=(${paramsA.x}, ${paramsA.y}), " +
                "距离B=${finalDistance.toInt()}px/${maxDistancePx.toInt()}px, " +
                "方向=${state.detectedDirection}",
        )
    }

    /**
     * 对齐球体：确保A球和B球中心对齐
     */
    fun alignBalls() {
        val ballARadius = ballA.width / 2f
        val ballBRadius = ballB.width / 2f
        val ballACenterX = paramsA.x + ballARadius
        val ballACenterY = paramsA.y + ballARadius

        // B球中心与A球中心对齐
        val ballBCenterX = ballACenterX
        val newBX = (ballBCenterX - ballBRadius).toInt()
        val newBY = (ballACenterY - ballBRadius).toInt()

        // 检查边界限制
        val minX = ballBRadius
        val maxX = displayMetrics.widthPixels - ballBRadius
        val maxY = displayMetrics.heightPixels - ballBRadius

        val clampedBCenterX = ballBCenterX.coerceIn(minX, maxX)
        val clampedBCenterY = ballACenterY.coerceIn(ballBRadius, maxY)

        if (clampedBCenterX != ballBCenterX || clampedBCenterY != ballACenterY) {
            // B球被边界限制，调整A球位置
            val adjustedAX = (clampedBCenterX - ballARadius).toInt()
            val adjustedAY = (clampedBCenterY - ballARadius).toInt()

            paramsA.x = adjustedAX
            paramsA.y = adjustedAY
            paramsB.x = (clampedBCenterX - ballBRadius).toInt()
            paramsB.y = (clampedBCenterY - ballBRadius).toInt()
        } else {
            paramsB.x = newBX
            paramsB.y = newBY
        }

        windowManager.updateViewLayout(ballA, paramsA)
        windowManager.updateViewLayout(ballB, paramsB)

        // 更新B球中心位置
        state.ballBCenterX = paramsB.x + ballB.width / 2f
        state.ballBCenterY = paramsB.y + ballB.height / 2f

        Log.d(
            LogTags.FLOATING_CONTROLLER_MSG,
            "对齐完成: A中心=(${paramsA.x + ballARadius}, ${paramsA.y + ballARadius}), " +
                "B中心=(${state.ballBCenterX}, $ballACenterY)",
        )
    }
}
