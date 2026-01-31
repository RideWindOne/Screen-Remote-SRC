package com.mobile.scrcpy.android.feature.codec.component.encoder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import com.mobile.scrcpy.android.feature.codec.component.EncoderType
import com.mobile.scrcpy.android.feature.session.ui.component.CompactTextField
import com.mobile.scrcpy.android.infrastructure.adb.connection.EncoderInfo

/**
 * 编码器列表组件
 * 
 * 提取自 EncoderSelectionDialog.kt
 * 包含编码器选项区域、列表区域和状态卡片
 */

/**
 * 检测中卡片
 */
@Composable
fun DetectingCard(
    status: String,
    host: String,
    port: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$host:${port.ifBlank { "5555" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * 错误卡片
 */
@Composable
fun ErrorCard(error: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFFFEBEE),
    ) {
        Text(
            text = error,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFC62828),
        )
    }
}

/**
 * 空状态卡片
 */
@Composable
fun EmptyCard(message: String) {
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
 * 编码器选项区域
 */
@Composable
fun EncoderOptionsSection(
    selectedEncoder: String,
    customEncoderName: String,
    onDefaultEncoderSelected: () -> Unit,
    onCustomEncoderNameChange: (String) -> Unit,
    showCodecTest: Boolean,
    onCodecTestClick: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 默认编码器选项
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppDimens.listItemHeight)
                    .clickable(onClick = onDefaultEncoderSelected)
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
                    if (selectedEncoder.isEmpty() && customEncoderName.isEmpty()) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.RadioButtonUnchecked
                    },
                contentDescription = null,
                tint =
                    if (selectedEncoder.isEmpty() && customEncoderName.isEmpty()) {
                        Color(0xFF007AFF)
                    } else {
                        Color(0xFFE5E5EA)
                    },
                modifier = Modifier.size(22.dp),
            )
        }

        AppDivider()

        // 自定义编码器输入
        CompactTextField(
            value = customEncoderName,
            onValueChange = onCustomEncoderNameChange,
            placeholder = SessionTexts.PLACEHOLDER_CUSTOM_ENCODER.get(),
            keyboardType = KeyboardType.Text,
        )
    }
}

/**
 * 编码器列表区域
 */
@Composable
fun EncoderListSection(
    encoders: List<EncoderInfo>,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    codecTypeFilter: String,
    onCodecTypeFilterChange: (String) -> Unit,
    filterOptions: List<String>,
    selectedEncoder: String,
    onEncoderSelected: (EncoderInfo) -> Unit,
    encoderType: EncoderType,
    matchesCodecFilter: (String, String, String) -> Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 搜索框和筛选选项在一行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 搜索框
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(5f),
            ) {
                CompactTextField(
                    value = searchText,
                    onValueChange = onSearchTextChange,
                    placeholder = SessionTexts.PLACEHOLDER_SEARCH_ENCODER.get(),
                    keyboardType = KeyboardType.Text,
                )
            }

            // 筛选选项
            var showFilterMenu by remember { mutableStateOf(false) }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier =
                    Modifier.weight(1.5f),
            ) {
                Row(
                    modifier =
                        Modifier
                            .height(AppDimens.listItemHeight)
                            .clickable { showFilterMenu = true }
                            .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = codecTypeFilter,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IOSStyledDropdownMenu(
                    expanded = showFilterMenu,
                    offset = DpOffset(0.dp, 100.dp),
                    onDismissRequest = { showFilterMenu = false },
                ) {
                    filterOptions.forEach { option ->
                        IOSStyledDropdownMenuItem(
                            text = option,
                            onClick = {
                                onCodecTypeFilterChange(option)
                                showFilterMenu = false
                            },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 编码器列表
        val filteredEncoders =
            encoders.filter { encoder ->
                val matchesSearch =
                    searchText.isEmpty() ||
                        encoder.name.contains(searchText, ignoreCase = true) ||
                        encoder.mimeType.contains(searchText, ignoreCase = true)

                val matchesFilter =
                    matchesCodecFilter(
                        encoder.mimeType,
                        codecTypeFilter,
                        filterOptions.first(),
                    )

                matchesSearch && matchesFilter
            }

        if (filteredEncoders.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column {
                    filteredEncoders.forEachIndexed { index, encoder ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onEncoderSelected(encoder) }
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = encoder.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = encoder.mimeType,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selectedEncoder == encoder.name) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF007AFF),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        if (index < filteredEncoders.size - 1) {
                            AppDivider()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 显示筛选结果统计
            Text(
                text = "${CodecTexts.CODEC_TEST_FOUND_COUNT.get()} ${filteredEncoders.size} ${
                    if (encoderType == EncoderType.VIDEO) {
                        CodecTexts.CODEC_TEST_VIDEO_CODECS.get()
                    } else {
                        CodecTexts.CODEC_TEST_AUDIO_CODECS.get()
                    }
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            EmptyCard(message = SessionTexts.STATUS_NO_ENCODERS_DETECTED.get())
        }
    }
}
