package com.screen.remote.android.infrastructure.media.video

/**
 * VideoNalParser - NAL 单元解析器
 * 负责 H.264/H.265 NAL 单元的提取和 Frame Meta 解析
 */
class VideoNalParser {
    companion object {
        // H.264 NAL 类型
        const val H264_NAL_SPS = 7
        const val H264_NAL_PPS = 8
        const val H264_NAL_IDR = 5

        // H.265 NAL 类型
        const val H265_NAL_VPS = 32
        const val H265_NAL_SPS = 33
        const val H265_NAL_PPS = 34
        const val H265_NAL_IDR_W_RADL = 19

    }

    /**
     * Splits one scrcpy codec-config packet into its Annex-B NAL units.
     */
    fun extractNalUnits(data: ByteArray): List<ByteArray> {
        if (data.size < 3) return emptyList()
        val starts = mutableListOf<StartCode>()
        var searchFrom = 0
        while (searchFrom < data.size - 2) {
            val start = findStartCode(data, searchFrom) ?: break
            starts += start
            searchFrom = start.index + start.length
        }
        if (starts.isEmpty()) return emptyList()
        return starts.mapIndexed { index, start ->
            val end = starts.getOrNull(index + 1)?.index ?: data.size
            data.copyOfRange(start.index, end)
        }
    }

    /**
     * 检查是否为 NAL 起始码
     */
    fun isNalStartCode(data: ByteArray): Boolean =
        startCodeLength(data) > 0

    /**
     * 获取 H.264 NAL 类型
     */
    fun getH264NalType(nalUnit: ByteArray): Int {
        val offset = startCodeLength(nalUnit)
        return if (offset > 0 && nalUnit.size > offset) nalUnit[offset].toInt() and 0x1F else -1
    }

    /**
     * 获取 H.265 NAL 类型
     */
    fun getH265NalType(nalUnit: ByteArray): Int {
        val offset = startCodeLength(nalUnit)
        return if (offset > 0 && nalUnit.size > offset) (nalUnit[offset].toInt() and 0x7E) shr 1 else -1
    }

    private fun startCodeLength(data: ByteArray): Int =
        when {
            data.size >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte() &&
                data[2] == 0.toByte() && data[3] == 1.toByte() -> 4

            data.size >= 3 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 1.toByte() -> 3
            else -> 0
        }

    private fun findStartCode(
        data: ByteArray,
        fromIndex: Int,
    ): StartCode? {
        for (index in fromIndex until data.size - 2) {
            if (data[index] != 0.toByte() || data[index + 1] != 0.toByte()) continue
            if (data[index + 2] == 1.toByte()) return StartCode(index, 3)
            if (index + 3 < data.size && data[index + 2] == 0.toByte() && data[index + 3] == 1.toByte()) {
                return StartCode(index, 4)
            }
        }
        return null
    }

    private data class StartCode(val index: Int, val length: Int)

}
