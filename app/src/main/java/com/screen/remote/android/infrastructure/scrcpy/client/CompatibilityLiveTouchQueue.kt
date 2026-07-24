package com.screen.remote.android.infrastructure.scrcpy.client

internal data class CompatibilityLiveTouchEvent(
    val action: Int,
    val pointerId: Long,
    val x: Int,
    val y: Int,
)

internal class CompatibilityLiveTouchQueue {
    private val events = ArrayDeque<CompatibilityLiveTouchEvent>(3)

    fun offer(event: CompatibilityLiveTouchEvent) {
        if (event.action == ACTION_MOVE && events.lastOrNull()?.action == ACTION_MOVE) {
            events.removeLast()
        }
        events.addLast(event)
        check(events.size <= MAX_PENDING_EVENTS) {
            "Compatibility live touch queue exceeded its bounded capacity"
        }
    }

    fun poll(): CompatibilityLiveTouchEvent? = events.removeFirstOrNull()

    fun clear() {
        events.clear()
    }

    private companion object {
        const val ACTION_MOVE = 2
        const val MAX_PENDING_EVENTS = 3
    }
}
