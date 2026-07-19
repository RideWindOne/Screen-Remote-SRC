package com.screen.remote.android.infrastructure.scrcpy.session

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.storage.SessionStorage
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext

/**
 * 活跃会话管理器。
 *
 * 当前只维护一个运行中的会话实例，但保留实例化形态，
 * 为后续多会话运行时留出扩展空间。
 */
class SessionManager {
    @Volatile
    private var activeSession: Session? = null

    val current: Session
        get() = activeSession ?: error("当前没有活跃会话，请先调用 start()")

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
            LogManager.w(LogTags.SCRCPY_CLIENT, "会话已存在，先清理: $previousDeviceId")
            stop()
            ScrcpyEventBus.clearDeviceState(previousDeviceId)
        }

        return Session(options, storage, onVideoResolution).also { activeSession = it }
    }

    fun stop() {
        val session = activeSession ?: return
        activeSession = null
        LogManager.d(LogTags.SCRCPY_CLIENT, "停止会话: ${session.deviceIdentifier}, sessionId=${session.sessionId}")
        session.cleanup()
    }
}
