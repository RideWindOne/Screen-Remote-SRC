package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.infrastructure.scrcpy.session.Session
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionComponentStateSnapshot
import com.screen.remote.android.infrastructure.scrcpy.session.model.decoderSummary
import com.screen.remote.android.infrastructure.scrcpy.session.model.infrastructureSummary
import com.screen.remote.android.infrastructure.scrcpy.session.model.socketSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

internal fun Session.observeComponentSnapshot(observerScope: CoroutineScope): Job =
    observerScope.launch {
        var previousSnapshot: SessionComponentStateSnapshot? = null

        componentSnapshot.drop(1).collect { snapshot ->
            val previous = previousSnapshot

            if (previous == null || previous.infrastructureConnection != snapshot.infrastructureConnection) {
                LogManager.d(
                    LogTags.SCRCPY_CLIENT,
                    "Component snapshot[infrastructure]: ${snapshot.infrastructureConnection.infrastructureSummary()}",
                )
            }

            if (previous == null || previous.socketConnections != snapshot.socketConnections) {
                LogManager.d(
                    LogTags.SCRCPY_CLIENT,
                    "Component snapshot [sockets]: ${snapshot.socketConnections.socketSummary()}",
                )
            }

            if (previous == null || previous.decoderConnection != snapshot.decoderConnection) {
                LogManager.d(
                    LogTags.SCRCPY_CLIENT,
                    "Component snapshot [decoders]: ${snapshot.decoderConnection.decoderSummary()}",
                )
            }

            previousSnapshot = snapshot
        }
    }
