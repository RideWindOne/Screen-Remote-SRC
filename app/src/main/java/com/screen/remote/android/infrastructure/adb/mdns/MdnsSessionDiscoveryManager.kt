package com.screen.remote.android.infrastructure.adb.mdns

import android.annotation.SuppressLint
import android.content.Context
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.repository.SessionRepository
import com.screen.remote.android.feature.device.data.PairingEndpointMetadataManager
import com.screen.remote.android.infrastructure.adb.AdbRuntimeProvider
import dadb.android.runtime.ExperimentalDadbAndroidApi
import dadb.android.wireless.AdbMdnsConfig
import dadb.android.wireless.AdbMdnsMonitor
import dadb.android.wireless.AdbMdnsServiceType
import dadb.android.wireless.AdbMdnsStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalDadbAndroidApi::class)
class MdnsSessionDiscoveryManager private constructor(
    context: Context,
    sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val monitor: AdbMdnsMonitor =
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
    private val trackedSessions = MutableStateFlow<List<MdnsTrackedSession>>(emptyList())
    private val _state = MutableStateFlow(MdnsSessionPresenceState())
    @Volatile
    private var pairedMdnsDeviceKeys: Set<String> = emptySet()
    private val lifecycleLock = Any()
    private var interactiveDiscoveryConsumers = 0
    private var monitorStarted = false

    val state: StateFlow<MdnsSessionPresenceState> = _state.asStateFlow()

    fun acquireInteractiveDiscovery(): AutoCloseable {
        synchronized(lifecycleLock) {
            interactiveDiscoveryConsumers += 1
            reconcileMonitoring()
        }
        var released = false
        return AutoCloseable {
            synchronized(lifecycleLock) {
                if (!released) {
                    released = true
                    interactiveDiscoveryConsumers = (interactiveDiscoveryConsumers - 1).coerceAtLeast(0)
                    reconcileMonitoring()
                }
            }
        }
    }

    init {
        observeTrackedSessions(sessionRepository)
        observePairingRecords(PairingEndpointMetadataManager(context))
        observeDiscovery()
    }

    private fun observePairingRecords(manager: PairingEndpointMetadataManager) {
        scope.launch {
            manager.pairedMdnsDeviceKeysFlow
                .distinctUntilChanged()
                .collect { keys ->
                    pairedMdnsDeviceKeys = keys
                    recomputePresence()
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
                    trackedSessions.value = sessions
                    synchronized(lifecycleLock) {
                        reconcileMonitoring()
                    }
                    recomputePresence()
                }
        }
    }

    private fun observeDiscovery() {
        scope.launch {
            monitor.state.collect { mdnsState ->
                val monitoring = mdnsState.status != AdbMdnsStatus.STOPPED && mdnsState.status != AdbMdnsStatus.FAILED
                if (_state.value.monitoring != monitoring || _state.value.loading != mdnsState.loading) {
                    _state.value = _state.value.copy(monitoring = monitoring, loading = mdnsState.loading)
                }
                recomputePresence()
            }
        }
    }

    private fun startMonitoring() {
        if (monitorStarted) {
            return
        }
        runCatching { monitor.start() }
            .onSuccess {
                monitorStarted = true
                _state.value = _state.value.copy(monitoring = true, loading = true)
                LogManager.i(LogTags.ADB_CONNECTION, "Started shared mDNS discovery for saved mDNS sessions")
            }.onFailure { error ->
                LogManager.e(LogTags.ADB_CONNECTION, "Failed to start shared mDNS discovery", error)
            }
    }

    private fun stopMonitoring() {
        if (!monitorStarted) {
            return
        }
        runCatching { monitor.stop() }
        monitorStarted = false
        _state.value = MdnsSessionPresenceState()
        LogManager.i(LogTags.ADB_CONNECTION, "Stopped shared mDNS discovery because no saved mDNS sessions remain")
    }

    private fun reconcileMonitoring() {
        val shouldMonitor = trackedSessions.value.isNotEmpty() || interactiveDiscoveryConsumers > 0
        if (shouldMonitor) {
            startMonitoring()
        } else {
            stopMonitoring()
        }
    }

    private fun recomputePresence() {
        val discoveredSerials =
            monitor.state.value.connectServices
                .mapTo(linkedSetOf()) { it.canonicalSerial() }
        val onlineSessions = onlineTrackedSessions(trackedSessions.value, discoveredSerials)
        val nextOnlineSessionIds = onlineSessions.mapTo(linkedSetOf()) { it.sessionId }
        val discoveredServices =
            discoveredMdnsServices(
                connectServices = monitor.state.value.connectServices,
                pairingServices = monitor.state.value.pairingServices,
                pairedDeviceKeys = pairedMdnsDeviceKeys,
            )

        _state.value =
            MdnsSessionPresenceState(
                monitoring = _state.value.monitoring,
                loading = monitor.state.value.loading,
                onlineSessionIds = nextOnlineSessionIds,
                onlineMdnsSerials = discoveredSerials,
                connectServices = discoveredServices,
            )

    }

    companion object {
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
