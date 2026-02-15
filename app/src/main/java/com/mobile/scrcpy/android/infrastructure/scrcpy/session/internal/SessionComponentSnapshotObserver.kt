package com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.Session
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionComponentStateSnapshot
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.decoderSummary
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.infrastructureSummary
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.socketSummary
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
                    "组件快照[infrastructure]: ${snapshot.infrastructureConnection.infrastructureSummary()}",
                )
            }

            if (previous == null || previous.socketConnections != snapshot.socketConnections) {
                LogManager.d(
                    LogTags.SCRCPY_CLIENT,
                    "组件快照[sockets]: ${snapshot.socketConnections.socketSummary()}",
                )
            }

            if (previous == null || previous.decoderConnection != snapshot.decoderConnection) {
                LogManager.d(
                    LogTags.SCRCPY_CLIENT,
                    "组件快照[decoders]: ${snapshot.decoderConnection.decoderSummary()}",
                )
            }

            previousSnapshot = snapshot
        }
    }
