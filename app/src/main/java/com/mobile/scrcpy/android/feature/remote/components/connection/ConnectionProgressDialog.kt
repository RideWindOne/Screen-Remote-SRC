package com.mobile.scrcpy.android.feature.remote.components.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.AppTextSizes
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.DialogContainer
import com.mobile.scrcpy.android.core.designsystem.component.DialogHeader
import com.mobile.scrcpy.android.core.domain.model.ConnectionProgress
import com.mobile.scrcpy.android.core.domain.model.StepStatus
import com.mobile.scrcpy.android.core.domain.model.getDisplayText
import com.mobile.scrcpy.android.core.domain.model.getIcon
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import kotlinx.coroutines.launch

/**
 * 连接进度对话框
 *
 * @param progressList 连接进度列表
 * @param onDismiss 关闭对话框回调（仅在失败时可关闭）
 */
@Composable
fun ConnectionProgressDialog(
    progressList: List<ConnectionProgress>,
    onDismiss: () -> Unit = {},
) {
    // 检查是否有失败的步骤
    val hasFailed = progressList.any { it.status == StepStatus.FAILED }

    // 自动滚动到最新项
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(progressList.size) {
        if (progressList.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(progressList.size - 1)
            }
        }
    }

    Dialog(
        onDismissRequest = { if (hasFailed) onDismiss() },
        properties =
            DialogProperties(
                dismissOnBackPress = hasFailed,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        DialogContainer(widthRatio = 0.6f, maxHeightRatio = 0.5f) {
            val txtConnecting = CommonTexts.STATUS_CONNECTING.get()
            val txtFailed = CommonTexts.CONNECTION_FAILED_TITLE.get()

            // 标题栏
            DialogHeader(
                title = if (hasFailed) txtFailed else txtConnecting,
                onDismiss = onDismiss,
                showBackButton = false,
                leftButtonText = if (hasFailed) null else " ", // 未失败时占位，不显示按钮
            )

            // 进度列表
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(progressList) { progress ->
                    ConnectionProgressItem(progress)
                }
            }

            // 失败时显示关闭按钮
            if (hasFailed) {
                AppDivider()

                TextButton(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                ) {
                    val txtCancel = rememberText(CommonTexts.BUTTON_CANCEL)
                    Text(
                        text = txtCancel,
                        fontSize = AppTextSizes.sectionTitle,
                        color = AppColors.iOSBlue,
                    )
                }
            }
        }
    }
}

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
