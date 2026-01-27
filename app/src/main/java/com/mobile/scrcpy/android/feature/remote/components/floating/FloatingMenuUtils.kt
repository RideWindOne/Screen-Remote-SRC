package com.mobile.scrcpy.android.feature.remote.components.floating

import android.content.Context
import android.util.Log
import com.mobile.scrcpy.android.core.common.LogTags


/**
 * 屏幕旋转时重新定位小球
 * 旋转后屏幕宽高互换，原坐标可能超出范围导致小球不可见
 * 策略：等待 1 秒后移动到默认位置
 * - 横屏：右侧居中
 * - 竖屏：底部居中
 */
internal fun repositionBallsOnRotation(
    context: Context,
    reference: BallSystemReference
) {
    val (ballA, ballB, _, _) = reference
    val displayMetrics = context.resources.displayMetrics

    // 判断当前屏幕方向
    val isLandscape = displayMetrics.widthPixels > displayMetrics.heightPixels

    // 获取当前小球位置
    val paramsA = ballA.layoutParams as android.view.WindowManager.LayoutParams
    val paramsB = ballB.layoutParams as android.view.WindowManager.LayoutParams

    Log.d(
        LogTags.FLOATING_CONTROLLER_MSG,
        "🔄 屏幕旋转检测 (${if (isLandscape) "横屏" else "竖屏"})，当前小球位置: A=(${paramsA.x}, ${paramsA.y}), B=(${paramsB.x}, ${paramsB.y})")

    // TODO: 屏幕旋转，增强用户体验，让小球移动到底部/右侧
    /*
    val density = context.resources.displayMetrics.density
    val ballACenterOffsetX = (BALL_B_SIZE_DP - BALL_A_SIZE_DP) * density / 2f
    val ballACenterOffsetY = (BALL_B_SIZE_DP - BALL_A_SIZE_DP) * density / 2f

    Log.d(LogTags.FLOATING_CONTROLLER_MSG,
        "🔄 屏幕旋转检测 (${if (isLandscape) "横屏" else "竖屏"})，等待 1 秒后重新定位小球")

    // 等待 1 秒后再移动小球
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        // 计算目标位置（屏幕内）
        val targetBX: Float
        val targetBY: Float

        if (isLandscape) {
            // 横屏：右侧居中，距离右边缘 20dp
            targetBX = displayMetrics.widthPixels - 20 * density - BALL_B_SIZE_DP * density
            targetBY = (displayMetrics.heightPixels - BALL_B_SIZE_DP * density) / 2f
        } else {
            // 竖屏：底部居中，距离底部 85dp
            targetBX = (displayMetrics.widthPixels - BALL_B_SIZE_DP * density) / 2f
            targetBY = displayMetrics.heightPixels - 85 * density - BALL_B_SIZE_DP * density
        }

        val targetAX = targetBX + ballACenterOffsetX
        val targetAY = targetBY + ballACenterOffsetY

        Log.d(LogTags.FLOATING_CONTROLLER_MSG,
            "🔄 开始重定位: 从(${paramsB.x}, ${paramsB.y}) → (${targetBX.toInt()}, ${targetBY.toInt()}) (${if (isLandscape) "横屏右侧" else "竖屏底部"})")

        // 平滑移动到目标位置
        val startAX = paramsA.x
        val startAY = paramsA.y
        val startBX = paramsB.x
        val startBY = paramsB.y

        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350L
            interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float

                paramsA.x = (startAX + (targetAX - startAX) * fraction).toInt()
                paramsA.y = (startAY + (targetAY - startAY) * fraction).toInt()
                paramsB.x = (startBX + (targetBX - startBX) * fraction).toInt()
                paramsB.y = (startBY + (targetBY - startBY) * fraction).toInt()

                try {
                    windowManager.updateViewLayout(ballA, paramsA)
                    windowManager.updateViewLayout(ballB, paramsB)
                } catch (e: Exception) {
                    Log.e(LogTags.FLOATING_CONTROLLER, "重定位动画失败: ${e.message}")
                    cancel()
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    Log.d(LogTags.FLOATING_CONTROLLER_MSG,
                        "✅ 重定位完成: A=(${paramsA.x}, ${paramsA.y}), B=(${paramsB.x}, ${paramsB.y})")
                }
            })
            start()
        }
    }, 1000L) // 延迟 1 秒
    */
}
