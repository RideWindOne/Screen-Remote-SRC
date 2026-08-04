package com.screen.remote.android.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.common.IosDesignTokens
import com.screen.remote.android.core.designsystem.component.HelpIcon
import com.screen.remote.android.core.designsystem.component.IOSSwitch
import com.screen.remote.android.core.designsystem.component.SectionCard

/**
 * 设置卡片容器
 *
 * 用于包装一组相关的设置项，带标题和圆角卡片样式
 */
@Composable
fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) = SectionCard(title = title, content = content)

/**
 * 设置分隔线
 *
 * 用于分隔设置项
 */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = IosDesignTokens.standardHorizontalPadding),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = IosDesignTokens.dividerAlpha),
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
 * @param helpText 帮助说明文本（可选）
 * @param onClick 点击回调
 */
@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    showExternalIcon: Boolean = false,
    isDestructive: Boolean = false,
    isLink: Boolean = false,
    enabled: Boolean = true,
    helpText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
                .padding(horizontal = IosDesignTokens.standardHorizontalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IosDesignTokens.compactInlineSpacing),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        isDestructive -> MaterialTheme.colorScheme.error
                        isLink -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
            )
            if (helpText != null) {
                HelpIcon(helpText = helpText)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showExternalIcon) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "外部链接",
                    tint =
                        if (isLink) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(IosDesignTokens.externalIconSize),
                )
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
 * @param helpText 帮助说明文本（可选）
 * @param onCheckedChange 开关状态变化回调
 */
@Composable
fun SettingsSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    helpText: String? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = IosDesignTokens.standardHorizontalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IosDesignTokens.compactInlineSpacing),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.38f,
                        )
                    },
            )
            if (helpText != null) {
                HelpIcon(helpText = helpText)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(IosDesignTokens.compactInlineSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailingAction?.invoke()
            IOSSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}
