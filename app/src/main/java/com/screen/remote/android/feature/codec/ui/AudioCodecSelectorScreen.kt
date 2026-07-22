package com.screen.remote.android.feature.codec.ui

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.manager.TTSManager
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.data.datastore.LocalDecoderCache
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.i18n.CodecTexts
import com.screen.remote.android.feature.codec.ui.internal.AudioCodecSelectorContent
import com.screen.remote.android.feature.codec.ui.internal.initializeAudioCodecSelectorTts
import com.screen.remote.android.feature.codec.ui.internal.rememberAudioCodecSelectorState
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
    val txtTestFailed = rememberText(CodecTexts.CODEC_TEST_FAILED)

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
                    tint = MaterialTheme.colorScheme.primary,
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
                    val success = testAudioDecoderDirect(codec.name, TTSManager.getInstance())
                    state.finishTesting()
                    Toast.makeText(context, if (success) txtTestSuccess else txtTestFailed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}
