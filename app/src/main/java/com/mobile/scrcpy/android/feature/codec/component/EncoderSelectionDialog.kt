package com.mobile.scrcpy.android.feature.codec.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.codec.component.encoder.DetectingCard
import com.mobile.scrcpy.android.feature.codec.component.encoder.EmptyCard
import com.mobile.scrcpy.android.feature.codec.component.encoder.EncoderListSection
import com.mobile.scrcpy.android.feature.codec.component.encoder.EncoderOptionsSection
import com.mobile.scrcpy.android.feature.codec.component.encoder.ErrorCard
import com.mobile.scrcpy.android.feature.codec.component.encoder.getAudioEncoderDialogConfig
import com.mobile.scrcpy.android.feature.codec.component.encoder.getVideoEncoderDialogConfig
import com.mobile.scrcpy.android.feature.codec.component.encoder.matchesAudioCodecFilter
import com.mobile.scrcpy.android.feature.codec.component.encoder.matchesVideoCodecFilter
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.infrastructure.adb.connection.EncoderInfo
import kotlinx.coroutines.launch

/**
 * 编码器选择对话框
 * 
 * 文件拆分说明：
 * - encoder/VideoEncoderSection.kt - 视频编码器配置逻辑
 * - encoder/AudioEncoderSection.kt - 音频编码器配置逻辑
 * - encoder/EncoderListComponents.kt - 编码器列表UI组件
 * 
 * 本文件保留主对话框逻辑和状态管理
 */

/**
 * 通用编码器选择对话框
 *
 * @param encoderType 编码器类型（视频或音频）
 * @param sessionId 会话 ID（可选，用于保存检测结果）
 * @param host 设备主机地址
 * @param port 设备端口
 * @param currentEncoder 当前选中的编码器名称
 * @param cachedEncoders 缓存的编码器列表
 * @param onDismiss 关闭对话框回调
 * @param onEncoderSelected 选择编码器回调
 * @param onEncodersDetected 检测到编码器后的回调（用于更新缓存）
 */
@Composable
fun EncoderSelectionDialog(
    encoderType: EncoderType,
    sessionId: String? = null,
    host: String,
    port: String,
    currentEncoder: String = "",
    cachedEncoders: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onEncoderSelected: (String) -> Unit = {},
    onEncodersDetected: (List<String>) -> Unit = {},
) {
    var selectedEncoder by remember { mutableStateOf(currentEncoder) }
    var customEncoderName by remember { mutableStateOf(currentEncoder) }
    var detectedEncoders by remember { mutableStateOf<List<EncoderInfo>>(emptyList()) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectError by remember { mutableStateOf<String?>(null) }
    var usedCache by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val adbConnectionManager = remember { AdbConnectionManager.getInstance(context) }

    // 根据编码器类型获取配置
    val config =
        remember(encoderType, detectedEncoders) {
            when (encoderType) {
                EncoderType.VIDEO -> getVideoEncoderDialogConfig(detectedEncoders)
                EncoderType.AUDIO -> getAudioEncoderDialogConfig(detectedEncoders)
            }
        }

    // 检测编码器的函数
    fun detectEncoders(forceRefresh: Boolean = false) {
        if (host.isBlank()) {
            detectError = SessionTexts.ENCODER_ERROR_INPUT_HOST.get()
            return
        }

        scope.launch {
            isDetecting = true
            detectError = null
            usedCache = false

            try {
                // 优先使用缓存（除非强制刷新）
                if (!forceRefresh && cachedEncoders.isNotEmpty()) {
                    detectedEncoders =
                        cachedEncoders.map { name ->
                            when (encoderType) {
                                EncoderType.VIDEO -> EncoderInfo.Video(name = name, mimeType = "")
                                EncoderType.AUDIO -> EncoderInfo.Audio(name = name, mimeType = "")
                            }
                        }
                    usedCache = true
                    isDetecting = false
                    return@launch
                }

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

                // 检测编码器
                val result = connection.detectEncoders(context)
                if (result.isSuccess) {
                    val detectionResult = result.getOrNull()
                    if (detectionResult != null) {
                        // 根据类型选择对应的编码器列表
                        detectedEncoders =
                            when (encoderType) {
                                EncoderType.VIDEO -> detectionResult.videoEncoders
                                EncoderType.AUDIO -> detectionResult.audioEncoders
                            }

                        if (detectedEncoders.isEmpty()) {
                            detectError = config.noEncodersStatus
                        } else {
                            // 更新缓存
                            onEncodersDetected(detectedEncoders.map { it.name })
                        }
                    } else {
                        detectError = SessionTexts.ERROR_DETECTION_FAILED.get()
                    }
                } else {
                    detectError =
                        "${SessionTexts.ERROR_DETECTION_FAILED.get()}: ${result.exceptionOrNull()?.message}"
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

    DialogPage(
        title = config.title,
        onDismiss = { saveSelection() },
        showBackButton = true,
        rightButtonText = SessionTexts.ENCODER_REFRESH_BUTTON.get(),
        onRightButtonClick = { detectEncoders(forceRefresh = true) },
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
                    onCodecTestClick = { },
                )
            }
        }

        // 检测到的编码器
        SectionTitle(
            if (usedCache) {
                "${config.sectionTitle} (${SessionTexts.LABEL_CACHED.get()})"
            } else {
                config.sectionTitle
            },
        )
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
                    matchesCodecFilter = when (encoderType) {
                        EncoderType.VIDEO -> ::matchesVideoCodecFilter
                        EncoderType.AUDIO -> ::matchesAudioCodecFilter
                    },
                )
            }
        }
    }
}
