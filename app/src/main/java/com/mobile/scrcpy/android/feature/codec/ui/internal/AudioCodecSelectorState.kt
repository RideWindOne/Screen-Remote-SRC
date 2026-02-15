package com.mobile.scrcpy.android.feature.codec.ui.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mobile.scrcpy.android.feature.codec.model.CodecInfo

internal class AudioCodecSelectorState(
    currentCodecName: String?,
) {
    var allCodecs by mutableStateOf<List<CodecInfo>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    var refreshTrigger by mutableIntStateOf(0)
        private set

    var selectedCodec by mutableStateOf(currentCodecName ?: "")
        private set

    var customCodecName by mutableStateOf(currentCodecName ?: "")
        private set

    var searchText by mutableStateOf("")
        private set

    var testingCodec by mutableStateOf<String?>(null)
        private set

    var codecTypeFilter by mutableStateOf("")
        private set

    var hardwareFilter by mutableStateOf("")
        private set

    fun updateLoading(loading: Boolean) {
        isLoading = loading
    }

    fun updateCodecs(codecs: List<CodecInfo>) {
        allCodecs = codecs
    }

    fun requestRefresh() {
        refreshTrigger++
    }

    fun selectDefaultCodec() {
        selectedCodec = ""
        customCodecName = ""
    }

    fun updateCustomCodecName(value: String) {
        customCodecName = value
        selectedCodec = ""
    }

    fun selectCodec(codec: CodecInfo) {
        selectedCodec = codec.name
        customCodecName = codec.name
    }

    fun updateSearchText(value: String) {
        searchText = value
    }

    fun updateCodecTypeFilter(value: String) {
        codecTypeFilter = value
    }

    fun updateHardwareFilter(value: String) {
        hardwareFilter = value
    }

    fun startTesting(codecName: String) {
        testingCodec = codecName
    }

    fun finishTesting() {
        testingCodec = null
    }

    fun resolveSelectedCodec(): String =
        when {
            selectedCodec.isEmpty() && customCodecName.isEmpty() -> ""
            selectedCodec.isNotEmpty() -> selectedCodec
            else -> customCodecName
        }
}

@Composable
internal fun rememberAudioCodecSelectorState(currentCodecName: String?): AudioCodecSelectorState =
    remember(currentCodecName) { AudioCodecSelectorState(currentCodecName) }
