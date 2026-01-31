/*
 * 存储 API 兼容性工具
 * 
 * 从 ApiCompatHelper.kt 拆分而来
 * 职责：USB、Intent、Parcelable 相关 API 兼容
 */

package com.mobile.scrcpy.android.core.common.util.compat

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Parcelable

/**
 * 获取 USB 设备序列号（兼容不同 API 级别）
 */
fun getUsbDeviceSerialNumber(device: UsbDevice): String? =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            device.serialNumber
        } else {
            null
        }
    } catch (e: SecurityException) {
        null
    }

/**
 * 从 Intent 中获取 Parcelable 对象（兼容不同 API 级别）
 */
fun <T : Parcelable> getParcelableExtraCompat(
    intent: Intent,
    key: String,
    clazz: Class<T>,
): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(key, clazz)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(key)
    }
