package com.mobile.scrcpy.android.feature.codec.ui

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
 * 视频解码器选择页面
 */
@Composable
fun VideoCodecSelectorScreen(
    currentCodecName: String?,
    onCodecSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 动态获取视频解码器列表（优先使用持久化数据）
    var allCodecs by remember { mutableStateOf<List<CodecInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // 加载解码器列表
    fun loadDecoders() {
        scope.launch {
            isLoading = true
            val decoders = LocalDecoderCache.getVideoDecoders()
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

    // 筛选选项（存储标识符，显示时用 rememberText 转换）
    var codecTypeFilter by remember { mutableStateOf("") }
    var hardwareFilter by remember { mutableStateOf("") }
    var featureFilter by remember { mutableStateOf("") }

    // 动态获取显示文本（支持语言切换）
    val filterAllText = rememberText(CommonTexts.FILTER_ALL)
    val filterHardwareText = rememberText(CommonTexts.FILTER_HARDWARE)
    val filterSoftwareText = rememberText(CommonTexts.FILTER_SOFTWARE)
    val filterLowLatencyText = rememberText(CodecTexts.FILTER_LOW_LATENCY)
    val filterC2Text = rememberText(CodecTexts.FILTER_C2)

    // 动态提取编解码器类型
    val codecTypeOptions =
        remember(allCodecs) {
            val types = mutableSetOf<String>()
            allCodecs.forEach { codec ->
                when {
                    codec.type.contains("avc", ignoreCase = true) -> types.add("H.264")

                    codec.type.contains("hevc", ignoreCase = true) -> types.add("H.265")

                    codec.type.contains("av01", ignoreCase = true) ||
                        codec.type.contains("av1", ignoreCase = true) -> types.add("AV1")

                    codec.type.contains("vp8", ignoreCase = true) -> types.add("VP8")

                    codec.type.contains("vp9", ignoreCase = true) -> types.add("VP9")

                    codec.type.contains("mpeg4", ignoreCase = true) -> types.add("MPEG4")

                    codec.type.contains("h263", ignoreCase = true) ||
                        codec.type.contains("3gpp", ignoreCase = true) -> types.add("H.263")
                }
            }
            types.sorted()
        }

    val filteredCodecs =
        remember(searchText, codecTypeFilter, hardwareFilter, featureFilter, allCodecs) {
            allCodecs.filter { codec ->
                // 搜索过滤
                val matchesSearch =
                    searchText.isEmpty() ||
                        codec.name.contains(searchText, ignoreCase = true) ||
                        codec.type.contains(searchText, ignoreCase = true)

                // 编解码器类型过滤
                val matchesCodecType =
                    when (codecTypeFilter) {
                        "H.264" -> {
                            codec.type.contains("avc", ignoreCase = true)
                        }

                        "H.265" -> {
                            codec.type.contains("hevc", ignoreCase = true)
                        }

                        "AV1" -> {
                            codec.type.contains("av01", ignoreCase = true) ||
                                codec.type.contains("av1", ignoreCase = true)
                        }

                        "VP8" -> {
                            codec.type.contains("vp8", ignoreCase = true)
                        }

                        "VP9" -> {
                            codec.type.contains("vp9", ignoreCase = true)
                        }

                        "MPEG4" -> {
                            codec.type.contains("mpeg4", ignoreCase = true)
                        }

                        "H.263" -> {
                            codec.type.contains("h263", ignoreCase = true) ||
                                codec.type.contains("3gpp", ignoreCase = true)
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

                // 特性过滤
                val matchesFeature =
                    when (featureFilter) {
                        "low_latency" -> codec.name.contains("low_latency", ignoreCase = true)
                        "c2" -> codec.name.contains("c2.", ignoreCase = true)
                        else -> true
                    }

                matchesSearch && matchesCodecType && matchesHardware && matchesFeature
            }
        }

    DialogPage(
        title = CodecTexts.CODEC_SELECTOR_VIDEO_TITLE.get(),
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
            searchWeight = 2.9f,
            filters =
                listOf(
                    FilterConfig(
                        currentLabel = codecTypeFilter.ifEmpty { filterAllText },
                        options = listOf(filterAllText) + codecTypeOptions,
                        onOptionSelected = { selected ->
                            codecTypeFilter = if (selected == filterAllText) "" else selected
                        },
                        weight = 1.7f, // 编解码器类型筛选器更宽
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
                        weight = 2.2f,
                    ),
                    FilterConfig(
                        currentLabel =
                            when (featureFilter) {
                                "low_latency" -> filterLowLatencyText
                                "c2" -> filterC2Text
                                else -> filterAllText
                            },
                        options = listOf(filterAllText, filterLowLatencyText, filterC2Text),
                        onOptionSelected = { selected ->
                            featureFilter =
                                when (selected) {
                                    filterLowLatencyText -> "low_latency"
                                    filterC2Text -> "c2"
                                    else -> ""
                                }
                        },
                        weight = 2.7f,
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
