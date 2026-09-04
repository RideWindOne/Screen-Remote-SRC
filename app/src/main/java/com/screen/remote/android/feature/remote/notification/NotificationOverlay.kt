package com.screen.remote.android.feature.remote.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 通知提示覆盖层
 * 在远程控制界面顶部显示被控端新通知横幅
 */
@Composable
fun NotificationOverlay(
    notification: DeviceNotification?,
    onDismiss: () -> Unit,
    durationMs: Long = 4000,
) {
    var visible by remember { mutableStateOf(false) }
    var currentNotification by remember { mutableStateOf<DeviceNotification?>(null) }

    LaunchedEffect(notification) {
        if (notification != null) {
            currentNotification = notification
            visible = true
            delay(durationMs)
            visible = false
            delay(300) // 等待退出动画完成
            if (currentNotification?.key == notification.key) {
                onDismiss()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible && currentNotification != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            currentNotification?.let { notif ->
                NotificationBanner(
                    notification = notif,
                    onClick = {
                        visible = false
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun NotificationBanner(
    notification: DeviceNotification,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth(0.92f)
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xE61A1A1A))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 应用图标占位（彩色圆点）
        Box(
            modifier =
                Modifier
                    .width(36.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF4A90D9)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = notification.packageName.takeLastWhile { it != '.' }.firstOrNull()?.uppercase() ?: "N",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            if (notification.title.isNotBlank()) {
                Text(
                    text = notification.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            if (notification.text.isNotBlank()) {
                Text(
                    text = notification.text,
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 包名（小字）
            Text(
                text = notification.packageName,
                color = Color(0xFF888888),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
