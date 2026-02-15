package com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal

import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.AdbIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.DecoderIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ForwardIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ForwardSetupContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ServerIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SocketIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.targetSummary

internal fun AdbIssue.progressMessage(): String = "${AdbTexts.ADB_DISCONNECTED.get()}: ${message}"

internal fun ServerIssue.pushFailedProgressMessage(): String = "${RemoteTexts.REMOTE_PUSH_FAILED.get()}: ${message}"

internal fun ServerIssue.startFailedProgressMessage(): String = "${RemoteTexts.REMOTE_START_FAILED.get()}: ${message}"

internal fun ForwardSetupContext.progressMessage(
    localPort: Int,
    remoteSocket: String,
): String = "${RemoteTexts.REMOTE_FORWARD_SETUP.get()}: ${targetSummary(localPort, remoteSocket)}"

internal fun ForwardIssue.progressMessage(): String =
    "${RemoteTexts.REMOTE_FORWARD_FAILED.get()}: ${targetSummary()}: $message"

internal fun SocketIssue.progressMessage(): String =
    "${RemoteTexts.REMOTE_SOCKET_ERROR.get()}: ${socketType} - $message"

internal fun DecoderIssue.logMessage(): String = "解码器错误[${decoderType.name}]: $message"
