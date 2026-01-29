package com.mobile.scrcpy.android.feature.codec.component

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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenu
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenuItem
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.i18n.CodecTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.codec.ui.CodecTestScreen
import com.mobile.scrcpy.android.feature.session.ui.component.CompactTextField
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import kotlinx.coroutines.launch

/**
 * 通用编码器选择对话框
 *
 * @param encoderType 编码器类型（视频或音频）
 * @param host 设备主机地址
 * @param port 设备端口
 * @param currentEncoder 当前选中的编码器名称
 * @param onDismiss 关闭对话框回调
 * @param onEncoderSelected 选择编码器回调
 */
@Composable
fun EncoderSelectionDialog(
    encoderType: EncoderType,
    host: String,
    port: String,
    currentEncoder: String = "",
    onDismiss: () -> Unit,
    onEncoderSelected: (String) -> Unit = {},
) {
    var selectedEncoder by remember { mutableStateOf(currentEncoder) }
    var customEncoderName by remember { mutableStateOf(currentEncoder) }
    var detectedEncoders by remember { mutableStateOf<List<EncoderInfo>>(emptyList()) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectError by remember { mutableStateOf<String?>(null) }
    var showCodecTestScreen by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val adbConnectionManager = remember { AdbConnectionManager.getInstance(context) }

    // 根据编码器类型获取配置
    val config = remember(encoderType) { getEncoderDialogConfig(encoderType) }

    // 检测编码器的函数
    fun detectEncoders() {
        if (host.isBlank()) {
            detectError = SessionTexts.ENCODER_ERROR_INPUT_HOST.get()
            return
        }

        scope.launch {
            isDetecting = true
            detectError = null

            try {
                val deviceId = "$host:${port.ifBlank { "5555" }}"
                val connectResult = adbConnectionManager.connectDevice(host, port.toIntOrNull() ?: 5555)

                if (connectResult.isFailure) {
                    detectError = "${connectResult.exceptionOrNull()?.message}"
                    isDetecting = false
                    return@launch
                }

                val connection = adbConnectionManager.getConnection(deviceId)
                if (connection == null) {
                    detectError = SessionTexts.ERROR_CANNOT_GET_CONNECTION.get()
                    isDetecting = false
                    return@launch
                }

                // 根据类型检测不同的编码器
                when (encoderType) {
                    EncoderType.VIDEO -> {
                        val result = connection.detectVideoEncoders(context)
                        if (result.isSuccess) {
                            detectedEncoders = result.getOrNull() ?: emptyList()
                            if (detectedEncoders.isEmpty()) {
                                detectError = config.noEncodersStatus
                            }
                        } else {
                            detectError =
                                "${SessionTexts.ERROR_DETECTION_FAILED.get()}: ${result.exceptionOrNull()?.message}"
                        }
                    }

                    EncoderType.AUDIO -> {
                        val result = connection.detectAudioEncoders(context)
                        if (result.isSuccess) {
                            detectedEncoders = result.getOrNull() ?: emptyList()
                            if (detectedEncoders.isEmpty()) {
                                detectError = config.noEncodersStatus
                            }
                        } else {
                            detectError =
                                "${SessionTexts.ERROR_DETECTION_FAILED.get()}: ${result.exceptionOrNull()?.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                detectError = "${SessionTexts.ERROR_DETECTION_EXCEPTION.get()}: ${e.message}"
            } finally {
                isDetecting = false
            }
        }
    }

    // 保存选择的函数
    fun saveSelection() {
        val encoder =
            when {
                selectedEncoder.isNotEmpty() -> selectedEncoder
                customEncoderName.isNotEmpty() -> customEncoderName
                else -> ""
            }
        onEncoderSelected(encoder)
        onDismiss()
    }

    // 自动检测一次
    LaunchedEffect(Unit) {
        detectEncoders()
    }

    // 搜索和筛选状态
    var searchText by remember { mutableStateOf("") }
    var codecTypeFilter by remember { mutableStateOf(config.filterOptions.first()) }

    // 显示编解码器测试屏幕
    if (showCodecTestScreen) {
        CodecTestScreen(
            onBack = { showCodecTestScreen = false },
        )
        return
    }

    DialogPage(
        title = config.title,
        onDismiss = { saveSelection() },
        showBackButton = true,
        rightButtonText = SessionTexts.ENCODER_REFRESH_BUTTON.get(),
        onRightButtonClick = { detectEncoders() },
        rightButtonEnabled = !isDetecting,
        maxHeightRatio = 0.8f,
        enableScroll = true,
        horizontalPadding = 16.dp,
        verticalSpacing = 8.dp,
    ) {
        // 编码器选项
        SectionTitle(SessionTexts.SECTION_ENCODER_OPTIONS.get())

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column {
                EncoderOptionsSection(
                    selectedEncoder = selectedEncoder,
                    customEncoderName = customEncoderName,
                    onDefaultEncoderSelected = {
                        selectedEncoder = ""
                        customEncoderName = ""
                    },
                    onCustomEncoderNameChange = {
                        customEncoderName = it
                        selectedEncoder = ""
                    },
                    showCodecTest = config.showCodecTest,
                    onCodecTestClick = { showCodecTestScreen = true },
                )
            }
        }

        // 检测到的编码器
        SectionTitle(config.sectionTitle)
        when {
            isDetecting -> {
                DetectingCard(
                    status = config.detectingStatus,
                    host = host,
                    port = port,
                )
            }

            detectError != null -> {
                ErrorCard(error = detectError!!)
            }

            detectedEncoders.isEmpty() -> {
                EmptyCard(message = config.noEncodersStatus)
            }

            else -> {
                EncoderListSection(
                    encoders = detectedEncoders,
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    codecTypeFilter = codecTypeFilter,
                    onCodecTypeFilterChange = { codecTypeFilter = it },
                    filterOptions = config.filterOptions,
                    selectedEncoder = selectedEncoder,
                    onEncoderSelected = { encoder ->
                        selectedEncoder = encoder.name
                        customEncoderName = encoder.name
                    },
                    encoderType = encoderType,
                )
            }
        }
    }
}

/**
 * 检测中卡片
 */
@Composable
private fun DetectingCard(
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
private fun ErrorCard(error: String) {
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
private fun EmptyCard(message: String) {
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
private fun EncoderOptionsSection(
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
                text = SessionTexts.LABEL_DEFAULT_ENCODER.get(),
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

        // 编解码器测试按钮（仅音频编码器显示）
        if (showCodecTest) {
            AppDivider()
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(AppDimens.listItemHeight)
                        .clickable { onCodecTestClick() }
                        .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = SessionTexts.LABEL_TEST_AUDIO_DECODER.get(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF007AFF),
                )
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
 * 编码器列表区域
 */
@Composable
private fun EncoderListSection(
    encoders: List<EncoderInfo>,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    codecTypeFilter: String,
    onCodecTypeFilterChange: (String) -> Unit,
    filterOptions: List<String>,
    selectedEncoder: String,
    onEncoderSelected: (EncoderInfo) -> Unit,
    encoderType: EncoderType,
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
                modifier = Modifier.weight(1f),
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
                    Modifier
                        .widthIn(min = 60.dp, max = 80.dp)
                        .wrapContentWidth(),
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
