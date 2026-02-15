/*
 * JVM API 兼容性工具
 *
 * 职责：InputStream、集合默认方法等 Java/Kotlin 运行时兼容
 */

package com.mobile.scrcpy.android.core.common.util.compat

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 从输入流中读取至多 [maxBytes] 个字节，兼容缺少 InputStream.readNBytes() 的环境。
 */
fun readAtMostBytesCompat(
    inputStream: InputStream,
    maxBytes: Int,
): ByteArray {
    require(maxBytes >= 0) { "maxBytes must be >= 0" }
    if (maxBytes == 0) return ByteArray(0)

    val output = ByteArrayOutputStream(maxBytes.coerceAtMost(DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(minOf(DEFAULT_BUFFER_SIZE, maxBytes))
    var remaining = maxBytes

    while (remaining > 0) {
        val read = inputStream.read(buffer, 0, minOf(buffer.size, remaining))
        if (read <= 0) break
        output.write(buffer, 0, read)
        remaining -= read
    }

    return output.toByteArray()
}

/**
 * 兼容不依赖 Map.putIfAbsent() 默认实现的写入方式。
 */
fun <K, V> MutableMap<K, V>.putIfAbsentCompat(
    key: K,
    value: V,
): V? {
    val existing = this[key]
    if (existing != null || containsKey(key)) {
        return existing
    }

    put(key, value)
    return null
}
