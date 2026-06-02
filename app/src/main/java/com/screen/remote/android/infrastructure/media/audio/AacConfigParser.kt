package com.screen.remote.android.infrastructure.media.audio

internal data class AacConfig(
    val audioObjectType: Int,
    val sampleRate: Int,
    val channelCount: Int,
)

/** Minimal MPEG-4 AudioSpecificConfig parser used to configure arbitrary AAC decoders correctly. */
internal object AacConfigParser {
    private val sampleRates =
        intArrayOf(96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000, 22_050, 16_000, 12_000, 11_025, 8_000, 7_350)

    fun parse(data: ByteArray): AacConfig? =
        runCatching {
            val bits = BitReader(data)
            var objectType = bits.read(5)
            if (objectType == 31) objectType = 32 + bits.read(6)
            if (objectType <= 0) return null
            val frequencyIndex = bits.read(4)
            val sampleRate = if (frequencyIndex == 15) bits.read(24) else sampleRates.getOrNull(frequencyIndex) ?: return null
            val channelConfiguration = bits.read(4)
            if (sampleRate <= 0 || channelConfiguration !in 1..7) return null
            val channelCount = if (channelConfiguration == 7) 8 else channelConfiguration
            AacConfig(objectType, sampleRate, channelCount)
        }.getOrNull()

    private class BitReader(private val data: ByteArray) {
        private var bitOffset = 0

        fun read(count: Int): Int {
            require(count in 1..24 && bitOffset + count <= data.size * 8)
            var value = 0
            repeat(count) {
                val byte = data[bitOffset / 8].toInt() and 0xFF
                value = (value shl 1) or ((byte shr (7 - bitOffset % 8)) and 1)
                bitOffset++
            }
            return value
        }
    }
}
