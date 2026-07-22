package com.screen.remote.android.infrastructure.media.audio

internal data class FlacStreamInfo(
    val rawStreamInfo: ByteArray,
    val minBlockSize: Int,
    val maxBlockSize: Int,
    val sampleRate: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
    val totalSamples: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FlacStreamInfo) return false

        return rawStreamInfo.contentEquals(other.rawStreamInfo) &&
            minBlockSize == other.minBlockSize &&
            maxBlockSize == other.maxBlockSize &&
            sampleRate == other.sampleRate &&
            channelCount == other.channelCount &&
            bitsPerSample == other.bitsPerSample &&
            totalSamples == other.totalSamples
    }

    override fun hashCode(): Int {
        var result = rawStreamInfo.contentHashCode()
        result = 31 * result + minBlockSize
        result = 31 * result + maxBlockSize
        result = 31 * result + sampleRate
        result = 31 * result + channelCount
        result = 31 * result + bitsPerSample
        result = 31 * result + totalSamples.hashCode()
        return result
    }
}

internal object FlacConfigParser {
    const val STREAM_INFO_SIZE = 34
    private val FLAC_STREAM_MARKER = byteArrayOf(0x66, 0x4C, 0x61, 0x43) // "fLaC"

    fun parseStreamInfo(data: ByteArray): FlacStreamInfo? {
        val streamInfo = extractStreamInfo(data) ?: return null

        val minBlockSize = readUnsignedShort(streamInfo, 0)
        val maxBlockSize = readUnsignedShort(streamInfo, 2)

        val sampleRate =
            ((streamInfo[10].toInt() and 0xFF) shl 12) or
                ((streamInfo[11].toInt() and 0xFF) shl 4) or
                ((streamInfo[12].toInt() and 0xF0) shr 4)
        val channelCount = ((streamInfo[12].toInt() and 0x0E) shr 1) + 1
        val bitsPerSample = (((streamInfo[12].toInt() and 0x01) shl 4) or ((streamInfo[13].toInt() and 0xF0) shr 4)) + 1
        val totalSamples =
            ((streamInfo[13].toLong() and 0x0F) shl 32) or
                ((streamInfo[14].toLong() and 0xFF) shl 24) or
                ((streamInfo[15].toLong() and 0xFF) shl 16) or
                ((streamInfo[16].toLong() and 0xFF) shl 8) or
                (streamInfo[17].toLong() and 0xFF)

        if (sampleRate <= 0) {
            return null
        }

        return FlacStreamInfo(
            rawStreamInfo = streamInfo,
            minBlockSize = minBlockSize,
            maxBlockSize = maxBlockSize,
            sampleRate = sampleRate,
            channelCount = channelCount,
            bitsPerSample = bitsPerSample,
            totalSamples = totalSamples,
        )
    }

    private fun extractStreamInfo(data: ByteArray): ByteArray? {
        if (data.size == STREAM_INFO_SIZE) return data.copyOf()
        if (data.size >= 8 + STREAM_INFO_SIZE && data.copyOfRange(0, 4).contentEquals(FLAC_STREAM_MARKER)) {
            val blockType = data[4].toInt() and 0x7F
            val blockLength =
                ((data[5].toInt() and 0xFF) shl 16) or
                    ((data[6].toInt() and 0xFF) shl 8) or
                    (data[7].toInt() and 0xFF)
            if (blockType == 0 && blockLength == STREAM_INFO_SIZE) {
                return data.copyOfRange(8, 8 + STREAM_INFO_SIZE)
            }
        }
        if (data.size >= 4 + STREAM_INFO_SIZE) {
            val blockType = data[0].toInt() and 0x7F
            val blockLength =
                ((data[1].toInt() and 0xFF) shl 16) or
                    ((data[2].toInt() and 0xFF) shl 8) or
                    (data[3].toInt() and 0xFF)
            if (blockType == 0 && blockLength == STREAM_INFO_SIZE) {
                return data.copyOfRange(4, 4 + STREAM_INFO_SIZE)
            }
        }
        return null
    }

    fun buildInitializationData(data: ByteArray): ByteArray {
        require(data.size == STREAM_INFO_SIZE) { "FLAC STREAMINFO must be exactly $STREAM_INFO_SIZE bytes" }
        val metadataHeader =
            byteArrayOf(
                0x80.toByte(), // last-metadata-block = 1, block type = STREAMINFO
                0x00,
                0x00,
                STREAM_INFO_SIZE.toByte(),
            )

        return FLAC_STREAM_MARKER + metadataHeader + data
    }

    private fun readUnsignedShort(
        data: ByteArray,
        offset: Int,
    ): Int = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
}
