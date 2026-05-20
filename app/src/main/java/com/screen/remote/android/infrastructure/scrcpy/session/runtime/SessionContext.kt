package com.screen.remote.android.infrastructure.scrcpy.session.runtime

import com.screen.remote.android.core.domain.model.ScrcpyOptions
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
) {
    fun currentSession(): Session? = currentSessionProvider()

    fun requireSession(): Session = currentSession() ?: error("会话不存在")

    fun currentOptions(): ScrcpyOptions? = currentSession()?.options

    fun emit(event: SessionEvent) {
        currentSession()?.handleEvent(event)
    }
}
