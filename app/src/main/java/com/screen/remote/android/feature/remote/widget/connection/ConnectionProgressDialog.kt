package com.screen.remote.android.feature.remote.widget.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screen.remote.android.core.common.AppColors
import com.screen.remote.android.core.common.AppTextSizes
import com.screen.remote.android.core.domain.model.ConnectionProgress
import com.screen.remote.android.core.domain.model.StepStatus
import com.screen.remote.android.core.domain.model.getDisplayText
import com.screen.remote.android.core.domain.model.getIcon

/**
 * 单个连接进度项
 */
@Composable
private fun ConnectionProgressItem(progress: ConnectionProgress) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 状态图标
        Text(
            text = progress.status.getIcon(),
            fontSize = 16.sp,
        )

        // 步骤信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // 步骤名称
            Text(
                text = progress.step.getDisplayText(),
                fontSize = AppTextSizes.sectionTitle,
                fontWeight = FontWeight.Medium,
                color =
                    when (progress.status) {
                        StepStatus.FAILED -> AppColors.error
                        StepStatus.SUCCESS -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )

            // 消息
            if (progress.message.isNotEmpty()) {
                Text(
                    text = progress.message,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 错误信息
            if (progress.error != null) {
                Text(
                    text = progress.error,
                    fontSize = 11.sp,
                    color = AppColors.error,
                )
            }
        }
    }
}
