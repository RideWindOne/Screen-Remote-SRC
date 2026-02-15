package com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal

import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ComponentState
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionComponent
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionComponentStateSnapshot
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.toSessionComponentStateSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
