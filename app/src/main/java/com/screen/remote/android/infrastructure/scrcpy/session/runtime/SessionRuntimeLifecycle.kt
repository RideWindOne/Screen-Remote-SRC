package com.screen.remote.android.infrastructure.scrcpy.session.runtime

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.storage.SessionStorage
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.infrastructure.scrcpy.session.Session

internal class SessionRuntimeLifecycle(
    private val store: SessionRuntimeStore,
) {
    val current: Session
        get() = store.current

    val currentOrNull: Session?
        get() = store.currentOrNull

    fun start(
        options: ScrcpyOptions,
        storage: SessionStorage,
        onVideoResolution: (Int, Int) -> Unit = { _, _ -> },
    ): Session {
        store.currentOrNull?.let { activeSession ->
            LogManager.w(LogTags.SCRCPY_CLIENT, "会话已存在，先清理: ${activeSession.deviceIdentifier}")
            val oldDeviceId = activeSession.deviceIdentifier
            stop()
            ScrcpyEventBus.clearDeviceState(oldDeviceId)
        }

        return Session(options, storage, onVideoResolution).also { session ->
            store.attach(session)
        }
    }

    fun stop() {
        store.detach()?.let { session ->
            LogManager.d(LogTags.SCRCPY_CLIENT, "停止会话: ${session.deviceIdentifier}, sessionId=${session.sessionId}")
            session.cleanup()
        }
    }

    fun exists(): Boolean = store.exists()

    val deviceIdentifier: String?
        get() = store.currentOrNull?.deviceIdentifier

    val sessionId: String?
        get() = store.currentOrNull?.sessionId
}
