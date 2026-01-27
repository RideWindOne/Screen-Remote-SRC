package com.mobile.scrcpy.android.infrastructure.media.video

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import java.nio.ByteBuffer

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
        const val H265_NAL_IDR_N_LP = 20

        const val FRAME_META_MIN_SIZE = 6
        const val FRAME_META_MAX_SIZE = 10
    }

    /**
     * 提取 NAL 单元
     */
    fun extractNalUnit(buffer: ByteBuffer): ByteArray? {
        if (buffer.position() < 4) return null

        buffer.flip()

        var startPos = -1
        val limit = buffer.limit()

        // 查找第一个起始码
        for (i in 0 until limit - 3) {
            if (buffer.get(i) == 0.toByte() &&
                buffer.get(i + 1) == 0.toByte() &&
                buffer.get(i + 2) == 0.toByte() &&
                buffer.get(i + 3) == 1.toByte()) {
                startPos = i
                break
            }
        }

        if (startPos < 0) {
            buffer.compact()
            return null
        }

        // 查找下一个起始码
        var endPos = -1
        for (i in startPos + 4 until limit - 3) {
            if (buffer.get(i) == 0.toByte() &&
                buffer.get(i + 1) == 0.toByte() &&
                buffer.get(i + 2) == 0.toByte() &&
                buffer.get(i + 3) == 1.toByte()) {
                endPos = i
                break
            }
        }

        val nalSize = if (endPos > 0) endPos - startPos else limit - startPos
        val nalUnit = ByteArray(nalSize)
        buffer.position(startPos)
        buffer.get(nalUnit)

        if (endPos > 0) {
            buffer.position(endPos)
            buffer.compact()
        } else {
            buffer.clear()
        }

        return nalUnit
    }

    /**
     * 检查是否为 NAL 起始码
     */
    fun isNalStartCode(data: ByteArray): Boolean {
        return data.size >= 4 &&
                data[0] == 0.toByte() &&
                data[1] == 0.toByte() &&
                data[2] == 0.toByte() &&
                data[3] == 1.toByte()
    }

    /**
     * 解析 Frame Meta 消息
     * @return Triple(width, height, rotation) 或 null
     */
    fun parseFrameMeta(data: ByteArray): Triple<Int, Int, Int>? {
        try {
            if (data.size < FRAME_META_MIN_SIZE) return null

            val width = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
            val height = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
            val rotation = data[5].toInt() and 0xFF

            return Triple(width, height, rotation)
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "解析 Frame Meta 失败: ${e.message}")
            return null
        }
    }

    /**
     * 获取 H.264 NAL 类型
     */
    fun getH264NalType(nalUnit: ByteArray): Int {
        return if (nalUnit.size > 4) nalUnit[4].toInt() and 0x1F else -1
    }

    /**
     * 获取 H.265 NAL 类型
     */
    fun getH265NalType(nalUnit: ByteArray): Int {
        return if (nalUnit.size > 4) (nalUnit[4].toInt() and 0x7E) shr 1 else -1
    }

    /**
     * 检查是否为 H.264 关键帧
     */
    fun isH264KeyFrame(nalType: Int): Boolean {
        return nalType == H264_NAL_IDR
    }

    /**
     * 检查是否为 H.265 关键帧
     */
    fun isH265KeyFrame(nalType: Int): Boolean {
        return nalType == H265_NAL_IDR_W_RADL || nalType == H265_NAL_IDR_N_LP
    }
}
