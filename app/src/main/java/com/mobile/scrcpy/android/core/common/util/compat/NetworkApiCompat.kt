/*
 * 网络 API 兼容性工具
 * 
 * 从 ApiCompatHelper.kt 拆分而来
 * 职责：网络、广播接收器相关 API 兼容
 */

package com.mobile.scrcpy.android.core.common.util.compat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build

/**
 * 注册广播接收器（兼容不同 API 级别）
 *
 * Android 13 (API 33) 引入了 RECEIVER_NOT_EXPORTED 标志
 */
fun registerReceiverCompat(
    context: Context,
    receiver: BroadcastReceiver,
    filter: IntentFilter,
    exported: Boolean = false,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val flags =
            if (exported) {
                Context.RECEIVER_EXPORTED
            } else {
                Context.RECEIVER_NOT_EXPORTED
            }
        context.registerReceiver(receiver, filter, flags)
    } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(receiver, filter)
    }
}
