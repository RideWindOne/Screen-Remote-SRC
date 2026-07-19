package com.screen.remote.android.infrastructure.scrcpy.connection

import java.io.EOFException

/** Expected transport shutdowns are lifecycle signals, not component failures requiring stack traces. */
internal fun Throwable.isExpectedConnectionClosure(): Boolean =
    generateSequence(this) { it.cause }
        .any { error ->
            error is EOFException ||
                error.javaClass.simpleName == "AdbConnectionClosedException" ||
                EXPECTED_CONNECTION_CLOSURE_MARKERS.any { marker ->
                    error.message?.contains(marker, ignoreCase = true) == true
                }
        }

private val EXPECTED_CONNECTION_CLOSURE_MARKERS =
    listOf(
        "connection lost while reading stream",
        "socket closed",
        "stream closed",
        "closed channel",
        "connection reset",
        "broken pipe",
        "transport endpoint is not connected",
    )
