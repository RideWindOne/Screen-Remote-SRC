package com.mobile.scrcpy.android.core.common.util

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 通用扩展函数集合
 * 
 * 包含项目中常用的扩展函数，提供便捷的工具方法
 */

// ============================================
// String 扩展
// ============================================

/**
 * 检查字符串是否为空或仅包含空白字符
 */
fun String?.isNullOrBlank(): Boolean = this == null || this.isBlank()

/**
 * 安全地转换字符串为整数，失败时返回默认值
 */
fun String?.toIntOrDefault(default: Int = 0): Int = this?.toIntOrNull() ?: default

/**
 * 安全地转换字符串为长整数，失败时返回默认值
 */
fun String?.toLongOrDefault(default: Long = 0L): Long = this?.toLongOrNull() ?: default

/**
 * 限制字符串长度，超出部分用省略号替代
 */
fun String.ellipsize(maxLength: Int, ellipsis: String = "..."): String {
    return if (this.length <= maxLength) {
        this
    } else {
        this.take(maxLength - ellipsis.length) + ellipsis
    }
}

// ============================================
// Collection 扩展
// ============================================

/**
 * 安全地获取列表元素，越界时返回 null
 */
fun <T> List<T>.getOrNull(index: Int): T? {
    return if (index in indices) this[index] else null
}

/**
 * 检查集合是否不为空
 */
fun <T> Collection<T>?.isNotNullOrEmpty(): Boolean = !this.isNullOrEmpty()

// ============================================
// Android 扩展
// ============================================

/**
 * 从 SurfaceTexture 创建 Surface
 */
fun SurfaceTexture.toSurface(): Surface {
    return Surface(this)
}

/**
 * 获取应用版本名称
 */
fun Context.getVersionName(): String {
    return try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
    } catch (e: Exception) {
        "Unknown"
    }
}

/**
 * 获取应用版本号
 */
fun Context.getVersionCode(): Long {
    return try {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    } catch (e: Exception) {
        0L
    }
}

// ============================================
// Compose 扩展
// ============================================

/**
 * 在 Composable 中获取 Context
 */
@Composable
fun rememberContext(): Context = LocalContext.current

// ============================================
// Number 扩展
// ============================================

/**
 * 将字节数转换为可读的文件大小字符串
 */
fun Long.toReadableFileSize(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = this.toDouble()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    
    return "%.2f %s".format(size, units[unitIndex])
}

/**
 * 将毫秒转换为可读的时间字符串
 */
fun Long.toReadableDuration(): String {
    val seconds = this / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        days > 0 -> "${days}天 ${hours % 24}小时"
        hours > 0 -> "${hours}小时 ${minutes % 60}分钟"
        minutes > 0 -> "${minutes}分钟 ${seconds % 60}秒"
        else -> "${seconds}秒"
    }
}

/**
 * 限制数值在指定范围内
 */
fun Int.coerceInRange(min: Int, max: Int): Int = this.coerceIn(min, max)
fun Float.coerceInRange(min: Float, max: Float): Float = this.coerceIn(min, max)
fun Double.coerceInRange(min: Double, max: Double): Double = this.coerceIn(min, max)

// ============================================
// Boolean 扩展
// ============================================

/**
 * 如果为 true 则执行 block
 */
inline fun Boolean.ifTrue(block: () -> Unit): Boolean {
    if (this) block()
    return this
}

/**
 * 如果为 false 则执行 block
 */
inline fun Boolean.ifFalse(block: () -> Unit): Boolean {
    if (!this) block()
    return this
}
