package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.core.domain.model.ConnectionStep
import com.screen.remote.android.core.domain.model.StepStatus
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionStateMachine

internal class SessionRuntimeBindings {
    private var reconnectAttempts = 0
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
