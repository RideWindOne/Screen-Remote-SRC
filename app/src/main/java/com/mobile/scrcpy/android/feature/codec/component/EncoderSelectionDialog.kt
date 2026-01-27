package com.mobile.scrcpy.android.feature.codec.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.core.designsystem.component.DialogHeader
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import kotlinx.coroutines.launch

import com.mobile.scrcpy.android.core.i18n.CodecTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
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
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun EncoderSelectionDialog(
    encoderType: EncoderType,
    host: String,
    port: String,
    currentEncoder: String = "",
    onDismiss: () -> Unit,
    onEncoderSelected: (String) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val dialogHeight = screenHeight * 0.8f

    var selectedEncoder by remember { mutableStateOf(currentEncoder) }
    var customEncoderName by remember { mutableStateOf(currentEncoder) }
    var detectedEncoders by remember { mutableStateOf<List<EncoderInfo>>(emptyList()) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectError by remember { mutableStateOf<String?>(null) }

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
                    detectError = "${CommonTexts.ERROR_CONNECTION_FAILED.get()}: ${connectResult.exceptionOrNull()?.message}"
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
                            detectError = "${SessionTexts.ERROR_DETECTION_FAILED.get()}: ${result.exceptionOrNull()?.message}"
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
                            detectError = "${SessionTexts.ERROR_DETECTION_FAILED.get()}: ${result.exceptionOrNull()?.message}"
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

    // 自动检测一次
    LaunchedEffect(Unit) {
        detectEncoders()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(dialogHeight),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                DialogHeader(
                    title = config.title,
                    onDismiss = onDismiss,
                    showBackButton = true,
                    rightButtonText = CommonTexts.BUTTON_DONE.get(),
                    onRightButtonClick = {
                        val encoder = when {
                            selectedEncoder.isNotEmpty() -> selectedEncoder
                            customEncoderName.isNotEmpty() -> customEncoderName
                            else -> ""
                        }
                        onEncoderSelected(encoder)
                        onDismiss()
                    }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    // 搜索和筛选状态
                    var searchText by remember { mutableStateOf("") }
                    var codecTypeFilter by remember { mutableStateOf(config.filterOptions.first()) }
                    
                    // 编码器选项
                    SectionTitle(SessionTexts.SECTION_ENCODER_OPTIONS.get())
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
                        showCodecTest = config.showCodecTest
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 检测到的编码器
                    SectionTitle(config.sectionTitle)

                    when {
                        isDetecting -> {
                            DetectingCard(
                                status = config.detectingStatus,
                                host = host,
                                port = port
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
                                encoderType = encoderType
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 刷新编码器按钮
                    Button(
                        onClick = { detectEncoders() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        enabled = !isDetecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF007AFF),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(SessionTexts.ENCODER_REFRESH_BUTTON.get())
                    }
                }
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
    port: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$host:${port.ifBlank { "5555" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFFEBEE)
    ) {
        Text(
            text = error,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFC62828)
        )
    }
}

/**
 * 空状态卡片
 */
@Composable
private fun EmptyCard(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    showCodecTest: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 默认编码器选项
        androidx.compose.material3.RadioButton(
            selected = selectedEncoder.isEmpty() && customEncoderName.isEmpty(),
            onClick = onDefaultEncoderSelected
        )
        Text(
            text = SessionTexts.LABEL_DEFAULT_ENCODER.get(),
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 自定义编码器输入
        androidx.compose.material3.OutlinedTextField(
            value = customEncoderName,
            onValueChange = onCustomEncoderNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(SessionTexts.PLACEHOLDER_CUSTOM_ENCODER.get()) },
            singleLine = true
        )
        
        // 编解码器测试按钮（仅音频编码器显示）
        if (showCodecTest) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { /* TODO: 打开编解码器测试页面 */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text(SessionTexts.LABEL_TEST_AUDIO_DECODER.get())
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
    encoderType: EncoderType
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 搜索框
        androidx.compose.material3.OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(SessionTexts.PLACEHOLDER_SEARCH_ENCODER.get()) },
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 筛选选项
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            filterOptions.forEach { option ->
                androidx.compose.material3.FilterChip(
                    selected = codecTypeFilter == option,
                    onClick = { onCodecTypeFilterChange(option) },
                    label = { Text(option) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 编码器列表
        val filteredEncoders = encoders.filter { encoder ->
            val matchesSearch = searchText.isEmpty() || 
                encoder.name.contains(searchText, ignoreCase = true) ||
                encoder.mimeType.contains(searchText, ignoreCase = true)
            
            val matchesFilter = codecTypeFilter == filterOptions.first() || 
                encoder.mimeType.contains(codecTypeFilter, ignoreCase = true)
            
            matchesSearch && matchesFilter
        }
        
        filteredEncoders.forEach { encoder ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (selectedEncoder == encoder.name) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                onClick = { onEncoderSelected(encoder) }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = encoder.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedEncoder == encoder.name) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = encoder.mimeType,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedEncoder == encoder.name) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        
        // 显示筛选结果统计
        Text(
            text = "${CodecTexts.CODEC_TEST_FOUND_COUNT.get()} ${filteredEncoders.size} ${
                if (encoderType == EncoderType.VIDEO) 
                    CodecTexts.CODEC_TEST_AUDIO_CODECS.get() 
                else 
                    CodecTexts.CODEC_TEST_AUDIO_CODECS.get()
            }",
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
