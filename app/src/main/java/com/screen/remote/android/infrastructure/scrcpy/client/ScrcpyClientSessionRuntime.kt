package com.screen.remote.android.infrastructure.scrcpy.client

import android.content.Context
import com.screen.remote.android.core.data.storage.SessionStorage
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionStateMachine
import com.screen.remote.android.infrastructure.scrcpy.session.SessionManager
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import com.screen.remote.android.infrastructure.scrcpy.session.internal.observeComponentSnapshot
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionRuntimeRegistry
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionRuntimeStore
import com.screen.remote.android.infrastructure.scrcpy.session.internal.createMonitorBus
import com.screen.remote.android.infrastructure.scrcpy.session.internal.initMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class ScrcpyClientSessionRuntime(
    private val context: Context,
) {
    private val sessionStore = SessionRuntimeStore()
    val sessionManager = SessionManager(sessionStore)
    val sessionContext: SessionContext = sessionStore.createContext()

    private var sessionMonitorInitialized = false
    private var sessionStateObserverJob: Job? = null
    private var componentSnapshotObserverJob: Job? = null

    init {
        SessionRuntimeRegistry.install(sessionStore)
    }

    suspend fun ensureSession(
        sessionId: String,
        options: ScrcpyOptions,
        onVideoResolution: (Int, Int) -> Unit,
    ) = sessionManager.currentOrNull ?: run {
        val storage = SessionStorage(context)
        val sessionOptions = storage.getOptions(sessionId) ?: options
        sessionManager.start(
            options = sessionOptions,
            storage = storage,
            onVideoResolution = onVideoResolution,
        )
    }

    fun ensureMonitor(
        stateMachine: ConnectionStateMachine,
        onReconnect: () -> Unit,
        observerScope: CoroutineScope,
        onSessionStateChanged: (SessionState) -> Unit,
    ) {
        if (sessionMonitorInitialized) return

        val session = sessionManager.current
        session.createMonitorBus()
        session.initMonitor(
            stateMachine = stateMachine,
            onReconnect = onReconnect,
        )

        sessionStateObserverJob?.cancel()
        sessionStateObserverJob =
            observerScope.launch {
                session.sessionState.collect { state ->
                    onSessionStateChanged(state)
                }
            }

        componentSnapshotObserverJob?.cancel()
        componentSnapshotObserverJob = session.observeComponentSnapshot(observerScope)

        sessionMonitorInitialized = true
    }

    fun clearMonitor() {
        sessionStateObserverJob?.cancel()
        sessionStateObserverJob = null
        componentSnapshotObserverJob?.cancel()
        componentSnapshotObserverJob = null
        sessionMonitorInitialized = false
    }
}
