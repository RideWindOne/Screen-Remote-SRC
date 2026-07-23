package com.screen.remote.android.infrastructure.media.video

internal object VideoMemoryPolicy {
    private const val MIN_BOUNDED_ALLOCATION_BYTES = 4 * 1024 * 1024
    private const val MAX_PACKET_BYTES = 32 * 1024 * 1024
    private const val MAX_BOOTSTRAP_CACHE_BYTES = 16 * 1024 * 1024

    fun maxPacketBytes(maxHeapBytes: Long = Runtime.getRuntime().maxMemory()): Int =
        boundedAllocation(maxHeapBytes, MAX_PACKET_BYTES)

    fun maxBootstrapCacheBytes(maxHeapBytes: Long = Runtime.getRuntime().maxMemory()): Int =
        boundedAllocation(maxHeapBytes, MAX_BOOTSTRAP_CACHE_BYTES)

    private fun boundedAllocation(
        maxHeapBytes: Long,
        upperBoundBytes: Int,
    ): Int {
        val heapShare = (maxHeapBytes.coerceAtLeast(0L) / 8L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return heapShare.coerceIn(
            MIN_BOUNDED_ALLOCATION_BYTES.coerceAtMost(upperBoundBytes),
            upperBoundBytes,
        )
    }
}
