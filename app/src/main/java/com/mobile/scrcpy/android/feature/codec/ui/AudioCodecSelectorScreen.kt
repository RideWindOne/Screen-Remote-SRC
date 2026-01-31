package com.mobile.scrcpy.android.feature.codec.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.manager.TTSManager
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.data.datastore.LocalDecoderCache
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.i18n.CodecTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.codec.model.CodecInfo
import kotlinx.coroutines.launch

/**
 * 音频解码器选择页面
 */
@Composable
fun AudioCodecSelectorScreen(
    currentCodecName: String?,
    onCodecSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val txtSampleRate = rememberText(CodecTexts.CODEC_TEST_SAMPLE_RATE)
    val txtMaxChannels = rememberText(CodecTexts.CODEC_TEST_MAX_CHANNELS)
    val txtActual = rememberText(CodecTexts.CODEC_TEST_ACTUAL)
    val txtNoDetails = rememberText(CodecTexts.CODEC_TEST_NO_DETAILS)
    val txtTestSuccess = rememberText(CodecTexts.CODEC_TEST_SUCCESS)

    // 动态获取音频解码器列表（优先使用持久化数据）
    var allCodecs by remember { mutableStateOf<List<CodecInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // 加载解码器列表
    fun loadDecoders() {
        scope.launch {
            isLoading = true
            val decoders = LocalDecoderCache.getAudioDecoders()
            allCodecs =
                decoders.map { name ->
                    CodecInfo(
                        name = name,
                        type = "",
                        isEncoder = false,
                        capabilities = "",
                    )
                }
            isLoading = false
        }
    }

    LaunchedEffect(refreshTrigger) {
        loadDecoders()
    }

    var selectedCodec by remember { mutableStateOf(currentCodecName ?: "") }
    var customCodecName by remember { mutableStateOf(currentCodecName ?: "") }
    var searchText by remember { mutableStateOf("") }
    var testingCodec by remember { mutableStateOf<String?>(null) }

    // 初始化 TTS
    LaunchedEffect(Unit) {
        if (!TTSManager.isReady()) {
            TTSManager.init(context, showToast = true)
            var waitCount = 0
            while (!TTSManager.isReady() && waitCount < 50) {
                kotlinx.coroutines.delay(100)
                waitCount++
            }
        }
    }

    // 筛选选项（存储标识符，显示时用 rememberText 转换）
    var codecTypeFilter by remember { mutableStateOf("") }
    var hardwareFilter by remember { mutableStateOf("") }

    // 动态获取显示文本（支持语言切换）
    val filterAllText = rememberText(CommonTexts.FILTER_ALL)
    val filterHardwareText = rememberText(CommonTexts.FILTER_HARDWARE)
    val filterSoftwareText = rememberText(CommonTexts.FILTER_SOFTWARE)

    // 动态提取编解码器类型
    val codecTypeOptions =
        remember(allCodecs) {
            val types = mutableSetOf<String>()
            allCodecs.forEach { codec ->
                when {
                    codec.type.contains("opus", ignoreCase = true) -> types.add("OPUS")

                    codec.type.contains("aac", ignoreCase = true) ||
                        codec.type.contains("mp4a", ignoreCase = true) -> types.add("AAC")

                    codec.type.contains("flac", ignoreCase = true) -> types.add("FLAC")

                    codec.type.contains("vorbis", ignoreCase = true) -> types.add("Vorbis")

                    codec.type.contains("amr", ignoreCase = true) ||
                        codec.type.contains("3gpp", ignoreCase = true) -> types.add("AMR")

                    codec.type.contains("raw", ignoreCase = true) -> types.add("RAW")
                }
            }
            types.sorted()
        }

    val filteredCodecs =
        remember(searchText, codecTypeFilter, hardwareFilter, allCodecs) {
            allCodecs.filter { codec ->
                // 搜索过滤
                val matchesSearch =
                    searchText.isEmpty() ||
                        codec.name.contains(searchText, ignoreCase = true) ||
                        codec.type.contains(searchText, ignoreCase = true)

                // 编解码器类型过滤
                val matchesCodecType =
                    when (codecTypeFilter) {
                        "OPUS" -> {
                            codec.type.contains("opus", ignoreCase = true)
                        }

                        "AAC" -> {
                            codec.type.contains("aac", ignoreCase = true) ||
                                codec.type.contains("mp4a", ignoreCase = true)
                        }

                        "FLAC" -> {
                            codec.type.contains("flac", ignoreCase = true)
                        }

                        "Vorbis" -> {
                            codec.type.contains("vorbis", ignoreCase = true)
                        }

                        "AMR" -> {
                            codec.type.contains("amr", ignoreCase = true) ||
                                codec.type.contains("3gpp", ignoreCase = true)
                        }

                        "RAW" -> {
                            codec.type.contains("raw", ignoreCase = true)
                        }

                        else -> {
                            true
                        }
                    }

                // 硬件/软件过滤
                val isHardware =
                    !codec.name.startsWith("OMX.google") &&
                        !codec.name.startsWith("c2.android")
                val matchesHardware =
                    when (hardwareFilter) {
                        "hardware" -> isHardware
                        "software" -> !isHardware
                        else -> true
                    }

                matchesSearch && matchesCodecType && matchesHardware
            }
        }

    DialogPage(
        title = CodecTexts.CODEC_SELECTOR_AUDIO_TITLE.get(),
        onDismiss = {
            val result =
                when {
                    selectedCodec.isEmpty() && customCodecName.isEmpty() -> ""
                    selectedCodec.isNotEmpty() -> selectedCodec
                    else -> customCodecName
                }
            onCodecSelected(result)
            onBack()
        },
        showBackButton = true,
        trailingContent = {
            IconButton(
                onClick = {
                    scope.launch {
                        LocalDecoderCache.clear()
                        refreshTrigger++
                    }
                },
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "刷新",
                    tint = AppColors.iOSBlue,
                )
            }
        },
        maxHeightRatio = 0.8f,
        enableScroll = true,
        horizontalPadding = 16.dp,
        verticalSpacing = 8.dp,
    ) {
        // 解码器选项
        SectionTitle(SessionTexts.SECTION_DECODER_OPTIONS.get())
        CodecOptionsSection(
            selectedCodec = selectedCodec,
            customCodecName = customCodecName,
            onDefaultSelected = {
                selectedCodec = ""
                customCodecName = ""
            },
            onCustomCodecChange = {
                customCodecName = it
                selectedCodec = ""
            },
            placeholderText = SessionTexts.PLACEHOLDER_CUSTOM_DECODER.get(),
        )

        // 检测到的解码器
        SectionTitle(SessionTexts.SECTION_DETECTED_DECODERS.get())

        // 筛选选项行
        CodecFilterBar(
            searchText = searchText,
            onSearchChange = { searchText = it },
            searchPlaceholder = SessionTexts.PLACEHOLDER_SEARCH_DECODER.get(),
            searchWeight = 3.6f,
            filters =
                listOf(
                    FilterConfig(
                        currentLabel = codecTypeFilter.ifEmpty { filterAllText },
                        options = listOf(filterAllText) + codecTypeOptions,
                        onOptionSelected = { selected ->
                            codecTypeFilter = if (selected == filterAllText) "" else selected
                        },
                        weight = 1.5f, // 编解码器类型筛选器稍宽
                    ),
                    FilterConfig(
                        currentLabel =
                            when (hardwareFilter) {
                                "hardware" -> filterHardwareText
                                "software" -> filterSoftwareText
                                else -> filterAllText
                            },
                        options = listOf(filterAllText, filterHardwareText, filterSoftwareText),
                        onOptionSelected = { selected ->
                            hardwareFilter =
                                when (selected) {
                                    filterHardwareText -> "hardware"
                                    filterSoftwareText -> "software"
                                    else -> ""
                                }
                        },
                        weight = 2f,
                    ),
                ),
        )

        if (filteredCodecs.isNotEmpty()) {
            CodecList(
                codecs = filteredCodecs,
                selectedCodec = selectedCodec,
                onCodecSelect = { codec ->
                    selectedCodec = codec.name
                    customCodecName = codec.name
                },
                showTestButton = true,
                testingCodec = testingCodec,
                onTest = { codec ->
                    scope.launch {
                        testingCodec = codec.name
                        testAudioDecoderDirect(codec.type, TTSManager.getInstance())
                        testingCodec = null
                        Toast.makeText(context, txtTestSuccess, Toast.LENGTH_SHORT).show()
                    }
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            CodecCountInfo(
                count = filteredCodecs.size,
                codecType = CodecTexts.CODEC_SELECTOR_DECODERS.get(),
            )
        } else {
            EmptyCodecState(SessionTexts.STATUS_NO_DECODERS_DETECTED.get())
        }
    }
}
