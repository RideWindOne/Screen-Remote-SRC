package com.mobile.scrcpy.android.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenu
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle

/**
 * 设置卡片容器
 * 
 * 用于包装一组相关的设置项，带标题和圆角卡片样式
 */
@Composable
fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

/**
 * 设置分隔线
 * 
 * 用于分隔设置项
 */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

/**
 * 设置项（可点击）
 * 
 * @param title 标题
 * @param subtitle 副标题（可选）
 * @param showExternalIcon 是否显示外部链接图标
 * @param isDestructive 是否为危险操作（红色文字）
 * @param isLink 是否为链接（蓝色文字）
 * @param onClick 点击回调
 */
@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    showExternalIcon: Boolean = false,
    isDestructive: Boolean = false,
    isLink: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                isDestructive -> MaterialTheme.colorScheme.error
                isLink -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showExternalIcon) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "外部链接",
                    tint = if (isLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 设置项（带下拉菜单）
 * 
 * @param title 标题
 * @param subtitle 当前选中的值
 * @param expanded 菜单是否展开
 * @param onExpandedChange 菜单展开状态变化回调
 * @param menuContent 菜单内容
 */
@Composable
fun SettingsItemWithMenu(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .clickable { onExpandedChange(true) }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Box {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IOSStyledDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.widthIn(min = 80.dp)
            ) {
                menuContent()
            }
        }
    }
}

/**
 * 设置项（开关）
 * 
 * @param title 标题
 * @param checked 开关状态
 * @param enabled 是否启用
 * @param onCheckedChange 开关状态变化回调
 */
@Composable
fun SettingsSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.38f
            )
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.9f)
        )
    }
}

/**
 * 设置区域（已废弃，使用 SettingsCard 替代）
 */
@Deprecated("Use SettingsCard instead")
@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        Spacer(modifier = Modifier.height(16.dp))
    }
}
