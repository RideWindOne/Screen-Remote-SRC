package com.mobile.scrcpy.android.infrastructure.scrcpy.session.runtime

/**
 * 兼容旧链路的运行时注册点。
 *
 * 目标是把全局兼容入口保留在 session 域内，
 * 而不是继续让 ScrcpyClient 暴露全局会话管理器。
 */
object SessionRuntimeRegistry {
    @Volatile
    private var store: SessionRuntimeStore? = null

    fun install(store: SessionRuntimeStore) {
        this.store = store
    }

    fun currentStore(): SessionRuntimeStore? = store

    fun createContext(): SessionContext = SessionContext { store?.currentOrNull }
}
