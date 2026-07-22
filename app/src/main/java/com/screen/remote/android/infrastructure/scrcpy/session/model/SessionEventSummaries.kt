package com.screen.remote.android.infrastructure.scrcpy.session.model

fun ServerPushContext.startedSummary(): String = "target=$targetPath"

fun ServerPushContext.completedSummary(): String = "target=$targetPath, durationMs=${durationMs ?: -1}"

fun targetSummary(
    localPort: Int,
    remoteSocket: String,
): String = "$localPort -> $remoteSocket"

fun ForwardSetupContext.logSummary(
    localPort: Int,
    remoteSocket: String,
): String = "${targetSummary(localPort, remoteSocket)} durationMs=$durationMs"

fun ForwardRemovalContext.summary(localPort: Int): String =
    "$localPort -> ${remoteSocket ?: "unknown"} trigger=$trigger"

fun ForwardIssue.targetSummary(): String = remoteSocket?.let { "$localPort -> $it" } ?: localPort.toString()

fun ForwardIssue.summary(): String = "kind=$kind target=${targetSummary()} detail=$message"

fun SocketConnectingContext.summary(): String =
    "localPort=$localPort expectedSockets=$expectedSocketCount audioEnabled=$audioEnabled"

fun SocketConnectContext.summary(socketType: SocketType): String =
    "type=$socketType localPort=$localPort dummyByteConfirmed=$dummyByteConfirmed"

fun SocketIssue.summary(): String = "type=$socketType kind=$kind detail=$message"

fun SocketDisconnectContext.reconnectDetail(socketType: SocketType): String =
    detail ?: "${socketType.name} socket disconnected ($kind)"

fun DecoderIssue.summary(): String = "type=$decoderType kind=$kind detail=$message"

fun DecoderIssue.reconnectDetail(): String = "${decoderType.name}: $message"
