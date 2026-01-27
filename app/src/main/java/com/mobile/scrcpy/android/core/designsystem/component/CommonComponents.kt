package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.AppDimens

/**
 * 统一的分组标题组件
 * 用于显示"通用"、"ADB 管理"、"远程设备"等分组标题
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.sectionTitleHeight)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 统一的分隔线组件
 * 用于卡片内部的分隔线
 */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

/**
 * 统一的 Dialog 标题栏组件
 * 支持三种布局模式：
 * 1. 左侧返回 + 中间标题 + 右侧占位
 * 2. 左侧取消 + 中间标题 + 右侧完成/保存
 * 3. 左侧返回 + 中间标题 + 右侧自定义按钮
 */
@Composable
fun DialogHeader(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    leftButtonText: String? = null,
    rightButtonText: String? = null,
    onRightButtonClick: (() -> Unit)? = null,
    rightButtonEnabled: Boolean = true,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧按钮
            if (leftButtonText != null) {
                TextButton(onClick = onDismiss) {
                    Text(
                        leftButtonText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.iOSBlue
                    )
                }
            } else if (showBackButton) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = AppColors.iOSBlue
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
            
            // 中间标题
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            // 右侧按钮或内容
            if (trailingContent != null) {
                trailingContent()
            } else if (rightButtonText != null && onRightButtonClick != null) {
                TextButton(
                    onClick = onRightButtonClick,
                    enabled = rightButtonEnabled
                ) {
                    Text(
                        rightButtonText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.iOSBlue
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
        
        AppDivider()
    }
}

/**
 * 统一样式的 DropdownMenu
 * 自动应用优化的背景色和阴影效果
 * 浅色模式：纯白背景 + 柔和阴影
 * 深色模式：#2C2C2E 背景 + 深色阴影
 * 
 * @param expanded 是否展开
 * @param onDismissRequest 关闭回调
 * @param modifier 修饰符
 * @param offset 偏移量
 * @param content 菜单项内容
 */
@Composable
fun IOSStyledDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    // 使用 MaterialTheme 覆盖 DropdownMenu 的默认背景色和形状
    val backgroundColor = MaterialTheme.colorScheme.surface
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = backgroundColor  // 使用主题的 surface 颜色
        ),
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(12.dp)  // DropdownMenu 使用 extraSmall 形状
        )
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = offset,
            modifier = modifier
                    .background(
                        color = backgroundColor,
                        shape = RoundedCornerShape(12.dp)
                    )
//                .shadow(
//                    elevation = 4.dp,
//                    shape = RoundedCornerShape(12.dp),
//                    ambientColor = Color.Black.copy(alpha = 0.08f),
//                    spotColor = Color.Black.copy(alpha = 0.12f)
//                )
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 50.dp)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally  // 内容居中
            ) {
                content()
            }
        }
    }
}

/**
 * iOS 风格的 DropdownMenuItem
 * 文字居中对齐
 * 
 * @param text 菜单项文字
 * @param onClick 点击回调
 * @param modifier 修饰符
 * @param textColor 文字颜色
 * @param fontSize 字体大小（可选，默认使用主题字体）
 */
@Composable
fun IOSStyledDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    DropdownMenuItem(
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val style = if (fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) {
                    MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize)
                } else {
                    MaterialTheme.typography.bodyMedium
                }
                Text(
                    text = text,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    style = style
                )
            }
        },
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    )
}

/**
 * iOS 风格的 DropdownMenuItem（自定义内容版本）
 * 支持完全自定义的 Composable 内容，文字居中对齐
 * 
 * @param text Composable 内容
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun IOSStyledDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenuItem(
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                text()
            }
        },
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    )
}
