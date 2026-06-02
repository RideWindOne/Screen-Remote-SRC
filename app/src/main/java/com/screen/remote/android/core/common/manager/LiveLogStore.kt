package com.screen.remote.android.core.common.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveLogEntry(
    val id: Long,
    val level: String,
    val tag: String,
    val message: String,
)

/** A bounded, in-memory mirror of logs emitted through [LogManager]. */
object LiveLogStore {
    private const val MAX_ENTRIES = 1_000
    private val lock = Any()
    @Volatile
    private var enabled = false
    private var nextId = 0L
    private val mutableEntries = MutableStateFlow<List<LiveLogEntry>>(emptyList())

    val entries = mutableEntries.asStateFlow()

    fun append(
        level: String,
        tag: String,
        message: String,
    ) {
        if (!enabled) return
        val lines = message.lineSequence().filter { it.isNotEmpty() }.toList().ifEmpty { listOf("") }
        synchronized(lock) {
            if (!enabled) return
            val additions =
                lines.map { line ->
                    LiveLogEntry(
                        id = nextId++,
                        level = level.uppercase(),
                        tag = tag,
                        message = line,
                    )
                }
            mutableEntries.value = (mutableEntries.value + additions).takeLast(MAX_ENTRIES)
        }
    }

    fun setEnabled(value: Boolean) {
        synchronized(lock) {
            if (enabled == value) return
            enabled = value
            if (!value) mutableEntries.value = emptyList()
        }
    }

    fun clear() {
        synchronized(lock) {
            mutableEntries.value = emptyList()
        }
    }
}
