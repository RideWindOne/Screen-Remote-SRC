/*
 * UI API 兼容性工具
 * 
 * 从 ApiCompatHelper.kt 拆分而来
 * 职责：窗口、系统栏、触觉反馈、震动相关 API 兼容
 */

package com.mobile.scrcpy.android.core.common.util.compat

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibrationEffect
import android.view.HapticFeedbackConstants
import android.view.Window
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager

/**
 * 设置窗口适配系统栏
 */
@Suppress("DEPRECATION")
fun setDecorFitsSystemWindows(
    window: Window?,
    decorFitsSystemWindows: Boolean,
) {
    window ?: return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(decorFitsSystemWindows)
    } else {
        if (!decorFitsSystemWindows) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
    }
}

/**
 * 获取兼容的 Vibrator 实例
 */
fun getVibratorCompat(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

/**
 * 触发震动反馈（兼容不同 API 级别）
 */
fun vibrateCompat(
    vibrator: Vibrator?,
    type: String = "tick",
) {
    vibrator ?: return
    if (!vibrator.hasVibrator()) return

    try {
        if (Build.VERSION.SDK_INT >= 29) {
            val effect =
                when (type) {
                    "tick" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    "click" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    "heavy" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    "double" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                    else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                }
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val duration =
                when (type) {
                    "tick" -> 10L
                    "click" -> 20L
                    "heavy" -> 50L
                    "double" -> 30L
                    else -> 10L
                }
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val duration =
                when (type) {
                    "tick" -> 10L
                    "click" -> 20L
                    "heavy" -> 50L
                    "double" -> 30L
                    else -> 10L
                }
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    } catch (e: Exception) {
        LogManager.e(LogTags.APP, "震动失败: ${e.message}", e)
    }
}

/**
 * 获取兼容的触觉反馈常量
 */
fun getHapticFeedbackConstant(feedbackType: String): Int =
    when (feedbackType) {
        "reject" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        }

        "confirm" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
        }

        "gesture_start" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.GESTURE_START
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }

        "gesture_end" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.GESTURE_END
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }

        else -> HapticFeedbackConstants.CLOCK_TICK
    }

/**
 * 设置全屏模式（隐藏状态栏和导航栏）
 */
@Suppress("DEPRECATION")
fun setFullScreen(
    window: Window?,
    fullscreen: Boolean,
) {
    window ?: return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller = window.insetsController
        if (fullscreen) {
            controller?.hide(
                android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars(),
            )
            controller?.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(
                android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars(),
            )
        }
    } else {
        if (fullscreen) {
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        } else {
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}
