package com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.Session
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.CodecDetectionContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.CodecDetectionSummary
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.CodecIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ComponentState
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.DecoderIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.DecoderType
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ReconnectIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ReconnectIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionComponent
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionState
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.reconnectDetail
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.summary

internal fun Session.handleDecoderStarted(decoderType: DecoderType) {
    runtime.updateComponentState(decoderType.toComponent(), ComponentState.Running)
    LogManager.d(LogTags.SCRCPY_CLIENT, "解码器已启动: ${decoderType.name}")
}

internal fun Session.handleDecoderStopped(decoderType: DecoderType) {
    runtime.updateComponentState(decoderType.toComponent(), ComponentState.Stopped)
    LogManager.d(LogTags.SCRCPY_CLIENT, "解码器已停止: ${decoderType.name}")
}

internal suspend fun Session.handleDecoderError(issue: DecoderIssue) {
    LogManager.e(LogTags.SCRCPY_CLIENT, issue.logMessage())

    if (runtime.sessionState.value is SessionState.Connected) {
        handleRequestReconnect(
            ReconnectIssue(
                kind = ReconnectIssueKind.DecoderError,
                detail = issue.reconnectDetail(),
            ),
        )
    }

    LogManager.e(LogTags.SCRCPY_CLIENT, "解码器错误详情: ${issue.summary()}")
}

internal fun Session.handleVideoEncoderDetecting(context: CodecDetectionContext) {
    val source = if (context.reusedUploadedServer) "复用已上传 server" else "重新 push server"
    LogManager.d(LogTags.SCRCPY_CLIENT, "正在检测视频编码器... source=$source")
}

internal fun Session.handleVideoEncoderDetected(summary: CodecDetectionSummary) {
    LogManager.d(
        LogTags.SCRCPY_CLIENT,
        "视频编码器检测完成: count=${summary.totalCount}, sample=${summary.sampleNames.joinToString()}, reusedServer=${summary.reusedUploadedServer}",
    )
}

internal fun Session.handleVideoEncoderDetectFailed(issue: CodecIssue) {
    val message = issue.message
    LogManager.e(LogTags.SCRCPY_CLIENT, "视频编码器检测失败: $message")
}

internal fun Session.handleVideoEncoderError(issue: CodecIssue) {
    val message = issue.message
    LogManager.e(LogTags.SCRCPY_CLIENT, "视频编码器错误: $message")
}

internal fun Session.handleAudioEncoderDetecting(context: CodecDetectionContext) {
    val source = if (context.reusedUploadedServer) "复用已上传 server" else "重新 push server"
    LogManager.d(LogTags.SCRCPY_CLIENT, "正在检测音频编码器... source=$source")
}

internal fun Session.handleAudioEncoderDetected(summary: CodecDetectionSummary) {
    LogManager.d(
        LogTags.SCRCPY_CLIENT,
        "音频编码器检测完成: count=${summary.totalCount}, sample=${summary.sampleNames.joinToString()}, reusedServer=${summary.reusedUploadedServer}",
    )
}

internal fun Session.handleAudioEncoderError(issue: CodecIssue) {
    val message = issue.message
    LogManager.e(LogTags.SCRCPY_CLIENT, "音频编码器错误: $message")
}

internal fun DecoderType.toComponent(): SessionComponent =
    when (this) {
        DecoderType.Video -> SessionComponent.VideoDecoder
        DecoderType.Audio -> SessionComponent.AudioDecoder
    }
