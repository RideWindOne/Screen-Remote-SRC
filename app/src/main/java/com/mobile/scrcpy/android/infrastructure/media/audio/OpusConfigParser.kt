package com.mobile.scrcpy.android.infrastructure.media.audio

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
)

internal object OpusConfigParser {
    const val OPUS_HEADER_SIZE = 19
    const val OPUS_OUTPUT_SAMPLE_RATE = 48_000
    const val DEFAULT_SEEK_PRE_ROLL_SAMPLES = 3_840
    private const val OPUS_HEAD_MAGIC = "OpusHead"

    fun isOpusHead(data: ByteArray): Boolean =
        data.size == OPUS_HEADER_SIZE &&
            String(data, 0, OPUS_HEAD_MAGIC.length, Charsets.US_ASCII) == OPUS_HEAD_MAGIC

    fun parse(data: ByteArray): OpusConfig? {
        if (!isOpusHead(data)) {
            return null
        }

        val version = data[8].toInt() and 0xFF
        val channelCount = data[9].toInt() and 0xFF
        if (channelCount <= 0) {
            return null
        }

        return OpusConfig(
            header = data.copyOf(),
            version = version,
            channelCount = channelCount,
            preSkipSamples = readUnsignedShortLittleEndian(data, offset = 10),
            originalSampleRate = readIntLittleEndian(data, offset = 12),
            outputGain = readShortLittleEndian(data, offset = 16).toInt(),
            channelMappingFamily = data[18].toInt() and 0xFF,
        )
    }

    fun buildInitializationData(config: OpusConfig): List<ByteArray> =
        listOf(
            config.header.copyOf(),
            nativeOrderLongToByteArray(samplesToNanoseconds(config.preSkipSamples.toLong())),
            nativeOrderLongToByteArray(samplesToNanoseconds(DEFAULT_SEEK_PRE_ROLL_SAMPLES.toLong())),
        )

    private fun samplesToNanoseconds(samples: Long): Long = samples * 1_000_000_000L / OPUS_OUTPUT_SAMPLE_RATE

    private fun nativeOrderLongToByteArray(value: Long): ByteArray =
        ByteBuffer
            .allocate(Long.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .putLong(value)
            .array()

    private fun readUnsignedShortLittleEndian(
        data: ByteArray,
        offset: Int,
    ): Int = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readShortLittleEndian(
        data: ByteArray,
        offset: Int,
    ): Short = readUnsignedShortLittleEndian(data, offset).toShort()

    private fun readIntLittleEndian(
        data: ByteArray,
        offset: Int,
    ): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
}
