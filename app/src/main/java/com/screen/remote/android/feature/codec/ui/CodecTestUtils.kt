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

package com.screen.remote.android.feature.codec.ui

import android.speech.tts.TextToSpeech
import com.screen.remote.android.feature.codec.ui.test.testAudioDecoder as testAudioDecoderImpl
import com.screen.remote.android.feature.codec.ui.test.testAudioDecoderDirect as testAudioDecoderDirectImpl

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
): Boolean = testAudioDecoderDirectImpl(codecName, tts)
