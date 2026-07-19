package com.screen.remote.android.feature.codec.component

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
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.EncoderCapability
import com.screen.remote.android.core.domain.model.toAddressEndpoint
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.designsystem.component.SectionTitle
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.feature.codec.component.encoder.DetectingCard
import com.screen.remote.android.feature.codec.component.encoder.EmptyCard
import com.screen.remote.android.feature.codec.component.encoder.EncoderListSection
import com.screen.remote.android.feature.codec.component.encoder.EncoderOptionsSection
import com.screen.remote.android.feature.codec.component.encoder.ErrorCard
import com.screen.remote.android.feature.codec.component.encoder.getAudioEncoderDialogConfig
import com.screen.remote.android.feature.codec.component.encoder.getVideoEncoderDialogConfig
import com.screen.remote.android.feature.codec.component.encoder.matchesAudioCodecFilter
import com.screen.remote.android.feature.codec.component.encoder.matchesVideoCodecFilter
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.adb.connection.raceAdbConnections
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionPurpose
import com.screen.remote.android.service.ScrcpyForegroundService
import kotlinx.coroutines.coroutineScope
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
 * @param connectionCandidates 当前编辑表单中的完整 ADB 连接候选列表
 * @param currentEncoder 当前选中的编码器名称
 * @param currentCodec 当前编码器对应的 scrcpy 格式
 * @param cachedEncoders 缓存的编码器列表
 * @param onDismiss 关闭对话框回调
 * @param onEncoderSelected 选择编码器回调
 * @param onEncodersDetected 检测到编码器后的回调（用于更新缓存）
 */
@Composable
fun EncoderSelectionDialog(
    encoderType: EncoderType,
    connectionCandidates: List<ConnectionCandidate>,
    currentEncoder: String = "",
    currentCodec: String = "",
    cachedEncoders: List<EncoderCapability> = emptyList(),
    onDismiss: () -> Unit,
    onEncoderSelected: (String, String?) -> Unit = { _, _ -> },
    onEncodersDetected: (List<EncoderCapability>) -> Unit = {},
) {
    var selectedEncoder by remember { mutableStateOf(currentEncoder) }
    var selectedCodec by remember { mutableStateOf(currentCodec) }
    var customEncoderName by remember { mutableStateOf(currentEncoder) }
    var detectedEncoders by remember { mutableStateOf<List<EncoderCapability>>(emptyList()) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectError by remember { mutableStateOf<String?>(null) }
    var usedCache by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val adbConnectionManager = remember { AdbConnectionManager.getInstance(context) }
    val detectionEndpoint =
        remember(connectionCandidates) {
            connectionCandidates
                .sortedBy(ConnectionCandidate::priority)
                .joinToString(" / ") { it.toAddressEndpoint() }
        }

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
        if (connectionCandidates.isEmpty()) {
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
                    val mediaType = if (encoderType == EncoderType.VIDEO) CodecMediaType.VIDEO else CodecMediaType.AUDIO
                    detectedEncoders = cachedEncoders.filter { it.mediaType == mediaType }
                    usedCache = true
                    isDetecting = false
                    return@launch
                }

                val existingDeviceIds =
                    connectionCandidates
                        .map(ConnectionCandidate::deviceIdentifier)
                        .filter(adbConnectionManager::isDeviceConnected)
                        .toSet()
                val connection =
                    coroutineScope {
                        raceAdbConnections(
                            candidates = connectionCandidates,
                            purpose = AdbConnectionPurpose.CODEC_TEST,
                            connectionManager = adbConnectionManager,
                            attemptScope = this,
                            cleanupScope = this,
                            logTag =
                                if (encoderType == EncoderType.VIDEO) {
                                    LogTags.VIDEO_CODEC_SELECTOR
                                } else {
                                    LogTags.AUDIO_CODEC_SELECTOR
                            },
                            logLabel = "Codec detection ADB",
                        ).result.getOrThrow()
                    }

                if (connection.deviceId !in existingDeviceIds) {
                    ScrcpyForegroundService.protectDevice(
                        context = context,
                        deviceId = connection.deviceId,
                        deviceName = connection.deviceInfo.name,
                    )
                }

                // 检测编码器
                // UI owns the result. Never persist through a SessionContext left bound by another session.
                val result =
                    connection.detectEncoders(
                        context = context,
                        skipPush = connection.getCachedCandidatePreflight()?.hasCompatibleScrcpyServer == true,
                        persistToBoundSession = false,
                    )
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
                            onEncodersDetected(detectedEncoders)
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
        val codec = selectedCodec.takeIf { selectedEncoder.isNotEmpty() }
        onEncoderSelected(encoder, codec)
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
                        selectedCodec = ""
                        customEncoderName = ""
                    },
                    onCustomEncoderNameChange = {
                        customEncoderName = it
                        selectedEncoder = ""
                        selectedCodec = ""
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
                    endpoint = detectionEndpoint,
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
                    selectedEncoderCodec = selectedCodec,
                    onEncoderSelected = { encoder ->
                        selectedEncoder = encoder.name
                        selectedCodec = encoder.codec
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
