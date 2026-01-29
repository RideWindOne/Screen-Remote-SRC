package com.mobile.scrcpy.android.feature.remote.components.connection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 连接进度显示组件（无窗口，直接显示文字）
 * @param progressText 进度文本
 */
@Composable
fun ConnectionProgressBox(progressText: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 进度文字靠左上角显示，无背景
        Box(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            progressText()
        }

        // 转圈圈在底部居中（距离底部 50dp）
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 46.3.dp)) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(50.dp),
                strokeWidth = 4.dp,
            )
        }
    }
}
