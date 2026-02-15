package com.mobile.scrcpy.android.infrastructure.scrcpy.connection.internal

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.domain.model.ScrcpyOptions
import com.mobile.scrcpy.android.infrastructure.media.codec.CodecSelectionResult
import com.mobile.scrcpy.android.infrastructure.media.codec.CodecSelector
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal.saveCodecSelection
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ServerIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ServerIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionEvent

/**
 * 编解码器检测与选择入口。
 *
 * 远程检测流程和本地选择策略已拆到协作文件。
 */
internal suspend fun ConnectionLifecycle.detectRemoteEncodersAfterPush(connection: com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection) {
    val session =
        sessionContext.currentSession() ?: run {
            sessionContext.emit(
                SessionEvent.SessionError(
                    SessionIssue(
                        kind = SessionIssueKind.SessionNotFound,
                        detail = "Session not found",
                    ),
                ),
            )
            return
        }

    val options = session.options
    val needDetectVideo = shouldDetectVideoCodec(options)
    val needDetectAudio = shouldDetectAudioCodec(options)

    if (!needDetectVideo && !needDetectAudio) {
        LogManager.d(LogTags.SCRCPY_CLIENT, "所有编解码器已配置，跳过检测")
        return
    }

    val (videoEncoderNames, audioEncoderNames) = fetchRemoteEncoders(connection, options) ?: return

    processCodecSelection(needDetectVideo, needDetectAudio, videoEncoderNames, audioEncoderNames, options)
}

internal suspend fun ConnectionLifecycle.processCodecSelection(
    needDetectVideo: Boolean,
    needDetectAudio: Boolean,
    videoEncoderNames: List<String>,
    audioEncoderNames: List<String>,
    options: ScrcpyOptions,
) {
    val videoResult = selectVideoCodecIfNeeded(needDetectVideo, videoEncoderNames, options)
    val audioResult = selectAudioCodecIfNeeded(needDetectAudio, audioEncoderNames, options)

    if (!validateCodecSelection(needDetectVideo, needDetectAudio, videoResult, audioResult)) {
        return
    }

    saveCodecSelection(videoResult, audioResult)
}

internal fun ConnectionLifecycle.shouldDetectVideoCodec(options: ScrcpyOptions): Boolean =
    when {
        options.userVideoEncoder.isNotBlank() && options.userVideoDecoder.isNotBlank() -> false
        options.userVideoEncoder.isNotBlank() && options.userVideoDecoder.isBlank() -> {
            shouldDetectDecoder(
                options.selectedVideoDecoder,
                options.userVideoEncoder,
                CodecSelector::inferVideoCodecFromName,
            )
        }

        options.userVideoEncoder.isBlank() && options.userVideoDecoder.isNotBlank() -> {
            shouldDetectEncoder(
                options.selectedVideoEncoder,
                options.userVideoDecoder,
                CodecSelector::inferVideoCodecFromName,
            )
        }

        else ->
            options.selectedVideoEncoder.isBlank() ||
                options.selectedVideoDecoder.isBlank() ||
                hasVideoCodecDrift(
                    preferredVideoCodec = options.preferredVideoCodec,
                    selectedEncoder = options.selectedVideoEncoder,
                    selectedDecoder = options.selectedVideoDecoder,
                )
    }

internal fun ConnectionLifecycle.shouldDetectAudioCodec(options: ScrcpyOptions): Boolean =
    when {
        options.userAudioEncoder.isNotBlank() && options.userAudioDecoder.isNotBlank() -> false
        options.userAudioEncoder.isNotBlank() && options.userAudioDecoder.isBlank() -> {
            shouldDetectDecoder(
                options.selectedAudioDecoder,
                options.userAudioEncoder,
                CodecSelector::inferAudioCodecFromName,
            )
        }

        options.userAudioEncoder.isBlank() && options.userAudioDecoder.isNotBlank() -> {
            shouldDetectEncoder(
                options.selectedAudioEncoder,
                options.userAudioDecoder,
                CodecSelector::inferAudioCodecFromName,
            )
        }

        else -> options.selectedAudioEncoder.isBlank() || options.selectedAudioDecoder.isBlank()
    }

internal fun ConnectionLifecycle.shouldDetectDecoder(
    selectedDecoder: String,
    userEncoder: String,
    inferCodec: (String) -> String,
): Boolean {
    if (selectedDecoder.isBlank()) {
        return true
    }
    return inferCodec(userEncoder) != inferCodec(selectedDecoder)
}

internal fun ConnectionLifecycle.shouldDetectEncoder(
    selectedEncoder: String,
    userDecoder: String,
    inferCodec: (String) -> String,
): Boolean {
    if (selectedEncoder.isBlank()) {
        return true
    }
    return inferCodec(userDecoder) != inferCodec(selectedEncoder)
}

internal fun ConnectionLifecycle.hasVideoCodecDrift(
    preferredVideoCodec: String,
    selectedEncoder: String,
    selectedDecoder: String,
): Boolean {
    val normalizedPreferred =
        preferredVideoCodec
            .ifBlank { return false }
            .let(CodecSelector::inferVideoCodecFromName)
    val encoderCodec = CodecSelector.inferVideoCodecFromName(selectedEncoder)
    val decoderCodec = CodecSelector.inferVideoCodecFromName(selectedDecoder)

    return encoderCodec != normalizedPreferred || decoderCodec != normalizedPreferred
}

internal fun ConnectionLifecycle.selectVideoCodecIfNeeded(
    needDetect: Boolean,
    encoderNames: List<String>,
    options: ScrcpyOptions,
): CodecSelectionResult? =
    if (needDetect) {
        CodecSelector.selectBestVideoCodec(
            remoteEncoders = encoderNames,
            userEncoder = options.userVideoEncoder.ifBlank { null },
            userDecoder = options.userVideoDecoder.ifBlank { null },
        )
    } else {
        null
    }

internal fun ConnectionLifecycle.selectAudioCodecIfNeeded(
    needDetect: Boolean,
    encoderNames: List<String>,
    options: ScrcpyOptions,
): CodecSelectionResult? =
    if (needDetect) {
        CodecSelector.selectBestAudioCodec(
            remoteEncoders = encoderNames,
            userEncoder = options.userAudioEncoder.ifBlank { null },
            userDecoder = options.userAudioDecoder.ifBlank { null },
        )
    } else {
        null
    }

internal fun ConnectionLifecycle.validateCodecSelection(
    needDetectVideo: Boolean,
    needDetectAudio: Boolean,
    videoResult: CodecSelectionResult?,
    audioResult: CodecSelectionResult?,
): Boolean {
    if ((needDetectVideo && videoResult == null) || (needDetectAudio && audioResult == null)) {
        LogManager.e(LogTags.SCRCPY_CLIENT, "编解码器选择失败")
        sessionContext.emit(
            SessionEvent.ServerFailed(
                ServerIssue(
                    kind = ServerIssueKind.CodecSelectionFailed,
                    detail = "编解码器选择失败",
                ),
            ),
        )
        return false
    }
    return true
}

internal suspend fun ConnectionLifecycle.saveCodecSelection(
    videoResult: CodecSelectionResult?,
    audioResult: CodecSelectionResult?,
) {
    try {
        val currentSession = sessionContext.currentSession()
        if (currentSession == null) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "会话不存在")
            return
        }

        LogManager.d(LogTags.SDL, "${currentSession.options}")
        LogManager.d(
            LogTags.SCRCPY_CLIENT,
            "保存编解码器选择: " +
                "视频编码器=${videoResult?.encoder ?: "跳过"}, " +
                "视频解码器=${videoResult?.decoder ?: "跳过"}, " +
                "视频格式=${videoResult?.codec ?: "跳过"}, " +
                "音频编码器=${audioResult?.encoder ?: "跳过"}, " +
                "音频解码器=${audioResult?.decoder ?: "跳过"}, " +
                "音频格式=${audioResult?.codec ?: "跳过"}",
        )

        currentSession.saveCodecSelection(
            videoEncoder = videoResult?.encoder ?: currentSession.options.selectedVideoEncoder,
            audioEncoder = audioResult?.encoder ?: currentSession.options.selectedAudioEncoder,
            videoDecoder = videoResult?.decoder ?: currentSession.options.selectedVideoDecoder,
            audioDecoder = audioResult?.decoder ?: currentSession.options.selectedAudioDecoder,
            preferredVideoCodec = videoResult?.codec ?: currentSession.options.preferredVideoCodec,
            preferredAudioCodec = audioResult?.codec ?: currentSession.options.preferredAudioCodec,
        )

        LogManager.d(LogTags.SCRCPY_CLIENT, "已保存编解码器选择到会话 ${currentSession.sessionId}")
    } catch (e: Exception) {
        LogManager.w(LogTags.SCRCPY_CLIENT, "保存编解码器选择失败: ${e.message}")
    }
}
