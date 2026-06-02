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

internal class SessionStateStore {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    fun updateSessionState(state: SessionState) {
        _sessionState.value = state
    }
}

internal class SessionComponentStateStore {
    private val _componentStates = MutableStateFlow<Map<SessionComponent, ComponentState>>(emptyMap())
    private var expectedSocketCount = 3
    private var audioEnabled = true
    private val _componentSnapshot =
        MutableStateFlow(
            emptyMap<SessionComponent, ComponentState>().toSessionComponentStateSnapshot(
                expectedSocketCount = expectedSocketCount,
                audioEnabled = audioEnabled,
            ),
        )
    val componentSnapshot: StateFlow<SessionComponentStateSnapshot> = _componentSnapshot.asStateFlow()

    fun updateComponentState(
        component: SessionComponent,
        state: ComponentState,
    ): SessionComponentStateSnapshot {
        val updatedStates =
            _componentStates.value.toMutableMap().apply {
                this[component] = state
            }
        val snapshot =
            updatedStates.toSessionComponentStateSnapshot(
                expectedSocketCount = expectedSocketCount,
                audioEnabled = audioEnabled,
            )
        _componentStates.value = updatedStates
        _componentSnapshot.value = snapshot
        return snapshot
    }

    fun updateSocketExpectation(
        expectedSocketCount: Int,
        audioEnabled: Boolean,
    ) {
        this.expectedSocketCount = expectedSocketCount
        this.audioEnabled = audioEnabled
        _componentSnapshot.value =
            _componentStates.value.toSessionComponentStateSnapshot(
                expectedSocketCount = expectedSocketCount,
                audioEnabled = audioEnabled,
            )
    }

    fun clear() {
        val clearedStates = emptyMap<SessionComponent, ComponentState>()
        _componentStates.value = clearedStates
        expectedSocketCount = 3
        audioEnabled = true
        _componentSnapshot.value =
            clearedStates.toSessionComponentStateSnapshot(
                expectedSocketCount = expectedSocketCount,
                audioEnabled = audioEnabled,
            )
    }
}

internal class SessionRuntimeBindings {
    private var reconnectAttempts = 0
    private var decoderRecoveryAttempts = 0
    private var onReconnectRequest: (() -> Unit)? = null
    private var stateMachine: ConnectionStateMachine? = null
    private var expectedSocketCount = 3
    private var audioEnabled = true

    fun setStateMachine(stateMachine: ConnectionStateMachine?) {
        this.stateMachine = stateMachine
    }

    fun setReconnectCallback(callback: (() -> Unit)?) {
        onReconnectRequest = callback
    }

    fun updateProgress(
        step: ConnectionStep,
        status: StepStatus,
        message: String,
    ) {
        stateMachine?.updateProgress(step, status, message)
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
        onReconnectRequest?.invoke()
    }

    fun setSocketExpectation(
        expectedSocketCount: Int,
        audioEnabled: Boolean,
    ) {
        this.expectedSocketCount = expectedSocketCount
        this.audioEnabled = audioEnabled
    }

    fun expectedSocketCount(): Int = expectedSocketCount

    fun audioEnabled(): Boolean = audioEnabled
}
