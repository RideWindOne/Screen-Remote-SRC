package com.mobile.scrcpy.android.feature.codec.ui

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.manager.TTSManager
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.data.datastore.LocalDecoderCache
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.i18n.CodecTexts
import com.mobile.scrcpy.android.feature.codec.model.CodecInfo
import com.mobile.scrcpy.android.feature.codec.ui.internal.AudioCodecSelectorContent
import com.mobile.scrcpy.android.feature.codec.ui.internal.initializeAudioCodecSelectorTts
import com.mobile.scrcpy.android.feature.codec.ui.internal.rememberAudioCodecSelectorState
import kotlinx.coroutines.launch

@Composable
fun AudioCodecSelectorScreen(
    currentCodecName: String?,
    onCodecSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberAudioCodecSelectorState(currentCodecName)
    val txtTestSuccess = rememberText(CodecTexts.CODEC_TEST_SUCCESS)

    fun loadDecoders() {
        scope.launch {
            state.updateLoading(true)
            state.updateCodecs(
                buildAudioDecoderInfos(LocalDecoderCache.getAudioDecoders()),
            )
            state.updateLoading(false)
        }
    }

    LaunchedEffect(state.refreshTrigger) {
        loadDecoders()
    }

    LaunchedEffect(Unit) {
        initializeAudioCodecSelectorTts(context)
    }

    DialogPage(
        title = CodecTexts.CODEC_SELECTOR_AUDIO_TITLE.get(),
        onDismiss = {
            onCodecSelected(state.resolveSelectedCodec())
            onBack()
        },
        showBackButton = true,
        trailingContent = {
            IconButton(
                onClick = {
                    scope.launch {
                        LocalDecoderCache.clear()
                        state.requestRefresh()
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
        AudioCodecSelectorContent(
            state = state,
            onTestCodec = { codec ->
                scope.launch {
                    state.startTesting(codec.name)
                    testAudioDecoderDirect(codec.name, TTSManager.getInstance())
                    state.finishTesting()
                    Toast.makeText(context, txtTestSuccess, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}
