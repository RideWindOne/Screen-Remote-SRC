/*
 * USB ADB 常量定义
 *
 * 集中管理 USB ADB 相关的常量，避免重复定义
 * 参考实现：Easycontrol
 */

package com.mobile.scrcpy.android.infrastructure.adb.usb

/**
 * USB ADB 常量
 */
object UsbConstants {
    // ============ ADB 接口标识 ============

    /**
     * ADB 接口类（Vendor Specific）
     */
    const val ADB_CLASS = 0xFF // 255

    /**
     * ADB 接口子类
     */
    const val ADB_SUBCLASS = 0x42 // 66

    /**
     * ADB 接口协议
     */
    const val ADB_PROTOCOL = 0x01 // 1

    // ============ USB 传输参数 ============

    /**
     * USB 传输超时（毫秒）
     */
    const val USB_TIMEOUT = 5000

    /**
     * USB 批量传输最大包大小
     */
    const val USB_MAX_PACKET_SIZE = 16384
}
