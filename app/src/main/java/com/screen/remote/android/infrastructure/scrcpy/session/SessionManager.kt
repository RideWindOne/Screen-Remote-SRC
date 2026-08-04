package com.screen.remote.android.infrastructure.scrcpy.session

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.storage.SessionStorage
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderResolutionRecoveryRequest
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext

/**
 * 活跃会话管理器。
 *
 * 当前只维护一个运行中的会话实例，但保留实例化形态，
 * 为后续多会话运行时留出扩展空间。
 */
class SessionManager(
    private val onDecoderResolutionRecoveryRequest: (DecoderResolutionRecoveryRequest?) -> Unit = {},
) {
    @Volatile
    private var activeSession: Session? = null

    val current: Session
        get() = activeSession ?: error("No active session; call start() first")

    val currentOrNull: Session?
        get() = activeSession

    fun createContext(): SessionContext = SessionContext(currentSessionProvider = { activeSession })

    fun start(
        options: ScrcpyOptions,
        storage: SessionStorage,
        onVideoResolution: (Int, Int) -> Unit = { _, _ -> },
    ): Session {
        activeSession?.let { previous ->
            val previousDeviceId = previous.deviceIdentifier
            LogManager.w(LogTags.SCRCPY_CLIENT, "The session already exists, clean it up first: $previousDeviceId")
            stop()
            ScrcpyEventBus.clearDeviceState(previousDeviceId)
        }

        return Session(
            options,
            storage,
            onVideoResolution,
            onDecoderResolutionRecoveryRequest,
        ).also { activeSession = it }
    }

    fun stop() {
        val session = activeSession ?: return
        activeSession = null
        LogManager.d(LogTags.SCRCPY_CLIENT, "Stop session: ${session.deviceIdentifier}, sessionId=${session.sessionId}")
        session.cleanup()
    }
}
