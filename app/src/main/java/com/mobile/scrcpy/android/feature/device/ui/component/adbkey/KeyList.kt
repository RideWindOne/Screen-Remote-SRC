/**
 * ADB 密钥列表组件
 * 
 * 包含密钥信息显示和编辑相关的 Composable 函数
 */
package com.mobile.scrcpy.android.feature.device.ui.component.adbkey

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.i18n.CommonTexts

/**
 * 密钥信息项（只读显示）
 */
@Composable
fun KeyInfoItem(
    label: String,
    value: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 密钥编辑项（可折叠的编辑框）
 */
@Composable
fun KeyEditItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isVisible: Boolean,
    onVisibilityToggle: (() -> Unit)?,
    focusRequester: FocusRequester?,
    txtHide: String = CommonTexts.BUTTON_HIDE.get(),
    txtShow: String = CommonTexts.BUTTON_SHOW.get(),
) {
    // 统一处理：所有密钥都可折叠
    Column(modifier = Modifier.fillMaxWidth()) {
        // 标题行（始终显示，列表高度）
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppDimens.listItemHeight)
                    .clickable { onVisibilityToggle?.invoke() }
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (isVisible) txtHide else txtShow,
                    fontSize = 13.sp,
                    color = Color(0xFF007AFF),
                )
                Icon(
                    if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isVisible) txtHide else txtShow,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF007AFF),
                )
            }
        }

        // 输入框区域
        if (!isVisible) {
            // 折叠状态：单行，显示隐藏内容（列表高度）
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .clickable { onVisibilityToggle?.invoke() }
                            .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "••••••••••••••••••••••••••••••••••••••••",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        } else {
            // 展开状态：多行输入框，显示明文
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        ),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 3,
                maxLines = 8,
            )
        }
    }

    // 自动聚焦
    LaunchedEffect(isVisible) {
        if (isVisible && focusRequester != null) {
            kotlinx.coroutines.delay(50)
            focusRequester.requestFocus()
        }
    }
}
