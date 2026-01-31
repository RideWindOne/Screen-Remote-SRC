/*
 * 编解码器测试工具 - 主入口
 * 
 * 文件拆分说明：
 * - VideoCodecTests.kt: 视频编解码器查询和测试
 * - AudioCodecTests.kt: 音频编解码器查询和测试
 * - CodecMockData.kt: 测试数据生成（TTS、Beep、重采样）
 * 
 * 本文件保留公开 API，内部实现已拆分到 test/ 目录
 */

package com.mobile.scrcpy.android.feature.codec.ui

import android.speech.tts.TextToSpeech
import com.mobile.scrcpy.android.feature.codec.model.CodecInfo
import com.mobile.scrcpy.android.feature.codec.ui.test.getAudioCodecs as getAudioCodecsImpl
import com.mobile.scrcpy.android.feature.codec.ui.test.getVideoCodecs as getVideoCodecsImpl
import com.mobile.scrcpy.android.feature.codec.ui.test.testAudioDecoder as testAudioDecoderImpl
import com.mobile.scrcpy.android.feature.codec.ui.test.testAudioDecoderDirect as testAudioDecoderDirectImpl

/**
 * 获取所有视频编解码器
 */
fun getVideoCodecs(): List<CodecInfo> = getVideoCodecsImpl()

/**
 * 获取所有音频编解码器
 */
fun getAudioCodecs(
    txtSampleRate: String,
    txtMaxChannels: String,
    txtActual: String,
    txtNoDetails: String,
): List<CodecInfo> = getAudioCodecsImpl(txtSampleRate, txtMaxChannels, txtActual, txtNoDetails)

/**
 * 测试音频解码器
 */
suspend fun testAudioDecoder(
    mimeType: String,
    tts: TextToSpeech?,
) = testAudioDecoderImpl(mimeType, tts)

/**
 * 直接测试音频解码器（不使用编码器，直接播放 PCM）
 */
suspend fun testAudioDecoderDirect(
    codecName: String,
    tts: TextToSpeech?,
) = testAudioDecoderDirectImpl(codecName, tts)
