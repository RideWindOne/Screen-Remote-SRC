package com.screen.remote.android.infrastructure.scrcpy.session.runtime

import com.screen.remote.android.infrastructure.scrcpy.session.Session
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent

/**
 * 会话上下文
 *
 * 用显式 provider 包装当前运行态会话，避免基础设施层直接依赖全局单例。
 * 第一阶段保留兼容入口，后续可继续替换为真正的会话作用域依赖注入。
 */
class SessionContext(
    private val currentSessionProvider: () -> Session?,
    private val boundSession: Session? = null,
) {
    fun currentSession(): Session? =
        boundSession?.takeIf { currentSessionProvider() === it }
            ?: if (boundSession == null) currentSessionProvider() else null

    fun requireSession(): Session = currentSession() ?: error("Session does not exist")

    /**
     * 将上下文绑定到当前会话实例。长生命周期协程和解码线程必须使用绑定上下文，
     * 避免旧会话的迟到事件被投递到随后创建的新会话。
     */
    fun bindCurrent(): SessionContext = SessionContext(currentSessionProvider, requireSession())

    fun emit(event: SessionEvent) {
        currentSession()?.handleEvent(event)
    }
}
