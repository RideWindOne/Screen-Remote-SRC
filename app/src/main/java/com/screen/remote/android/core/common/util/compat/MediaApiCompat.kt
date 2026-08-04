/*
 * 媒体 API 兼容性工具
 *
 * 从 ApiCompatHelper.kt 拆分而来
 * 职责：MediaCodec、音视频编解码器相关 API 兼容
 */

package com.screen.remote.android.core.common.util.compat

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi

/**
 * 判断 MediaCodecInfo 是否为硬件加速编解码器
 */
fun isHardwareAccelerated(info: MediaCodecInfo): Boolean =
    if (Build.VERSION.SDK_INT >= 29) {
        info.isHardwareAccelerated && !info.isSoftwareOnly
    } else {
        !info.name.startsWith("OMX.google", ignoreCase = true)
    }

/**
 * 安全地设置 MediaFormat 的 KEY_LOW_LATENCY
 */
fun setLowLatencyIfSupported(
    format: MediaFormat,
    lowLatency: Int,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        format.setInteger(MediaFormat.KEY_LOW_LATENCY, lowLatency)
    }
}

/**
 * 安全地设置 MediaFormat 的 KEY_ALLOW_FRAME_DROP
 */
fun setAllowFrameDropIfSupported(
    format: MediaFormat,
    allowFrameDrop: Int,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        format.setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, allowFrameDrop)
    }
}

/**
 * Reads the decoded PCM encoding on Android 7.0 and newer.
 */
fun getPcmEncodingOrDefault(
    format: MediaFormat,
    defaultEncoding: Int,
): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
        format.getInteger(MediaFormat.KEY_PCM_ENCODING)
    } else {
        defaultEncoding
    }

fun createAudioTrackCompat(
    sampleRate: Int,
    channelMask: Int,
    encoding: Int,
    bufferSize: Int,
): AudioTrack =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Api23Media.createAudioTrack(sampleRate, channelMask, encoding, bufferSize)
    } else {
        @Suppress("DEPRECATION")
        AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            channelMask,
            encoding,
            bufferSize,
            AudioTrack.MODE_STREAM,
        )
    }

fun writeAudioTrackBlockingCompat(
    audioTrack: AudioTrack,
    data: ByteArray,
    offset: Int,
    size: Int,
): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Api23Media.writeBlocking(audioTrack, data, offset, size)
    } else {
        audioTrack.write(data, offset, size)
    }

/**
 * API 21-22 cannot replace a configured codec's output Surface.
 * Returns false so the caller can retain the target for the next codec configuration.
 */
fun setOutputSurfaceCompat(
    codec: MediaCodec,
    surface: Surface,
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
    Api23Media.setOutputSurface(codec, surface)
    return true
}

@RequiresApi(Build.VERSION_CODES.M)
private object Api23Media {
    fun createAudioTrack(
        sampleRate: Int,
        channelMask: Int,
        encoding: Int,
        bufferSize: Int,
    ): AudioTrack {
        val builder =
            AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .setEncoding(encoding)
                        .build(),
                ).setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        return builder.build()
    }

    fun writeBlocking(
        audioTrack: AudioTrack,
        data: ByteArray,
        offset: Int,
        size: Int,
    ): Int = audioTrack.write(data, offset, size, AudioTrack.WRITE_BLOCKING)

    fun setOutputSurface(
        codec: MediaCodec,
        surface: Surface,
    ) {
        codec.setOutputSurface(surface)
    }
}

/**
 * 安全地从 MediaFormat 获取裁剪区域
 */
fun getCropRectIfSupported(format: MediaFormat): android.graphics.Rect? =
    try {
        if (format.containsKey("crop-left")) {
            val left = format.getInteger("crop-left")
            val right = format.getInteger("crop-right")
            val top = format.getInteger("crop-top")
            val bottom = format.getInteger("crop-bottom")
            android.graphics.Rect(left, top, right, bottom)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
