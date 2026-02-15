package com.mobile.scrcpy.android.feature.remote.widget.floating

import android.util.Log
import android.view.HapticFeedbackConstants
import com.mobile.scrcpy.android.core.common.LogTags

internal class FloatingMenuLongPressScheduler(
    private val state: FloatingMenuGestureState,
    private val hapticEnabled: Boolean,
) {
    fun schedule() {
        val longPressRunnable =
            Runnable {
                if (!state.hasMoved) {
                    state.canEnterLongPress = true
                    if (hapticEnabled) {
                        performHapticFeedbackCompat(HapticFeedbackConstants.LONG_PRESS)
                    }
                    Log.d(LogTags.FLOATING_CONTROLLER, "⏱️ 按住300ms未移动，可以进入长按模式")
                }
            }
        val reservedFunctionRunnable =
            Runnable {
                if (!state.hasMoved && state.canEnterLongPress) {
                    state.isSecondStageLongPress = true
                    if (hapticEnabled) {
                        performHapticFeedbackCompat(HapticFeedbackConstants.LONG_PRESS)
                    }
                    Log.d(LogTags.FLOATING_CONTROLLER, "⏱️ 按住800ms未移动，预留功能触发")
                }
            }

        state.longPressRunnable = longPressRunnable
        state.reservedFunctionRunnable = reservedFunctionRunnable
        state.longPressHandler?.postDelayed(longPressRunnable, LONG_PRESS_TIME_MS)
        state.reservedFunctionHandler?.postDelayed(reservedFunctionRunnable, RESERVED_FUNCTION_TIME_MS)
    }
}
