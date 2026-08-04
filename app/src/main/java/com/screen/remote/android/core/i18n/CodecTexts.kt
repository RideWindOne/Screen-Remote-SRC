package com.screen.remote.android.core.i18n

/**
 * 编解码器测试相关文本
 */
object CodecTexts {
    val CODEC_TEST_BUTTON = TextPair("测试", "Test")
    val CODEC_TEST_SUCCESS = TextPair("测试成功", "Test Successful")
    val CODEC_TEST_FAILED = TextPair(
        "测试失败：该解码器无法完成编解码回环",
        "Test failed: this decoder could not complete the codec loopback"
    )
    val CODEC_TEST_FOUND_COUNT = TextPair("共找到", "Found")
    val CODEC_TEST_AUDIO_CODECS = TextPair("个音频编解码器", "audio codecs")
    val CODEC_TEST_VIDEO_CODECS = TextPair("个视频编码器", "video encoders")

    // 编解码器选择
    val CODEC_SELECTOR_AUDIO_TITLE = TextPair("选择音频解码器", "Select Audio Decoder")
    val CODEC_SELECTOR_VIDEO_TITLE = TextPair("选择视频解码器", "Select Video Decoder")
    val CODEC_SELECTOR_DECODERS = TextPair("个解码器", "decoders")

    // 筛选选项（特定于编解码器）
    val FILTER_LOW_LATENCY = TextPair("低延迟", "Low Latency")
    val FILTER_C2 = TextPair("C2", "C2")

    // 编解码器协议匹配
    val CODEC_PROTOCOL_MISMATCH =
        TextPair(
            "音视频编解码器组合不兼容，请修改后再保存",
            "The audio/video codec combination is incompatible. Update it before saving.",
        )
    val CODEC_TTS_INIT_SUCCESS =
        TextPair(
            "TTS 初始化成功",
            "TTS initialized successfully",
        )
    val CODEC_TTS_INIT_FAILED =
        TextPair(
            "TTS 初始化失败，可能未安装 TTS 引擎",
            "TTS initialization failed, TTS engine may not be installed",
        )
}
