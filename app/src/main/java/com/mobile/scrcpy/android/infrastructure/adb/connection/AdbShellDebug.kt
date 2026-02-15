package com.mobile.scrcpy.android.infrastructure.adb.connection

import com.mobile.scrcpy.android.core.common.manager.ShellDebugLog

private const val SHELL_LOG_MAX_PREVIEW_LENGTH = 240

internal fun logShellCommandStart(
    tag: String,
    command: String,
) {
    ShellDebugLog.d(tag) {
        "shell start: ${shellLogPreview(command)}"
    }
}

internal fun logShellCommandResult(
    tag: String,
    command: String,
    exitCode: Int,
    output: String,
    errorOutput: String,
) {
    ShellDebugLog.d(tag) {
        buildString {
            append("shell result: command=")
            append(shellLogPreview(command))
            append(" exit=")
            append(exitCode)
            append(" stdout=")
            append(shellLogPreview(output))
            append(" stderr=")
            append(shellLogPreview(errorOutput))
        }
    }
}

internal fun logShellCommandFailure(
    tag: String,
    command: String,
    error: Throwable,
) {
    ShellDebugLog.d(tag) {
        "shell failure: command=${shellLogPreview(command)} error=${error.javaClass.simpleName}: ${error.message ?: "<no-message>"}"
    }
}

internal fun logShellStreamOpen(
    tag: String,
    command: String,
) {
    ShellDebugLog.d(tag) {
        "shell stream open: ${shellLogPreview(command)}"
    }
}

internal fun logShellStreamReady(
    tag: String,
    command: String,
) {
    ShellDebugLog.d(tag) {
        "shell stream ready: ${shellLogPreview(command)}"
    }
}

internal fun shellLogPreview(
    value: String,
    maxLength: Int = SHELL_LOG_MAX_PREVIEW_LENGTH,
): String {
    val normalized =
        value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()

    if (normalized.isBlank()) {
        return "<empty>"
    }

    return if (normalized.length <= maxLength) {
        normalized
    } else {
        normalized.take(maxLength) + "..."
    }
}
