package com.screen.remote.android.feature.codec.ui.internal

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.designsystem.component.SectionTitle
import com.screen.remote.android.core.domain.model.CodecAcceleration
import com.screen.remote.android.core.i18n.CodecTexts
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.feature.codec.model.CodecInfo
import com.screen.remote.android.feature.codec.ui.CodecCountInfo
import com.screen.remote.android.feature.codec.ui.CodecFilterBar
import com.screen.remote.android.feature.codec.ui.CodecList
import com.screen.remote.android.feature.codec.ui.CodecOptionsSection
import com.screen.remote.android.feature.codec.ui.EmptyCodecState
import com.screen.remote.android.feature.codec.ui.FilterConfig
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun AudioCodecSelectorContent(
    state: AudioCodecSelectorState,
    onTestCodec: (CodecInfo) -> Unit,
) {
    val filterAllText = rememberText(CommonTexts.FILTER_ALL)
    val filterHardwareText = rememberText(CommonTexts.FILTER_HARDWARE)
    val filterSoftwareText = rememberText(CommonTexts.FILTER_SOFTWARE)

    val codecTypeOptions =
        remember(state.allCodecs) {
            extractAudioCodecTypeOptions(state.allCodecs)
        }
    val filteredCodecs =
        remember(
            state.searchText,
            state.codecTypeFilter,
            state.hardwareFilter,
            state.allCodecs,
        ) {
            filterAudioCodecs(
                codecs = state.allCodecs,
                searchText = state.searchText,
                codecTypeFilter = state.codecTypeFilter,
                hardwareFilter = state.hardwareFilter,
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
        searchWeight = 3.6f,
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
                    weight = 1.5f,
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
                    weight = 2f,
                ),
            ),
    )

    if (filteredCodecs.isNotEmpty()) {
        CodecList(
            codecs = filteredCodecs,
            selectedCodec = state.selectedCodec,
            onCodecSelect = state::selectCodec,
            showTestButton = true,
            testingCodec = state.testingCodec,
            onTest = onTestCodec,
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

internal suspend fun initializeAudioCodecSelectorTts(context: android.content.Context) {
    if (!com.screen.remote.android.core.common.manager.TTSManager.isReady()) {
        com.screen.remote.android.core.common.manager.TTSManager.init(context, showToast = true)
        var waitCount = 0
        while (!com.screen.remote.android.core.common.manager.TTSManager.isReady() && waitCount < 50) {
            kotlinx.coroutines.delay(100.milliseconds)
            waitCount++
        }
    }
}

private fun extractAudioCodecTypeOptions(codecs: List<CodecInfo>): List<String> {
    val types = mutableSetOf<String>()
    codecs.forEach { codec ->
        when {
            codec.type.contains("opus", ignoreCase = true) -> types.add("OPUS")
            codec.type.contains("aac", ignoreCase = true) || codec.type.contains(
                "mp4a",
                ignoreCase = true
            ) -> types.add("AAC")

            codec.type.contains("flac", ignoreCase = true) -> types.add("FLAC")
        }
    }
    return types.sorted()
}

private fun filterAudioCodecs(
    codecs: List<CodecInfo>,
    searchText: String,
    codecTypeFilter: String,
    hardwareFilter: String,
): List<CodecInfo> =
    codecs.filter { codec ->
        val matchesSearch =
            searchText.isEmpty() ||
                codec.name.contains(searchText, ignoreCase = true) ||
                codec.type.contains(searchText, ignoreCase = true)

        val matchesCodecType =
            when (codecTypeFilter) {
                "OPUS" -> codec.type.contains("opus", ignoreCase = true)
                "AAC" -> codec.type.contains("aac", ignoreCase = true) || codec.type.contains("mp4a", ignoreCase = true)
                "FLAC" -> codec.type.contains("flac", ignoreCase = true)
                else -> true
            }

        val matchesHardware =
            when (hardwareFilter) {
                "hardware" -> codec.acceleration == CodecAcceleration.HARDWARE
                "software" -> codec.acceleration == CodecAcceleration.SOFTWARE
                else -> true
            }

        matchesSearch && matchesCodecType && matchesHardware
    }
