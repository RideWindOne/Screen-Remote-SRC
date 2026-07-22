package com.screen.remote.android.feature.session.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.screen.remote.android.core.data.repository.SessionData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns all data loaded for one management-page lifetime. Each data source is single-flight:
 * callers reuse a successful cache, wait for an active request, and may retry after a failure.
 */
@Stable
internal class SessionManagementDataProvider {
    private data class AppRequestKey(
        val selectedFilters: Set<AppListFilter>,
        val forceRefresh: Boolean,
    )

    private data class AppRequest(
        val sessionId: String,
        val context: Context,
        val key: AppRequestKey,
        val waiters: MutableList<CompletableDeferred<Unit>> = mutableListOf(),
    )

    private var activeSessionId: String? = null
    private var sessionScope: CoroutineScope? = null
    private val deviceLoadMutex = Mutex()
    private var activeAppRequest: AppRequest? = null
    private var pendingAppRequest: AppRequest? = null

    var deviceSnapshot by mutableStateOf<DeviceDashboardSnapshot?>(null)
        private set

    var deviceErrorMessage by mutableStateOf<String?>(null)
        private set

    var deviceRefreshing by mutableStateOf(false)
        private set

    var fileBrowserState by mutableStateOf(SessionManagementFileBrowserState())
        private set

    fun startPrefetch(
        context: Context,
        sessionData: SessionData,
    ) {
        activate(sessionData.id)
        val scope = checkNotNull(sessionScope)
        scope.launch { loadDeviceInformation(context, sessionData) }
        scope.launch { prefetchRootFileInformation(sessionData.id) }
        scope.launch {
            loadApplicationInformation(
                context = context,
                sessionId = sessionData.id,
                selectedFilters = AppListFilter.defaultSelection,
            )
        }
    }

    suspend fun loadDeviceInformation(
        context: Context,
        sessionData: SessionData,
        forceRefresh: Boolean = false,
    ): DeviceDashboardSnapshot? =
        deviceLoadMutex.withLock {
            if (activeSessionId != sessionData.id) return@withLock null
            if (!forceRefresh) {
                deviceSnapshot?.let { return@withLock it }
            }

            deviceRefreshing = true
            val result =
                loadDeviceDashboardSnapshot(
                    context = context,
                    sessionData = sessionData,
                    preferCachedConnectionInfo = !forceRefresh,
                )
            if (activeSessionId != sessionData.id) return@withLock null

            deviceRefreshing = false
            deviceErrorMessage = result.errorMessage
            if (result.errorMessage == null) {
                deviceSnapshot = result
            }
            result
        }

    suspend fun loadFileInformation(
        sessionId: String,
        path: String,
        forceRefresh: Boolean = false,
    ): FileBrowserSnapshot? {
        if (activeSessionId != sessionId) return null
        val state = fileBrowserState
        val result = state.loadSnapshot(path, forceRefresh)
        return result.takeIf { activeSessionId == sessionId && state === fileBrowserState }
    }

    @SuppressLint("SdCardPath")
    private suspend fun prefetchRootFileInformation(sessionId: String): FileBrowserSnapshot? {
        if (activeSessionId != sessionId) return null
        val state = fileBrowserState
        val result = state.prefetchSnapshot("/sdcard")
        return result.takeIf { activeSessionId == sessionId && state === fileBrowserState }
    }

    suspend fun loadApplicationInformation(
        context: Context,
        sessionId: String,
        selectedFilters: Set<AppListFilter>,
        forceRefresh: Boolean = false,
    ) {
        if (activeSessionId != sessionId) return
        val completion = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main.immediate) {
            enqueueAppRequest(
                AppRequest(
                    sessionId = sessionId,
                    context = context.applicationContext,
                    key =
                        AppRequestKey(
                            selectedFilters = selectedFilters.toSet(),
                            forceRefresh = forceRefresh,
                        ),
                    waiters = mutableListOf(completion),
                ),
            )
        }
        completion.await()
    }

    fun invalidate(sessionId: String) {
        if (activeSessionId != sessionId) return
        sessionScope?.cancel()
        sessionScope = null
        val cancellation = CancellationException("Session management data cache was invalidated")
        activeAppRequest?.waiters?.forEach { waiter ->
            if (!waiter.isCompleted) waiter.completeExceptionally(cancellation)
        }
        pendingAppRequest?.waiters?.forEach { waiter ->
            if (!waiter.isCompleted) waiter.completeExceptionally(cancellation)
        }
        activeAppRequest = null
        pendingAppRequest = null
        activeSessionId = null
        deviceSnapshot = null
        deviceErrorMessage = null
        deviceRefreshing = false
        fileBrowserState = SessionManagementFileBrowserState()
        SessionManagementAppCache.releaseScope(sessionId)
    }

    private fun activate(sessionId: String) {
        if (activeSessionId == sessionId && sessionScope != null) return
        activeSessionId?.let(::invalidate)
        activeSessionId = sessionId
        deviceSnapshot = null
        deviceErrorMessage = null
        deviceRefreshing = false
        fileBrowserState = SessionManagementFileBrowserState()
        SessionManagementAppCache.selectScope(sessionId)
        sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    private fun enqueueAppRequest(request: AppRequest) {
        if (activeSessionId != request.sessionId) {
            request.waiters.forEach { it.complete(Unit) }
            return
        }

        val active = activeAppRequest
        if (active?.key == request.key) {
            supersedePendingAppRequest()
            active.waiters += request.waiters
            return
        }

        val pending = pendingAppRequest
        if (pending?.key == request.key) {
            pending.waiters += request.waiters
            return
        }

        supersedePendingAppRequest()
        pendingAppRequest = request
        startNextAppRequestIfIdle()
    }

    private fun supersedePendingAppRequest() {
        val superseded = pendingAppRequest ?: return
        pendingAppRequest = null
        val cancellation = CancellationException("Application data request was superseded")
        superseded.waiters.forEach { waiter ->
            if (!waiter.isCompleted) waiter.completeExceptionally(cancellation)
        }
    }

    private fun startNextAppRequestIfIdle() {
        if (activeAppRequest != null) return
        val request = pendingAppRequest ?: return
        val scope = sessionScope ?: return
        pendingAppRequest = null
        activeAppRequest = request
        scope.launch {
            var failure: Throwable? = null
            try {
                loadSessionManagementAppData(
                    context = request.context,
                    scopeKey = request.sessionId,
                    selectedFilters = request.key.selectedFilters,
                    forceRefresh = request.key.forceRefresh,
                )
            } catch (error: Throwable) {
                failure = error
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    finishAppRequest(request, failure)
                }
            }
        }
    }

    private fun finishAppRequest(
        request: AppRequest,
        failure: Throwable?,
    ) {
        if (activeAppRequest === request) {
            activeAppRequest = null
        }
        request.waiters.forEach { waiter ->
            if (!waiter.isCompleted) {
                if (failure == null) waiter.complete(Unit) else waiter.completeExceptionally(failure)
            }
        }
        if (activeSessionId == request.sessionId) {
            startNextAppRequestIfIdle()
        }
    }
}
