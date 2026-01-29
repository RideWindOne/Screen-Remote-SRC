package com.mobile.scrcpy.android.feature.session.ui.component

import android.view.Surface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.designsystem.component.HelpIcon
import com.mobile.scrcpy.android.core.designsystem.component.IOSSwitch
import com.mobile.scrcpy.android.feature.codec.component.CodecMapper

/**
 * 紧凑型文本输入框
 */
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = 10.dp),
        textStyle =
            TextStyle(
                fontSize = 15.sp,
                lineHeight = 15.sp,
                color = if (isError) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurface,
            ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) Color(0xFFFF3B30).copy(alpha = 0.6f) else Color(0xFF959595),
                    )
                }
                innerTextField()
            }
        },
    )
}

/**
 * 带标签的文本输入框
 */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    helpText: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.wrapContentWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            )
            if (helpText != null) {
                HelpIcon(helpText = helpText)
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .padding(start = 10.dp),
            textStyle =
                TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 15.sp,
                    color = if (isError) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.wrapContentWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError) Color(0xFFFF3B30).copy(alpha = 0.6f) else Color(0xFF959595),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

/**
 * 紧凑型开关行
 */
@Composable
fun CompactSwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    helpText: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            )
            if (helpText != null) {
                HelpIcon(helpText = helpText)
            }
        }
        IOSSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * 紧凑型可点击行
 */
@Composable
fun CompactClickableRow(
    text: String,
    trailingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true,
    helpText: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            )
            if (helpText != null) {
                HelpIcon(helpText = helpText)
            }
        }
        Row(
            modifier = Modifier.clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showArrow) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFE5E5EA),
                )
            }
        }
    }
}

/**
 * 通用标题+内容行组件
 * 支持自适应宽度的标题和内容区域
 */
@Composable
fun LabeledRow(
    label: String,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier.padding(start = 10.dp),
    helpText: String? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.wrapContentWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            )
            if (helpText != null) {
                HelpIcon(helpText = helpText)
            }
        }
        Box(
            modifier = contentModifier.weight(1f, fill = false),
            contentAlignment = Alignment.CenterEnd,
        ) {
            content()
        }
    }
}

/**
 * 标题+输入框（基于 LabeledRow）
 */
@Composable
fun LabeledInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    helpText: String? = null,
) {
    LabeledRow(
        label = label,
        modifier = modifier,
        helpText = helpText,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.wrapContentWidth(),
            textStyle =
                TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 15.sp,
                    color = if (isError) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.wrapContentWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError) Color(0xFFFF3B30).copy(alpha = 0.6f) else Color(0xFF959595),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

/**
 * 标题+开关（基于 LabeledRow）
 */
@Composable
fun LabeledSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    helpText: String? = null,
) {
    LabeledRow(
        label = label,
        modifier = modifier,
        helpText = helpText,
    ) {
        IOSSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * 标题+可点击文本（基于 LabeledRow）
 */
@Composable
fun LabeledClickableRow(
    label: String,
    trailingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
    helpText: String? = null,
) {
    LabeledRow(
        label = label,
        modifier = modifier,
        helpText = helpText,
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = leadingIconTint ?: MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showArrow) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFFE5E5EA),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * 标题+下拉菜单行（基于 LabeledRow）
 * 支持在文本位置显示下拉菜单
 *
 * 使用示例：
 * ```
 * LabeledDropdownRow(
 *     label = "编码格式",
 *     trailingText = "H.264",
 *     onClick = { expanded = true },
 *     helpText = "选择视频编码格式"
 * ) {
 *     IOSStyledDropdownMenu(...) { ... }
 * }
 * ```
 */
@Composable
fun LabeledDropdownRow(
    label: String,
    trailingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    helpText: String? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (helpText != null) {
                HelpIcon(helpText = helpText)
            }
        }
        Box {
            Row(
                modifier =
                    Modifier
                        .clickable(onClick = onClick)
                        .fillMaxHeight(1f)
                        .padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}
