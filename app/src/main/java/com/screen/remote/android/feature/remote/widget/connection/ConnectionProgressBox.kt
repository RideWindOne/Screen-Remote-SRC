package com.screen.remote.android.feature.remote.widget.connection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.screen.remote.android.feature.remote.widget.floating.BALL_A_SIZE_DP
import com.screen.remote.android.feature.remote.widget.floating.BALL_B_SIZE_DP
import com.screen.remote.android.feature.remote.widget.floating.FLOATING_BALL_INITIAL_BOTTOM_MARGIN_DP
import com.screen.remote.android.feature.remote.widget.floating.FLOATING_BALL_INITIAL_RIGHT_MARGIN_DP

/**
 * 连接进度显示组件（无窗口，直接显示文字）
 * @param progressText 进度文本
 */
@Composable
fun ConnectionProgressBox(progressText: @Composable () -> Unit) {
    val indicatorSize = BALL_A_SIZE_DP.dp
    val indicatorStroke = 3.dp
    val ballDeltaDp = ((BALL_A_SIZE_DP - BALL_B_SIZE_DP) / 2f).dp
    val portraitBottomPadding = FLOATING_BALL_INITIAL_BOTTOM_MARGIN_DP.dp - ballDeltaDp - 14.dp
    val landscapeEndPadding = FLOATING_BALL_INITIAL_RIGHT_MARGIN_DP.dp - ballDeltaDp - 12.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight

        Box(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            progressText()
        }

        if (isLandscape) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = landscapeEndPadding),
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(indicatorSize),
                    strokeWidth = indicatorStroke,
                )
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = portraitBottomPadding),
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(indicatorSize),
                    strokeWidth = indicatorStroke,
                )
            }
        }
    }
}
