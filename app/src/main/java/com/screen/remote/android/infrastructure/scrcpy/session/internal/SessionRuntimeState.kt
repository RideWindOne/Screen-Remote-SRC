package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.core.domain.model.ConnectionStep
import com.screen.remote.android.core.domain.model.StepStatus
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionStateMachine
import com.screen.remote.android.infrastructure.scrcpy.session.model.ComponentState
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionComponent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionComponentStateSnapshot
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import com.screen.remote.android.infrastructure.scrcpy.session.model.toSessionComponentStateSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 单个 Session 唯一的可变运行态。 */
internal class SessionRuntimeState {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val componentStates = mutableMapOf<SessionComponent, ComponentState>()
    private var expectedSocketCount = DEFAULT_SOCKET_COUNT
    private var audioEnabled = true
    private val _componentSnapshot = MutableStateFlow(buildComponentSnapshot())
    val componentSnapshot: StateFlow<SessionComponentStateSnapshot> = _componentSnapshot.asStateFlow()

    private var reconnectAttempts = 0
    private var decoderRecoveryAttempts = 0
    private var reconnectCallback: (() -> Unit)? = null
    private var stateMachine: ConnectionStateMachine? = null

    fun bind(
        stateMachine: ConnectionStateMachine?,
        reconnectCallback: (() -> Unit)?,
    ) {
        this.stateMachine = stateMachine
        this.reconnectCallback = reconnectCallback
    }

    fun updateProgress(
        step: ConnectionStep,
        status: StepStatus,
        message: String,
    ) {
        stateMachine?.updateProgress(step, status, message)
    }

    fun updateSessionState(state: SessionState) {
        if (state is SessionState.Connected) {
            reconnectAttempts = 0
        }
        _sessionState.value = state
    }

    fun updateComponentState(
        component: SessionComponent,
        state: ComponentState,
    ): SessionComponentStateSnapshot {
        componentStates[component] = state
        return buildComponentSnapshot().also { _componentSnapshot.value = it }
    }

    fun clearComponentStates() {
        componentStates.clear()
        expectedSocketCount = DEFAULT_SOCKET_COUNT
        audioEnabled = true
        _componentSnapshot.value = buildComponentSnapshot()
    }

    fun updateSocketExpectation(
        expectedSocketCount: Int,
        audioEnabled: Boolean,
    ) {
        this.expectedSocketCount = expectedSocketCount
        this.audioEnabled = audioEnabled
        _componentSnapshot.value = buildComponentSnapshot()
    }

    fun reconnectAttempts(): Int = reconnectAttempts

    fun incrementReconnectAttempts() {
        reconnectAttempts++
    }

    fun resetReconnectAttempts() {
        reconnectAttempts = 0
    }

    fun tryConsumeDecoderRecoveryAttempt(maxAttempts: Int): Boolean {
        if (decoderRecoveryAttempts >= maxAttempts) return false
        decoderRecoveryAttempts++
        return true
    }

    fun invokeReconnectCallback() {
        reconnectCallback?.invoke()
    }

    fun expectedSocketCount(): Int = expectedSocketCount

    fun audioEnabled(): Boolean = audioEnabled

    private fun buildComponentSnapshot(): SessionComponentStateSnapshot =
        componentStates.toMap().toSessionComponentStateSnapshot(
            expectedSocketCount = expectedSocketCount,
            audioEnabled = audioEnabled,
        )

    private companion object {
        const val DEFAULT_SOCKET_COUNT = 3
    }
}
