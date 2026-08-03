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
        if (events.size >= MAX_PENDING_EVENTS) {
            if (!removeOldestMove()) {
                events.removeFirst()
            }
        }
        events.addLast(event)
    }

    private fun removeOldestMove(): Boolean {
        if (events.none { it.action == ACTION_MOVE }) {
            return false
        }

        val withoutFirstMove = ArrayDeque<CompatibilityLiveTouchEvent>(events.size)
        var removed = false
        while (events.isNotEmpty()) {
            val next = events.removeFirst()
            if (!removed && next.action == ACTION_MOVE) {
                removed = true
                continue
            }
            withoutFirstMove.addLast(next)
        }
        events.addAll(withoutFirstMove)
        return true
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
