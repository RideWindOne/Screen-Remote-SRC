package com.mobile.scrcpy.android.core.i18n

/**
 * 编解码器测试相关文本
 */
object CodecTexts {
    // 编解码器测试
    val CODEC_TEST_TITLE = TextPair("测试音频编解码器", "Test Audio Codecs")
    val CODEC_TEST_BUTTON = TextPair("点击测试", "Tap to Test")
    val CODEC_TEST_SEARCH_PLACEHOLDER = TextPair("搜索编解码器", "Search codec")
    val CODEC_TEST_FOUND_COUNT = TextPair("共找到", "Found")
    val CODEC_TEST_AUDIO_CODECS = TextPair("个音频编解码器", "audio codecs")
    val CODEC_TEST_WARNING_OPUS = TextPair("⚠️ 注意：部分设备的 Opus 解码器可能不兼容裸 Opus 帧，建议使用 AAC", "⚠️ Note: Some devices' Opus decoders may not support raw Opus frames, AAC is recommended")
    val CODEC_TEST_INFO_COMPATIBILITY = TextPair("💡 说明：测试功能未适配所有解码格式，如果测试没有声音，可能是适配问题", "💡 Info: Test function may not support all formats, no sound may indicate compatibility issues")
    val CODEC_TEST_TYPE_LABEL = TextPair("类型", "Type")
    val CODEC_TEST_ENCODER = TextPair("编码器", "Encoder")
    val CODEC_TEST_DECODER = TextPair("解码器", "Decoder")
    val CODEC_TEST_FILTER_ALL = TextPair("全部", "All")
    val CODEC_TEST_SAMPLE_RATE = TextPair("采样率", "Sample Rate")
    val CODEC_TEST_MAX_CHANNELS = TextPair("最大声道", "Max Channels")
    val CODEC_TEST_ACTUAL = TextPair("实际", "Actual")
    val CODEC_TEST_NO_DETAILS = TextPair("无法获取详细信息", "Unable to get details")
}
