package com.screen.remote.android.core.i18n

/**
 * 编解码器测试相关文本
 */
object CodecTexts {
    // 编解码器测试
    val CODEC_TEST_TITLE = TextPair("测试音频编解码器", "Test Audio Codecs")
    val CODEC_TEST_BUTTON = TextPair("测试", "Test")
    val CODEC_TEST_SUCCESS = TextPair("测试成功", "Test Successful")
    val CODEC_TEST_FAILED = TextPair("测试失败：该解码器无法完成编解码回环", "Test failed: this decoder could not complete the codec loopback")
    val CODEC_TEST_SEARCH_PLACEHOLDER = TextPair("搜索编解码器", "Search codec")
    val CODEC_TEST_FOUND_COUNT = TextPair("共找到", "Found")
    val CODEC_TEST_AUDIO_CODECS = TextPair("个音频编解码器", "audio codecs")
    val CODEC_TEST_VIDEO_CODECS = TextPair("个视频编码器", "video encoders")
    val CODEC_TEST_WARNING_OPUS =
        TextPair(
            "⚠️ 注意：部分设备的 Opus 解码器可能不兼容裸 Opus 帧，建议使用 AAC",
            "⚠️ Note: Some devices' Opus decoders may not support raw Opus frames, AAC is recommended",
        )
    val CODEC_TEST_INFO_COMPATIBILITY =
        TextPair(
            "💡 说明：测试功能未适配所有解码格式，如果测试没有声音，可能是适配问题",
            "💡 Info: Test function may not support all formats, no sound may indicate compatibility issues",
        )
    val CODEC_TEST_TYPE_LABEL = TextPair("类型", "Type")
    val CODEC_TEST_ENCODER = TextPair("编码器", "Encoder")
    val CODEC_TEST_DECODER = TextPair("解码器", "Decoder")
    val CODEC_TEST_SAMPLE_RATE = TextPair("采样率", "Sample Rate")
    val CODEC_TEST_MAX_CHANNELS = TextPair("最大声道", "Max Channels")
    val CODEC_TEST_ACTUAL = TextPair("实际", "Actual")
    val CODEC_TEST_NO_DETAILS = TextPair("无法获取详细信息", "Unable to get details")

    // 编解码器选择
    val CODEC_SELECTOR_AUDIO_TITLE = TextPair("选择音频解码器", "Select Audio Decoder")
    val CODEC_SELECTOR_VIDEO_TITLE = TextPair("选择视频解码器", "Select Video Decoder")
    val CODEC_SELECTOR_DECODERS = TextPair("个解码器", "decoders")
    val CODEC_SELECTOR_AUTO = TextPair("自动选择", "Auto Select")
    val CODEC_SELECTOR_AUTO_DESC = TextPair("由系统自动选择最佳解码器", "System will select the best decoder")
    val CODEC_SELECTOR_VIDEO_HELP =
        TextPair(
            "💡 推荐配置（按优先级）：\n" +
                "1. 硬件 + 低延迟 + C2架构\n" +
                "2. 硬件 + 低延迟 + OMX\n" +
                "3. 硬件 + C2架构\n" +
                "4. 硬件 + OMX",
            "💡 Recommended (by priority):\n" +
                "1. Hardware + Low Latency + C2\n" +
                "2. Hardware + Low Latency + OMX\n" +
                "3. Hardware + C2\n" +
                "4. Hardware + OMX",
        )

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
