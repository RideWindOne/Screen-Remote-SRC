package com.mobile.scrcpy.android.infrastructure.media.audio

internal data class FlacStreamInfo(
    val rawStreamInfo: ByteArray,
    val minBlockSize: Int,
    val maxBlockSize: Int,
    val sampleRate: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
    val totalSamples: Long,
)

internal object FlacConfigParser {
    const val STREAM_INFO_SIZE = 34
    private val FLAC_STREAM_MARKER = byteArrayOf(0x66, 0x4C, 0x61, 0x43) // "fLaC"

    fun parseStreamInfo(data: ByteArray): FlacStreamInfo? {
        if (data.size < STREAM_INFO_SIZE) {
            return null
        }

        val minBlockSize = readUnsignedShort(data, 0)
        val maxBlockSize = readUnsignedShort(data, 2)

        val sampleRate =
            ((data[10].toInt() and 0xFF) shl 12) or
                ((data[11].toInt() and 0xFF) shl 4) or
                ((data[12].toInt() and 0xF0) shr 4)
        val channelCount = ((data[12].toInt() and 0x0E) shr 1) + 1
        val bitsPerSample = (((data[12].toInt() and 0x01) shl 4) or ((data[13].toInt() and 0xF0) shr 4)) + 1
        val totalSamples =
            ((data[13].toLong() and 0x0F) shl 32) or
                ((data[14].toLong() and 0xFF) shl 24) or
                ((data[15].toLong() and 0xFF) shl 16) or
                ((data[16].toLong() and 0xFF) shl 8) or
                (data[17].toLong() and 0xFF)

        if (sampleRate <= 0 || channelCount <= 0 || bitsPerSample <= 0) {
            return null
        }

        return FlacStreamInfo(
            rawStreamInfo = data.copyOf(),
            minBlockSize = minBlockSize,
            maxBlockSize = maxBlockSize,
            sampleRate = sampleRate,
            channelCount = channelCount,
            bitsPerSample = bitsPerSample,
            totalSamples = totalSamples,
        )
    }

    fun buildInitializationData(data: ByteArray): ByteArray {
        val metadataHeader =
            byteArrayOf(
                0x80.toByte(), // last-metadata-block = 1, block type = STREAMINFO
                ((data.size shr 16) and 0xFF).toByte(),
                ((data.size shr 8) and 0xFF).toByte(),
                (data.size and 0xFF).toByte(),
            )

        return FLAC_STREAM_MARKER + metadataHeader + data
    }

    private fun readUnsignedShort(
        data: ByteArray,
        offset: Int,
    ): Int = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
}
