package com.screen.remote.android.infrastructure.adb.mdns

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.core.data.repository.SessionRepository
import com.screen.remote.android.feature.device.data.PairingEndpointMetadataManager
import com.screen.remote.android.infrastructure.adb.AdbRuntimeProvider
import dadb.android.runtime.ExperimentalDadbAndroidApi
import dadb.android.wireless.AdbMdnsConfig
import dadb.android.wireless.AdbMdnsMonitor
import dadb.android.wireless.AdbMdnsService
import dadb.android.wireless.AdbMdnsServiceType
import dadb.android.wireless.AdbMdnsState
import dadb.android.wireless.AdbMdnsStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalDadbAndroidApi::class)
class MdnsSessionDiscoveryManager private constructor(
    context: Context,
    sessionRepository: SessionRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val trackedSessions = MutableStateFlow<List<MdnsTrackedSession>>(emptyList())
    private val _state = MutableStateFlow(MdnsSessionPresenceState())
    private val lifecycleLock = Any()

    @Volatile
    private var pairedMdnsDeviceKeys: Set<String> = emptySet()
    private var monitor: AdbMdnsMonitor? = null
    private var monitorCollectJob: Job? = null
    private var settleJob: Job? = null
    private var periodicRefreshJob: Job? = null
    private var recoveryJob: Job? = null
    private var networkRefreshJob: Job? = null
    private var latestMdnsState = AdbMdnsState()
    private var retainedServices: List<AdbMdnsService> = emptyList()
    private var interactiveDiscoveryConsumers = 0
    private var resolutionConsumers = 0
    private var monitorStarted = false
    private var gameModePaused = false
    private var appInForeground = false
    private var refreshing = false
    private var refreshGeneration = 0L
    private var lastRefreshStartedAtMillis = 0L
    private var consecutiveFailures = 0

    val state: StateFlow<MdnsSessionPresenceState> = _state.asStateFlow()

    fun acquireInteractiveDiscovery(): AutoCloseable {
        synchronized(lifecycleLock) {
            interactiveDiscoveryConsumers += 1
            val wasStarted = monitorStarted
            reconcileMonitoringLocked("interactive consumer acquired")
            if (wasStarted && shouldRefreshLocked()) {
                restartMonitoringLocked("interactive discovery became visible")
            }
            reconcilePeriodicRefreshLocked()
        }
        var released = false
        return AutoCloseable {
            synchronized(lifecycleLock) {
                if (!released) {
                    released = true
                    interactiveDiscoveryConsumers = (interactiveDiscoveryConsumers - 1).coerceAtLeast(0)
                    reconcileMonitoringLocked("interactive consumer released")
                    reconcilePeriodicRefreshLocked()
                }
            }
        }
    }

    fun onAppForegrounded() {
        synchronized(lifecycleLock) {
            appInForeground = true
            val wasStarted = monitorStarted
            reconcileMonitoringLocked("app foregrounded")
            if (wasStarted && shouldRefreshLocked()) {
                restartMonitoringLocked("app foregrounded with stale discovery")
            }
            reconcilePeriodicRefreshLocked()
        }
    }

    fun onAppBackgrounded() {
        synchronized(lifecycleLock) {
            appInForeground = false
            reconcilePeriodicRefreshLocked()
        }
    }

    suspend fun resolveTlsConnectService(
        serviceName: String,
        timeoutMs: Long,
    ): AdbMdnsService {
        val lease = acquireResolutionDiscovery()
        return try {
            withTimeout(timeoutMs.milliseconds) {
                state
                    .mapNotNull { presence ->
                        presence.connectServices
                            .firstOrNull { service ->
                                !service.confirming &&
                                    !service.requiresPairing &&
                                    DeviceTransportSerial
                                        .mdnsDeviceSerial(service.deviceSerial)
                                        .equals(DeviceTransportSerial.mdnsDeviceSerial(serviceName), ignoreCase = true)
                            }?.let { service ->
                                AdbMdnsService(
                                    name = service.name,
                                    host = service.host,
                                    port = service.port,
                                    serviceType = AdbMdnsServiceType.TLS_CONNECT,
                                )
                            }
                    }.first()
            }
        } finally {
            lease.close()
        }
    }

    private fun acquireResolutionDiscovery(): AutoCloseable {
        synchronized(lifecycleLock) {
            resolutionConsumers += 1
            val wasStarted = monitorStarted
            reconcileMonitoringLocked("mDNS endpoint resolution started")
            if (wasStarted && shouldRefreshLocked()) {
                restartMonitoringLocked("mDNS endpoint resolution needs fresh discovery")
            }
        }
        var released = false
        return AutoCloseable {
            synchronized(lifecycleLock) {
                if (!released) {
                    released = true
                    resolutionConsumers = (resolutionConsumers - 1).coerceAtLeast(0)
                    reconcileMonitoringLocked("mDNS endpoint resolution finished")
                    reconcilePeriodicRefreshLocked()
                }
            }
        }
    }

    /** An active game session does not need background LAN service discovery. */
    fun setGameModePaused(paused: Boolean) {
        synchronized(lifecycleLock) {
            if (gameModePaused == paused) return
            gameModePaused = paused
            reconcileMonitoringLocked(if (paused) "game mode entered" else "game mode exited")
            reconcilePeriodicRefreshLocked()
        }
    }

    init {
        observeTrackedSessions(sessionRepository)
        observePairingRecords(PairingEndpointMetadataManager(appContext))
        observeNetworkChanges()
    }

    private fun createMonitor(): AdbMdnsMonitor =
        AdbRuntimeProvider
            .get()
            .createMdnsMonitor(
                AdbMdnsConfig(
                    serviceTypes =
                        setOf(
                            AdbMdnsServiceType.TLS_CONNECT,
                            AdbMdnsServiceType.TLS_PAIRING,
                        ),
                ),
            )

    private fun observePairingRecords(manager: PairingEndpointMetadataManager) {
        scope.launch {
            manager.pairedMdnsDeviceKeysFlow
                .distinctUntilChanged()
                .collect { keys ->
                    synchronized(lifecycleLock) {
                        pairedMdnsDeviceKeys = keys
                        recomputePresenceLocked()
                    }
                }
        }
    }

    private fun observeTrackedSessions(sessionRepository: SessionRepository) {
        scope.launch {
            sessionRepository.sessionDataFlow
                .map { sessions ->
                    sessions
                        .flatMap { it.mdnsTrackedSessions() }
                        .distinctBy { it.sessionId to it.mdnsSerial }
                }.distinctUntilChanged()
                .collectLatest { sessions ->
                    synchronized(lifecycleLock) {
                        trackedSessions.value = sessions
                        reconcileMonitoringLocked("tracked sessions changed")
                        reconcilePeriodicRefreshLocked()
                        recomputePresenceLocked()
                    }
                }
        }
    }

    private fun startMonitoringLocked(
        reason: String,
        retained: List<AdbMdnsService> = emptyList(),
    ) {
        if (monitorStarted || !shouldMonitorLocked()) return

        val newMonitor = createMonitor()
        monitor = newMonitor
        monitorStarted = true
        latestMdnsState = AdbMdnsState()
        retainedServices = retained.distinctBy(AdbMdnsService::stableRefreshKey)
        refreshing = true
        refreshGeneration += 1
        lastRefreshStartedAtMillis = SystemClock.elapsedRealtime()
        val generation = refreshGeneration

        monitorCollectJob =
            scope.launch {
                newMonitor.state.collect { mdnsState ->
                    synchronized(lifecycleLock) {
                        if (monitor !== newMonitor || generation != refreshGeneration) return@collect
                        latestMdnsState = mdnsState
                        if (mdnsState.status == AdbMdnsStatus.FAILED) {
                            scheduleRecoveryLocked(newMonitor, generation)
                        }
                        recomputePresenceLocked()
                    }
                }
            }

        runCatching { newMonitor.start() }
            .onSuccess {
                scheduleSettleLocked(generation)
                recomputePresenceLocked()
                LogManager.i(LogTags.ADB_CONNECTION, "Started shared mDNS discovery: reason=$reason generation=$generation")
            }.onFailure { error ->
                latestMdnsState = AdbMdnsState(status = AdbMdnsStatus.FAILED)
                recomputePresenceLocked()
                scheduleRecoveryLocked(newMonitor, generation)
                LogManager.e(LogTags.ADB_CONNECTION, "Failed to start shared mDNS discovery: reason=$reason", error)
            }
    }

    private fun restartMonitoringLocked(reason: String) {
        if (!shouldMonitorLocked()) return
        val retained =
            (latestMdnsState.connectServices + latestMdnsState.pairingServices + retainedServices)
                .distinctBy(AdbMdnsService::stableRefreshKey)
        stopMonitorInstanceLocked(clearState = false)
        startMonitoringLocked(reason = reason, retained = retained)
    }

    private fun stopMonitoringLocked(reason: String) {
        if (!monitorStarted && monitor == null) return
        stopMonitorInstanceLocked(clearState = true)
        LogManager.i(LogTags.ADB_CONNECTION, "Stopped shared mDNS discovery: reason=$reason")
    }

    private fun stopMonitorInstanceLocked(clearState: Boolean) {
        settleJob?.cancel()
        settleJob = null
        recoveryJob?.cancel()
        recoveryJob = null
        monitorCollectJob?.cancel()
        monitorCollectJob = null
        runCatching { monitor?.close() }
        monitor = null
        monitorStarted = false
        latestMdnsState = AdbMdnsState()
        refreshing = false
        if (clearState) {
            retainedServices = emptyList()
            consecutiveFailures = 0
            lastRefreshStartedAtMillis = 0L
            _state.value = MdnsSessionPresenceState()
        }
    }

    private fun scheduleSettleLocked(generation: Long) {
        settleJob?.cancel()
        settleJob =
            scope.launch {
                delay(REFRESH_CONFIRMATION_WINDOW_MS)
                synchronized(lifecycleLock) {
                    if (generation != refreshGeneration || !monitorStarted) return@synchronized
                    refreshing = false
                    retainedServices = emptyList()
                    if (latestMdnsState.status != AdbMdnsStatus.FAILED) {
                        consecutiveFailures = 0
                    }
                    recomputePresenceLocked()
                    LogManager.d(LogTags.ADB_CONNECTION, "Completed mDNS discovery confirmation: generation=$generation")
                }
            }
    }

    private fun scheduleRecoveryLocked(
        failedMonitor: AdbMdnsMonitor,
        generation: Long,
    ) {
        if (recoveryJob?.isActive == true || !shouldMonitorLocked()) return
        consecutiveFailures += 1
        val delayMillis = mdnsRetryDelayMillis(consecutiveFailures)
        LogManager.w(
            LogTags.ADB_CONNECTION,
            "mDNS discovery failed; scheduling restart in ${delayMillis}ms: generation=$generation attempt=$consecutiveFailures",
        )
        recoveryJob =
            scope.launch {
                delay(delayMillis)
                synchronized(lifecycleLock) {
                    recoveryJob = null
                    if (monitor !== failedMonitor || generation != refreshGeneration || !shouldMonitorLocked()) {
                        return@synchronized
                    }
                    restartMonitoringLocked("automatic failure recovery")
                }
            }
    }

    private fun reconcileMonitoringLocked(reason: String) {
        if (shouldMonitorLocked()) {
            startMonitoringLocked(reason)
        } else {
            stopMonitoringLocked(reason)
        }
    }

    private fun shouldMonitorLocked(): Boolean =
        shouldMonitorMdnsDiscovery(
            gameModePaused = gameModePaused,
            hasTrackedSessions = trackedSessions.value.isNotEmpty(),
            interactiveDiscoveryConsumers = interactiveDiscoveryConsumers,
            resolutionConsumers = resolutionConsumers,
        )

    private fun shouldRefreshLocked(): Boolean =
        shouldRefreshMdns(
            nowMillis = SystemClock.elapsedRealtime(),
            lastRefreshStartedAtMillis = lastRefreshStartedAtMillis,
            freshnessWindowMillis = REFRESH_FRESHNESS_WINDOW_MS,
        )

    private fun reconcilePeriodicRefreshLocked() {
        val shouldRun = monitorStarted && !gameModePaused && appInForeground
        if (!shouldRun) {
            periodicRefreshJob?.cancel()
            periodicRefreshJob = null
            return
        }
        if (periodicRefreshJob?.isActive == true) return
        periodicRefreshJob =
            scope.launch {
                while (isActive) {
                    delay(FOREGROUND_REFRESH_INTERVAL_MS)
                    synchronized(lifecycleLock) {
                        if (monitorStarted && shouldMonitorLocked()) {
                            restartMonitoringLocked("periodic foreground confirmation")
                        }
                    }
                }
            }
    }

    private fun observeNetworkChanges() {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scheduleNetworkRefresh("network available")
                }

                override fun onLost(network: Network) {
                    scheduleNetworkRefresh("network lost")
                }
            }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } else {
                val request =
                    NetworkRequest
                        .Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                connectivityManager.registerNetworkCallback(request, callback)
            }
        }.onFailure { error ->
            LogManager.e(LogTags.ADB_CONNECTION, "Failed to observe network changes for mDNS discovery", error)
        }
    }

    private fun scheduleNetworkRefresh(reason: String) {
        synchronized(lifecycleLock) {
            networkRefreshJob?.cancel()
            networkRefreshJob =
                scope.launch {
                    delay(NETWORK_REFRESH_DEBOUNCE_MS)
                    synchronized(lifecycleLock) {
                        networkRefreshJob = null
                        if (monitorStarted && shouldMonitorLocked()) {
                            restartMonitoringLocked(reason)
                        }
                    }
                }
        }
    }

    private fun recomputePresenceLocked() {
        val discoveredSerials =
            latestMdnsState.connectServices
                .mapTo(linkedSetOf()) { it.canonicalSerial() }
        val retainedConnectSerials =
            retainedServices
                .asSequence()
                .filter { it.serviceType == AdbMdnsServiceType.TLS_CONNECT || it.serviceType == AdbMdnsServiceType.ADB }
                .map(AdbMdnsService::canonicalSerial)
                .filter { it !in discoveredSerials }
                .toCollection(linkedSetOf())
        val onlineSessions = onlineTrackedSessions(trackedSessions.value, discoveredSerials)
        val nextOnlineSessionIds = onlineSessions.mapTo(linkedSetOf()) { it.sessionId }
        val discoveredServices =
            discoveredMdnsServices(
                connectServices = latestMdnsState.connectServices,
                pairingServices = latestMdnsState.pairingServices,
                pairedDeviceKeys = pairedMdnsDeviceKeys,
                retainedServices = retainedServices,
                refreshing = refreshing,
            )

        _state.value =
            MdnsSessionPresenceState(
                monitoring = monitorStarted && latestMdnsState.status != AdbMdnsStatus.FAILED,
                loading = refreshing && discoveredServices.isEmpty(),
                refreshing = refreshing,
                onlineSessionIds = nextOnlineSessionIds,
                onlineMdnsSerials = discoveredSerials,
                confirmingMdnsSerials = if (refreshing) retainedConnectSerials else emptySet(),
                connectServices = discoveredServices,
            )
    }

    companion object {
        private const val REFRESH_CONFIRMATION_WINDOW_MS = 5_000L
        private const val REFRESH_FRESHNESS_WINDOW_MS = 15_000L
        private const val FOREGROUND_REFRESH_INTERVAL_MS = 30_000L
        private const val NETWORK_REFRESH_DEBOUNCE_MS = 500L

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: MdnsSessionDiscoveryManager? = null

        fun initialize(
            context: Context,
            sessionRepository: SessionRepository = SessionRepository(context.applicationContext),
        ): MdnsSessionDiscoveryManager =
            instance ?: synchronized(this) {
                instance ?: MdnsSessionDiscoveryManager(
                    context.applicationContext,
                    sessionRepository,
                ).also { instance = it }
            }

        fun get(): MdnsSessionDiscoveryManager =
            checkNotNull(instance) { "MdnsSessionDiscoveryManager is not initialized" }
    }
}

private fun AdbMdnsService.stableRefreshKey(): String = "${serviceType.name}:${canonicalSerial()}"

internal fun shouldMonitorMdnsDiscovery(
    gameModePaused: Boolean,
    hasTrackedSessions: Boolean,
    interactiveDiscoveryConsumers: Int,
    resolutionConsumers: Int = 0,
): Boolean =
    resolutionConsumers > 0 ||
        (!gameModePaused && (hasTrackedSessions || interactiveDiscoveryConsumers > 0))
