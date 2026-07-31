package com.screen.remote.android.feature.codec.ui.internal

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.domain.model.CodecAcceleration
import com.screen.remote.android.core.designsystem.component.SectionTitle
import com.screen.remote.android.core.i18n.CodecTexts
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.feature.codec.ui.CodecCountInfo
import com.screen.remote.android.feature.codec.ui.CodecFilterBar
import com.screen.remote.android.feature.codec.ui.CodecList
import com.screen.remote.android.feature.codec.ui.CodecOptionsSection
import com.screen.remote.android.feature.codec.ui.EmptyCodecState
import com.screen.remote.android.feature.codec.ui.FilterConfig

@Composable
internal fun VideoCodecSelectorContent(state: VideoCodecSelectorState) {
    val filterAllText = rememberText(CommonTexts.FILTER_ALL)
    val filterHardwareText = rememberText(CommonTexts.FILTER_HARDWARE)
    val filterSoftwareText = rememberText(CommonTexts.FILTER_SOFTWARE)
    val filterLowLatencyText = rememberText(CodecTexts.FILTER_LOW_LATENCY)
    val filterC2Text = rememberText(CodecTexts.FILTER_C2)

    val codecTypeOptions =
        remember(state.allCodecs) {
            extractVideoCodecTypeOptions(state.allCodecs)
        }
    val filteredCodecs =
        remember(
            state.searchText,
            state.codecTypeFilter,
            state.hardwareFilter,
            state.featureFilter,
            state.allCodecs,
        ) {
            filterVideoCodecs(
                codecs = state.allCodecs,
                searchText = state.searchText,
                codecTypeFilter = state.codecTypeFilter,
                hardwareFilter = state.hardwareFilter,
                featureFilter = state.featureFilter,
            )
        }

    SectionTitle(SessionTexts.SECTION_DECODER_OPTIONS.get())
    CodecOptionsSection(
        selectedCodec = state.selectedCodec,
        customCodecName = state.customCodecName,
        onDefaultSelected = state::selectDefaultCodec,
        onCustomCodecChange = state::updateCustomCodecName,
        placeholderText = SessionTexts.PLACEHOLDER_CUSTOM_DECODER.get(),
    )

    SectionTitle(SessionTexts.SECTION_DETECTED_DECODERS.get())
    CodecFilterBar(
        searchText = state.searchText,
        onSearchChange = state::updateSearchText,
        searchPlaceholder = SessionTexts.PLACEHOLDER_SEARCH_DECODER.get(),
        searchWeight = 2.9f,
        filters =
            listOf(
                FilterConfig(
                    currentLabel = state.codecTypeFilter.ifEmpty { filterAllText },
                    options = listOf(filterAllText) + codecTypeOptions,
                    onOptionSelected = { selected ->
                        state.updateCodecTypeFilter(
                            if (selected == filterAllText) "" else selected,
                        )
                    },
                    weight = 1.7f,
                ),
                FilterConfig(
                    currentLabel =
                        when (state.hardwareFilter) {
                            "hardware" -> filterHardwareText
                            "software" -> filterSoftwareText
                            else -> filterAllText
                        },
                    options = listOf(filterAllText, filterHardwareText, filterSoftwareText),
                    onOptionSelected = { selected ->
                        state.updateHardwareFilter(
                            when (selected) {
                                filterHardwareText -> "hardware"
                                filterSoftwareText -> "software"
                                else -> ""
                            },
                        )
                    },
                    weight = 2.2f,
                ),
                FilterConfig(
                    currentLabel =
                        when (state.featureFilter) {
                            "low_latency" -> filterLowLatencyText
                            "c2" -> filterC2Text
                            else -> filterAllText
                        },
                    options = listOf(filterAllText, filterLowLatencyText, filterC2Text),
                    onOptionSelected = { selected ->
                        state.updateFeatureFilter(
                            when (selected) {
                                filterLowLatencyText -> "low_latency"
                                filterC2Text -> "c2"
                                else -> ""
                            },
                        )
                    },
                    weight = 2.7f,
                ),
            ),
    )

    if (filteredCodecs.isNotEmpty()) {
        CodecList(
            codecs = filteredCodecs,
            selectedCodec = state.selectedCodec,
            onCodecSelect = state::selectCodec,
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

private fun extractVideoCodecTypeOptions(codecs: List<com.screen.remote.android.feature.codec.model.CodecInfo>): List<String> {
    val types = mutableSetOf<String>()
    codecs.forEach { codec ->
        when {
            codec.type.contains("avc", ignoreCase = true) -> types.add("H264")
            codec.type.contains("hevc", ignoreCase = true) -> types.add("H265")
            codec.type.contains("av01", ignoreCase = true) || codec.type.contains("av1", ignoreCase = true) -> types.add("AV1")
            codec.type.contains("vp8", ignoreCase = true) -> types.add("VP8")
            codec.type.contains("vp9", ignoreCase = true) -> types.add("VP9")
        }
    }
    return types.sorted()
}

private fun filterVideoCodecs(
    codecs: List<com.screen.remote.android.feature.codec.model.CodecInfo>,
    searchText: String,
    codecTypeFilter: String,
    hardwareFilter: String,
    featureFilter: String,
): List<com.screen.remote.android.feature.codec.model.CodecInfo> =
    codecs.filter { codec ->
        val matchesSearch =
            searchText.isEmpty() ||
                codec.name.contains(searchText, ignoreCase = true) ||
                codec.type.contains(searchText, ignoreCase = true)

        val matchesCodecType =
            when (codecTypeFilter) {
                "H264" -> codec.type.contains("avc", ignoreCase = true)
                "H265" -> codec.type.contains("hevc", ignoreCase = true)
                "AV1" -> codec.type.contains("av01", ignoreCase = true) || codec.type.contains("av1", ignoreCase = true)
                "VP8" -> codec.type.contains("vp8", ignoreCase = true)
                "VP9" -> codec.type.contains("vp9", ignoreCase = true)
                else -> true
            }

        val matchesHardware =
            when (hardwareFilter) {
                "hardware" -> codec.acceleration == CodecAcceleration.HARDWARE
                "software" -> codec.acceleration == CodecAcceleration.SOFTWARE
                else -> true
            }

        val matchesFeature =
            when (featureFilter) {
                "low_latency" -> {
                    val selectedMime = videoFilterMimeType(codecTypeFilter)
                    if (selectedMime == null) {
                        codec.lowLatencyMimeTypes.isNotEmpty()
                    } else {
                        codec.lowLatencyMimeTypes.any { it.equals(selectedMime, ignoreCase = true) }
                    }
                }
                "c2" -> codec.name.startsWith("c2.", ignoreCase = true)
                else -> true
            }

        matchesSearch && matchesCodecType && matchesHardware && matchesFeature
    }

private fun videoFilterMimeType(filter: String): String? =
    when (filter) {
        "H264" -> "video/avc"
        "H265" -> "video/hevc"
        "AV1" -> "video/av01"
        "VP8" -> "video/x-vnd.on2.vp8"
        "VP9" -> "video/x-vnd.on2.vp9"
        else -> null
    }
