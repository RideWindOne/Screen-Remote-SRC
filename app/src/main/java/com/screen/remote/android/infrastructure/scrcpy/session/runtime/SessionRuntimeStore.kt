package com.screen.remote.android.infrastructure.scrcpy.session.runtime

import com.screen.remote.android.infrastructure.scrcpy.session.Session

/**
 * 当前运行态会话存储。
 *
 * 把“活跃会话访问”从生命周期管理中拆出来，
 * 便于后续继续演进到更明确的 runtime/store 边界。
 */
class SessionRuntimeStore {
    @Volatile
    private var activeSession: Session? = null

    val current: Session
        get() = activeSession ?: error("当前没有活跃会话，请先调用 start()")

    val currentOrNull: Session?
        get() = activeSession

    fun exists(): Boolean = activeSession != null

    fun createContext(): SessionContext = SessionContext { activeSession }

    internal fun attach(session: Session) {
        activeSession = session
    }

    internal fun detach(): Session? {
        val session = activeSession
        activeSession = null
        return session
    }
}
