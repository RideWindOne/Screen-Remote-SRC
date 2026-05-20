package com.screen.remote.android.infrastructure.scrcpy.session

import com.screen.remote.android.core.data.storage.SessionStorage
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionRuntimeLifecycle
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionRuntimeStore

/**
 * 活跃会话管理器。
 *
 * 当前只维护一个运行中的会话实例，但保留实例化形态，
 * 为后续多会话运行时留出扩展空间。
 */
class SessionManager(
    private val runtimeStore: SessionRuntimeStore = SessionRuntimeStore(),
) {
    private val lifecycle = SessionRuntimeLifecycle(runtimeStore)

    val current: Session
        get() = lifecycle.current

    val currentOrNull: Session?
        get() = lifecycle.currentOrNull

    fun start(
        options: ScrcpyOptions,
        storage: SessionStorage,
        onVideoResolution: (Int, Int) -> Unit = { _, _ -> },
    ): Session = lifecycle.start(options, storage, onVideoResolution)

    fun stop() = lifecycle.stop()

    fun exists(): Boolean = lifecycle.exists()

    val deviceIdentifier: String?
        get() = lifecycle.deviceIdentifier

    val sessionId: String?
        get() = lifecycle.sessionId
}
