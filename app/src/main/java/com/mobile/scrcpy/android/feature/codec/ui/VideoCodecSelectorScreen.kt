package com.mobile.scrcpy.android.feature.codec.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.data.datastore.LocalDecoderCache
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.i18n.CodecTexts
import com.mobile.scrcpy.android.feature.codec.ui.internal.VideoCodecSelectorContent
import com.mobile.scrcpy.android.feature.codec.ui.internal.rememberVideoCodecSelectorState
import kotlinx.coroutines.launch

@Composable
fun VideoCodecSelectorScreen(
    currentCodecName: String?,
    onCodecSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = rememberVideoCodecSelectorState(currentCodecName)

    fun loadDecoders() {
        scope.launch {
            state.updateLoading(true)
            state.updateCodecs(buildVideoDecoderInfos(LocalDecoderCache.getVideoDecoders()))
            state.updateLoading(false)
        }
    }

    LaunchedEffect(state.refreshTrigger) {
        loadDecoders()
    }

    DialogPage(
        title = CodecTexts.CODEC_SELECTOR_VIDEO_TITLE.get(),
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
        VideoCodecSelectorContent(state = state)
    }
}
