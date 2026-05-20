package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ConnectionStep
import com.screen.remote.android.core.domain.model.StepStatus
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.infrastructure.scrcpy.session.Session
import com.screen.remote.android.infrastructure.scrcpy.session.model.AdbConnectionContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.AdbIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ComponentState
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardRemovalContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardSetupContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionComponent
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerPushContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerStartContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import com.screen.remote.android.infrastructure.scrcpy.session.model.completedSummary
import com.screen.remote.android.infrastructure.scrcpy.session.model.logSummary
import com.screen.remote.android.infrastructure.scrcpy.session.model.startedSummary
import com.screen.remote.android.infrastructure.scrcpy.session.model.summary

internal fun Session.handleAdbConnecting() {
    runtime.updateProgress(ConnectionStep.ADB_CONNECT, StepStatus.RUNNING, AdbTexts.ADB_CONNECTING.get())
    runtime.updateSessionState(SessionState.AdbConnecting)
}

internal fun Session.handleAdbVerifying() {
    runtime.updateProgress(ConnectionStep.ADB_CONNECT, StepStatus.RUNNING, AdbTexts.ADB_VERIFYING.get())
}

internal fun Session.handleAdbConnected(context: AdbConnectionContext) {
    runtime.updateProgress(ConnectionStep.ADB_CONNECT, StepStatus.SUCCESS, AdbTexts.ADB_CONNECTED.get())
    runtime.updateSessionState(SessionState.AdbConnected(context))
    runtime.updateComponentState(SessionComponent.AdbConnection, ComponentState.Connected)
    LogManager.d(LogTags.SCRCPY_CLIENT, "ADB 已连接: deviceId=${context.deviceId}, serial=${context.serial}")
}

internal fun Session.handleAdbDisconnected(issue: AdbIssue) {
    runtime.updateProgress(ConnectionStep.ADB_CONNECT, StepStatus.FAILED, issue.progressMessage())
    runtime.updateSessionState(SessionState.AdbDisconnected(issue))
    runtime.updateComponentState(SessionComponent.AdbConnection, ComponentState.Disconnected)
}

internal fun Session.handleServerPushing(context: ServerPushContext) {
    runtime.updateProgress(ConnectionStep.PUSH_SERVER, StepStatus.RUNNING, RemoteTexts.REMOTE_PUSHING_SERVER.get())
    LogManager.d(LogTags.SCRCPY_CLIENT, "正在推送 scrcpy-server: ${context.startedSummary()}")
}

internal fun Session.handleServerPushed(context: ServerPushContext) {
    runtime.updateProgress(ConnectionStep.PUSH_SERVER, StepStatus.SUCCESS, RemoteTexts.REMOTE_SERVER_PUSHED.get())
    LogManager.d(LogTags.SCRCPY_CLIENT, "scrcpy-server 推送完成: ${context.completedSummary()}")
}

internal fun Session.handleServerPushFailed(issue: ServerIssue) {
    runtime.updateProgress(ConnectionStep.PUSH_SERVER, StepStatus.FAILED, issue.pushFailedProgressMessage())
    runtime.updateSessionState(SessionState.ServerFailed(issue))
}

internal fun Session.handleServerStarting() {
    runtime.updateProgress(ConnectionStep.START_SERVER, StepStatus.RUNNING, RemoteTexts.REMOTE_STARTING_SERVER.get())
    runtime.updateSessionState(SessionState.ServerStarting)
}

internal fun Session.handleServerStarted(context: ServerStartContext) {
    runtime.updateProgress(ConnectionStep.START_SERVER, StepStatus.SUCCESS, RemoteTexts.REMOTE_SERVER_STARTED.get())
    runtime.updateSessionState(SessionState.ServerStarted(context))
    runtime.updateComponentState(SessionComponent.ScrcpyServer, ComponentState.Running)
    LogManager.d(LogTags.SCRCPY_CLIENT, "scrcpy-server 已启动: scid=${context.scid}")
}

internal fun Session.handleServerFailed(issue: ServerIssue) {
    runtime.updateProgress(
        ConnectionStep.START_SERVER,
        StepStatus.FAILED,
        issue.startFailedProgressMessage(),
    )
    runtime.updateSessionState(SessionState.ServerFailed(issue))
    runtime.updateComponentState(SessionComponent.ScrcpyServer, ComponentState.Error(issue.message))
}

internal fun Session.handleForwardSetting() {
    runtime.updateProgress(ConnectionStep.ADB_FORWARD, StepStatus.RUNNING, RemoteTexts.REMOTE_SETTING_FORWARD.get())
}

internal fun Session.handleForwardSetup(
    localPort: Int,
    remoteSocket: String,
    context: ForwardSetupContext,
) {
    runtime.updateProgress(
        ConnectionStep.ADB_FORWARD,
        StepStatus.SUCCESS,
        context.progressMessage(localPort, remoteSocket),
    )
    LogManager.d(LogTags.SCRCPY_CLIENT, "Forward 已建立: ${context.logSummary(localPort, remoteSocket)}")
}

internal fun Session.handleForwardRemoved(
    localPort: Int,
    context: ForwardRemovalContext,
) {
    LogManager.d(
        LogTags.SCRCPY_CLIENT,
        "Forward 已移除: ${context.summary(localPort)}",
    )
}

internal fun Session.handleForwardFailed(issue: ForwardIssue) {
    runtime.updateProgress(
        ConnectionStep.ADB_FORWARD,
        StepStatus.FAILED,
        issue.progressMessage(),
    )
    LogManager.e(LogTags.SCRCPY_CLIENT, "Forward 建立失败: ${issue.summary()}")
}
