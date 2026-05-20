package com.screen.remote.android.core.common.manager

import com.screen.remote.android.core.common.LogTags

/**
 * 轻量级会话断点追踪器。
 *
 * 目标：
 * - 只记录每次会话的首个异常断点
 * - 输出统一格式，便于从长日志里定位“第一处坏掉的位置”
 * - 不参与状态机和重连逻辑，仅用于诊断
 */
object SessionIssueTracker {
    private var sessionId: String? = null
    private var deviceId: String? = null
    private var startedAtMs: Long = 0L
    private var firstIssueLogged = false

    @Synchronized
    fun begin(
        sessionId: String,
        deviceId: String,
        isReconnecting: Boolean,
    ) {
        this.sessionId = sessionId
        this.deviceId = deviceId
        startedAtMs = System.currentTimeMillis()
        firstIssueLogged = false

        LogManager.d(
            LogTags.SCRCPY_CLIENT,
            "DIAG session-start session=$sessionId device=$deviceId reconnecting=$isReconnecting",
        )
    }

    @Synchronized
    fun clear(reason: String) {
        if (startedAtMs != 0L) {
            LogManager.d(
                LogTags.SCRCPY_CLIENT,
                "DIAG session-clear session=${sessionId ?: "-"} device=${deviceId ?: "-"} reason=$reason",
            )
        }

        sessionId = null
        deviceId = null
        startedAtMs = 0L
        firstIssueLogged = false
    }

    fun record(
        source: String,
        detail: String,
    ) {
        val message =
            synchronized(this) {
                if (startedAtMs == 0L || firstIssueLogged) {
                    return
                }

                firstIssueLogged = true
                val elapsedMs = System.currentTimeMillis() - startedAtMs

                "DIAG first-break source=$source elapsed=${elapsedMs}ms " +
                    "session=${sessionId ?: "-"} device=${deviceId ?: "-"} detail=$detail"
            }

        LogManager.e(LogTags.SCRCPY_CLIENT, message)
    }
}
