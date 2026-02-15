package com.mobile.scrcpy.android.core.common.constants

/**
 * 文件路径常量
 */
object FilePathConstants {
    /** 默认文件传输路径 */
    const val DEFAULT_FILE_TRANSFER_PATH = "/sdcard/Download"

    /** 设备截图默认目录 */
    const val DEVICE_SCREENSHOT_DIR = "/sdcard/Pictures"

    /** 快速选择路径列表 */
    val QUICK_SELECT_PATHS =
        listOf(
            "/sdcard/Download",
            "/sdcard/DCIM",
            "/sdcard/Documents",
            "/sdcard/Pictures",
            "/sdcard/Music",
            "/sdcard/Movies",
        )

    /** 应用图标缓存目录名 */
    const val APP_ICONS_DIR = "app_icons"
}
