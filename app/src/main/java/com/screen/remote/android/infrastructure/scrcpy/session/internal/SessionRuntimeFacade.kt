package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.core.domain.model.ConnectionStep
import com.screen.remote.android.core.domain.model.StepStatus
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionStateMachine
import com.screen.remote.android.infrastructure.scrcpy.session.model.ComponentState
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionComponent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionComponentStateSnapshot
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import kotlinx.coroutines.flow.StateFlow

internal class SessionRuntimeFacade(
    private val stateStore: SessionStateStore,
    private val componentStateStore: SessionComponentStateStore,
    private val runtimeBindings: SessionRuntimeBindings,
) {
    val sessionState: StateFlow<SessionState> = stateStore.sessionState
    val componentSnapshot: StateFlow<SessionComponentStateSnapshot> = componentStateStore.componentSnapshot

    fun bind(
        stateMachine: ConnectionStateMachine?,
        reconnectCallback: (() -> Unit)?,
    ) {
        runtimeBindings.setStateMachine(stateMachine)
        runtimeBindings.setReconnectCallback(reconnectCallback)
    }

    fun updateProgress(
        step: ConnectionStep,
        status: StepStatus,
        message: String,
    ) {
        runtimeBindings.updateProgress(step, status, message)
    }

    fun updateSessionState(state: SessionState) {
        stateStore.updateSessionState(state)
    }

    fun updateComponentState(
        component: SessionComponent,
        state: ComponentState,
    ): SessionComponentStateSnapshot = componentStateStore.updateComponentState(component, state)

    fun clearComponentStates() {
        componentStateStore.clear()
    }

    fun updateSocketExpectation(
        expectedSocketCount: Int,
        audioEnabled: Boolean,
    ) {
        componentStateStore.updateSocketExpectation(expectedSocketCount, audioEnabled)
        runtimeBindings.setSocketExpectation(expectedSocketCount, audioEnabled)
    }

    fun reconnectAttempts(): Int = runtimeBindings.reconnectAttempts()

    fun incrementReconnectAttempts() {
        runtimeBindings.incrementReconnectAttempts()
    }

    fun resetReconnectAttempts() {
        runtimeBindings.resetReconnectAttempts()
    }

    fun tryConsumeDecoderRecoveryAttempt(maxAttempts: Int): Boolean =
        runtimeBindings.tryConsumeDecoderRecoveryAttempt(maxAttempts)

    fun invokeReconnectCallback() {
        runtimeBindings.invokeReconnectCallback()
    }

    fun expectedSocketCount(): Int = runtimeBindings.expectedSocketCount()

    fun audioEnabled(): Boolean = runtimeBindings.audioEnabled()
}
