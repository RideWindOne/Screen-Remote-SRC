package com.mobile.scrcpy.android.feature.remote.components.floating

import android.content.Context
import android.os.Vibrator
import android.util.Log
import android.view.HapticFeedbackConstants
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper

/**
 * 触感反馈工具类
 * 使用 Vibrator 服务和 VibrationEffect 新 API 确保触感稳定触发
 */
internal object HapticHelper {
    private var vibrator: Vibrator? = null

    /**
     * 初始化触感反馈（仅在开关开启时调用）
     */
    fun init(context: Context) {
        vibrator = ApiCompatHelper.getVibratorCompat(context)

        if (vibrator?.hasVibrator() == true) {
            Log.d(LogTags.FLOATING_CONTROLLER, "Vibrator 初始化成功")
        } else {
            Log.w(LogTags.FLOATING_CONTROLLER, "设备不支持触感")
        }
    }

    /**
     * 触发触感反馈
     * @param type 触感类型：tick(轻点), click(点击), heavy(重击)
     */
    fun vibrate(type: String = "tick") {
        ApiCompatHelper.vibrateCompat(vibrator, type)
    }
}

/**
 * 触感反馈辅助函数
 * 使用 Vibrator 服务替代 View.performHapticFeedback，确保触感稳定触发
 */
internal fun performHapticFeedbackCompat(feedbackConstant: Int) {
    // 映射 HapticFeedbackConstants 到触感类型
    val rejectConstant = ApiCompatHelper.getHapticFeedbackConstant("reject")
    val type =
        when (feedbackConstant) {
            HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.VIRTUAL_KEY,
            -> "tick"

            HapticFeedbackConstants.CONTEXT_CLICK -> "click"

            HapticFeedbackConstants.LONG_PRESS,
            rejectConstant,
            -> "heavy"

            else -> "tick"
        }
    HapticHelper.vibrate(type)
}
