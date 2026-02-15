package com.mobile.scrcpy.android.feature.remote.widget.floating

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.mobile.scrcpy.android.core.common.LogTags

internal class FloatingMenuEdgeAnimator(
    internal val ballA: View,
    internal val ballB: View,
    private val windowManager: WindowManager,
    internal val paramsA: WindowManager.LayoutParams,
    internal val paramsB: WindowManager.LayoutParams,
    private val state: FloatingMenuGestureState,
    private val menuManager: FloatingMenuViewManager,
) {
    private var resetAnimator: ValueAnimator? = null

    fun animateToEdge(target: FloatingMenuSnapTarget) {
        val startAX = paramsA.x
        val startAY = paramsA.y
        val startBX = paramsB.x
        val startBY = paramsB.y
        val startMenuX = menuManager.getMenuX()
        val startMenuY = menuManager.getMenuY()

        resetAnimator?.cancel()
        resetAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200L
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float

                    paramsA.x = (startAX + (target.targetX - startAX) * fraction).toInt()
                    paramsA.y = (startAY + (target.targetY - startAY) * fraction).toInt()
                    paramsB.x = (startBX + (target.targetBX - startBX) * fraction).toInt()
                    paramsB.y = (startBY + (target.targetBY - startBY) * fraction).toInt()

                    state.ballBCenterX = paramsB.x + ballB.width / 2f
                    state.ballBCenterY = paramsB.y + ballB.height / 2f

                    try {
                        windowManager.updateViewLayout(ballA, paramsA)
                        windowManager.updateViewLayout(ballB, paramsB)
                        menuManager.animateMenuWithSnap(
                            startMenuX = startMenuX,
                            startMenuY = startMenuY,
                            deltaX = target.targetX - startAX,
                            deltaY = target.targetY - startAY,
                            fraction = fraction,
                        )
                    } catch (e: Exception) {
                        Log.e(LogTags.FLOATING_CONTROLLER, "贴边动画更新失败: ${e.message}")
                        cancel()
                    }
                }
                start()
            }
    }

    fun resetAPosition() {
        state.ballBCenterX = paramsB.x + ballB.width / 2f
        state.ballBCenterY = paramsB.y + ballB.height / 2f

        val targetX = (state.ballBCenterX - ballA.width / 2f).toInt()
        val targetY = (state.ballBCenterY - ballA.height / 2f).toInt()
        val targetACenterX = targetX + ballA.width / 2f
        val targetACenterY = targetY + ballA.height / 2f

        Log.d(
            LogTags.FLOATING_CONTROLLER_MSG,
            "🎯 开始归位: A从(${paramsA.x}, ${paramsA.y}) → ($targetX, $targetY), " +
                "B中心=(${state.ballBCenterX}, ${state.ballBCenterY}), 归位后A中心=($targetACenterX, $targetACenterY)",
        )

        val startX = paramsA.x
        val startY = paramsA.y

        resetAnimator?.cancel()
        resetAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = RESET_ANIMATION_DURATION_MS
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    paramsA.x = (startX + (targetX - startX) * fraction).toInt()
                    paramsA.y = (startY + (targetY - startY) * fraction).toInt()
                    try {
                        windowManager.updateViewLayout(ballA, paramsA)
                    } catch (e: Exception) {
                        Log.e(LogTags.FLOATING_CONTROLLER, "归位动画更新失败: ${e.message}")
                        cancel()
                    }
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            val finalACenterX = paramsA.x + ballA.width / 2f
                            val finalACenterY = paramsA.y + ballA.height / 2f
                            Log.d(
                                LogTags.FLOATING_CONTROLLER_MSG,
                                "归位完成: A左上角=(${paramsA.x}, ${paramsA.y}), " +
                                    "A中心=($finalACenterX, $finalACenterY), B中心=(${state.ballBCenterX}, ${state.ballBCenterY})",
                            )
                        }
                    },
                )
                start()
            }
    }

    fun cancelAnimation() {
        resetAnimator?.cancel()
        resetAnimator = null
    }

    fun cleanup() {
        cancelAnimation()
    }
}
