package com.mobile.scrcpy.android.feature.codec.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenu
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenuItem
import com.mobile.scrcpy.android.core.i18n.CodecTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.codec.model.CodecInfo
import com.mobile.scrcpy.android.feature.session.ui.component.CompactTextField

/**
 * 筛选下拉菜单组件
 */
@Composable
fun FilterDropdown(
    label: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppDimens.listItemHeight)
                    .widthIn(min = 50.dp)
                    .clickable { showMenu = true }
                    .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IOSStyledDropdownMenu(
            expanded = showMenu,
            offset = DpOffset(0.dp, 108.dp),
            onDismissRequest = { showMenu = false },
        ) {
            options.forEach { option ->
                IOSStyledDropdownMenuItem(
                    text = option,
                    onClick = {
                        onOptionSelected(option)
                        showMenu = false
                    },
                )
            }
        }
    }
}

/**
 * 编解码器选项容器（默认+自定义）
 */
@Composable
fun CodecOptionsSection(
    selectedCodec: String,
    customCodecName: String,
    onDefaultSelected: () -> Unit,
    onCustomCodecChange: (String) -> Unit,
    placeholderText: String,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            // 默认选项
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(AppDimens.listItemHeight)
                        .clickable(onClick = onDefaultSelected)
                        .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = SessionTexts.LABEL_DEFAULT.get(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Icon(
                    imageVector =
                        if (selectedCodec.isEmpty() && customCodecName.isEmpty()) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.RadioButtonUnchecked
                        },
                    contentDescription = null,
                    tint =
                        if (selectedCodec.isEmpty() && customCodecName.isEmpty()) {
                            Color(0xFF007AFF)
                        } else {
                            Color(0xFFE5E5EA)
                        },
                    modifier = Modifier.size(22.dp),
                )
            }

            AppDivider()

            // 自定义输入
            CompactTextField(
                value = customCodecName,
                onValueChange = onCustomCodecChange,
                placeholder = placeholderText,
                keyboardType = KeyboardType.Text,
            )
        }
    }
}

/**
 * 筛选栏（搜索框+多个筛选下拉菜单）
 */
@Composable
fun CodecFilterBar(
    searchText: String,
    onSearchChange: (String) -> Unit,
    searchPlaceholder: String,
    filters: List<FilterConfig>,
    searchWeight: Float = 2f,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 搜索框
        Surface(
            modifier = Modifier.weight(searchWeight),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            CompactTextField(
                value = searchText,
                onValueChange = onSearchChange,
                placeholder = searchPlaceholder,
                keyboardType = KeyboardType.Text,
            )
        }

        // 筛选下拉菜单（使用各自的权重）
        filters.forEach { filter ->
            FilterDropdown(
                label = filter.currentLabel,
                options = filter.options,
                onOptionSelected = filter.onOptionSelected,
                modifier = Modifier.weight(filter.weight),
            )
        }
    }
}

/**
 * 筛选配置
 */
data class FilterConfig(
    val currentLabel: String,
    val options: List<String>,
    val onOptionSelected: (String) -> Unit,
    val weight: Float = 1f, // 新增权重参数
)

/**
 * 编解码器列表项
 */
@Composable
fun CodecListItem(
    codec: CodecInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    showTestButton: Boolean = false,
    isTesting: Boolean = false,
    onTest: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onSelect)
                .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = codec.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = codec.type,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (codec.capabilities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = codec.capabilities,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 测试按钮
            if (showTestButton && onTest != null) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = CodecTexts.CODEC_TEST_BUTTON.get(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF007AFF),
                        modifier = Modifier.clickable(onClick = onTest),
                    )
                }
            }

            // 选中图标
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF007AFF),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * 编解码器列表
 */
@Composable
fun CodecList(
    codecs: List<CodecInfo>,
    selectedCodec: String,
    onCodecSelect: (CodecInfo) -> Unit,
    showTestButton: Boolean = false,
    testingCodec: String? = null,
    onTest: ((CodecInfo) -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            codecs.forEachIndexed { index, codec ->
                CodecListItem(
                    codec = codec,
                    isSelected = selectedCodec == codec.name,
                    onSelect = { onCodecSelect(codec) },
                    showTestButton = showTestButton,
                    isTesting = testingCodec == codec.name,
                    onTest =
                        if (onTest != null) {
                            { onTest(codec) }
                        } else {
                            null
                        },
                    enabled = testingCodec == null,
                )
                if (index < codecs.size - 1) {
                    AppDivider()
                }
            }
        }
    }
}

/**
 * 空状态提示
 */
@Composable
fun EmptyCodecState(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 编解码器统计信息
 */
@Composable
fun CodecCountInfo(
    count: Int,
    codecType: String,
) {
    Text(
        text = "${CodecTexts.CODEC_TEST_FOUND_COUNT.get()} $count $codecType",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
