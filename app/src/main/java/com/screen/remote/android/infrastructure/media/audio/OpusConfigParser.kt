package com.screen.remote.android.infrastructure.media.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class OpusConfig(
    val header: ByteArray,
    val version: Int,
    val channelCount: Int,
    val preSkipSamples: Int,
    val originalSampleRate: Int,
    val outputGain: Int,
    val channelMappingFamily: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpusConfig) return false

        return header.contentEquals(other.header) &&
            version == other.version &&
            channelCount == other.channelCount &&
            preSkipSamples == other.preSkipSamples &&
            originalSampleRate == other.originalSampleRate &&
            outputGain == other.outputGain &&
            channelMappingFamily == other.channelMappingFamily
    }

    override fun hashCode(): Int {
        var result = header.contentHashCode()
        result = 31 * result + version
        result = 31 * result + channelCount
        result = 31 * result + preSkipSamples
        result = 31 * result + originalSampleRate
        result = 31 * result + outputGain
        result = 31 * result + channelMappingFamily
        return result
    }
}

internal object OpusConfigParser {
    const val OPUS_HEADER_SIZE = 19
    const val OPUS_OUTPUT_SAMPLE_RATE = 48_000
    const val DEFAULT_SEEK_PRE_ROLL_SAMPLES = 3_840
    private const val OPUS_HEAD_MAGIC = "OpusHead"

    fun isOpusHead(data: ByteArray): Boolean =
        data.size >= OPUS_HEADER_SIZE &&
            String(data, 0, OPUS_HEAD_MAGIC.length, Charsets.US_ASCII) == OPUS_HEAD_MAGIC &&
            hasCompleteChannelMapping(data)

    fun parse(data: ByteArray): OpusConfig? {
        if (!isOpusHead(data)) {
            return null
        }

        val version = data[8].toInt() and 0xFF
        val channelCount = data[9].toInt() and 0xFF
        if (version > 15 || channelCount <= 0 || !hasValidChannelMapping(data, channelCount)) {
            return null
        }

        return OpusConfig(
            header = data.copyOf(),
            version = version,
            channelCount = channelCount,
            preSkipSamples = readPreSkipSamples(data),
            originalSampleRate = readOriginalSampleRate(data),
            outputGain = readOutputGain(data),
            channelMappingFamily = data[18].toInt() and 0xFF,
        )
    }

    fun buildInitializationData(config: OpusConfig): List<ByteArray> =
        listOf(
            config.header.copyOf(),
            nativeOrderLongToByteArray(samplesToNanoseconds(config.preSkipSamples.toLong())),
            nativeOrderLongToByteArray(samplesToNanoseconds(DEFAULT_SEEK_PRE_ROLL_SAMPLES.toLong())),
        )

    private fun hasCompleteChannelMapping(data: ByteArray): Boolean {
        if (data.size < OPUS_HEADER_SIZE) return false
        val channelCount = data[9].toInt() and 0xFF
        val mappingFamily = data[18].toInt() and 0xFF
        return channelCount > 0 && (mappingFamily == 0 || data.size >= 21 + channelCount)
    }

    private fun hasValidChannelMapping(
        data: ByteArray,
        channelCount: Int,
    ): Boolean {
        val mappingFamily = data[18].toInt() and 0xFF
        if (mappingFamily == 0) return channelCount in 1..2

        val streamCount = data[19].toInt() and 0xFF
        val coupledStreamCount = data[20].toInt() and 0xFF
        if (streamCount <= 0 || coupledStreamCount > streamCount || streamCount + coupledStreamCount != channelCount) {
            return false
        }
        val codedChannelCount = streamCount + coupledStreamCount
        return (0 until channelCount).all { index ->
            val mapping = data[21 + index].toInt() and 0xFF
            mapping == 255 || mapping < codedChannelCount
        }
    }

    private fun samplesToNanoseconds(samples: Long): Long = samples * 1_000_000_000L / OPUS_OUTPUT_SAMPLE_RATE

    private fun nativeOrderLongToByteArray(value: Long): ByteArray =
        ByteBuffer
            .allocate(Long.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .putLong(value)
            .array()

    private fun readPreSkipSamples(data: ByteArray): Int =
        (data[10].toInt() and 0xFF) or ((data[11].toInt() and 0xFF) shl 8)

    private fun readOutputGain(data: ByteArray): Int =
        ((data[16].toInt() and 0xFF) or ((data[17].toInt() and 0xFF) shl 8)).toShort().toInt()

    private fun readOriginalSampleRate(data: ByteArray): Int =
        (data[12].toInt() and 0xFF) or
            ((data[13].toInt() and 0xFF) shl 8) or
            ((data[14].toInt() and 0xFF) shl 16) or
            ((data[15].toInt() and 0xFF) shl 24)
}
