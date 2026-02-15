package com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.ScrcpyConstants
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.CleanupContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ReconnectIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ReconnectStateContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.Session
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionState
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.summary

internal fun Session.handleSessionError(issue: SessionIssue) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "会话错误: ${issue.message}")
    runtime.updateSessionState(SessionState.Failed(issue))
}

internal fun Session.handleRequestReconnect(issue: ReconnectIssue) {
    val reason = issue.message
    val currentAttempts = runtime.reconnectAttempts()
    if (currentAttempts >= ScrcpyConstants.MAX_RECONNECT_ATTEMPTS) {
        LogManager.e(LogTags.SCRCPY_CLIENT, "重连次数已达上限，停止重连")
        runtime.updateSessionState(
            SessionState.Failed(
                SessionIssue(
                    kind = SessionIssueKind.RuntimeFailure,
                    detail = reason,
                ),
            ),
        )
        return
    }

    runtime.incrementReconnectAttempts()
    val newAttempts = runtime.reconnectAttempts()
    runtime.updateSessionState(
        SessionState.Reconnecting(
            ReconnectStateContext(
                attempt = newAttempts,
                issue = issue,
            ),
        ),
    )
    LogManager.d(
        LogTags.SCRCPY_CLIENT,
        "请求重连 (尝试 $newAttempts/${ScrcpyConstants.MAX_RECONNECT_ATTEMPTS}): $reason",
    )

    runtime.invokeReconnectCallback()
}

internal fun Session.handleRequestCleanup(context: CleanupContext) {
    LogManager.d(
        LogTags.SCRCPY_CLIENT,
        "请求清理会话: ${context.summary()}",
    )
    runtime.updateSessionState(SessionState.Idle)
    runtime.updateSocketExpectation(
        expectedSocketCount = 3,
        audioEnabled = true,
    )
    runtime.clearComponentStates()
    runtime.resetReconnectAttempts()
}
