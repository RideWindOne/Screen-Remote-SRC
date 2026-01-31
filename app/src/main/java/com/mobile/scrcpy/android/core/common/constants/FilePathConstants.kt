package com.mobile.scrcpy.android.core.common.constants

/**
 * 文件路径常量
 */
object FilePathConstants {
    /** 默认文件传输路径 */
    const val DEFAULT_FILE_TRANSFER_PATH = "/sdcard/Download"

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
}
