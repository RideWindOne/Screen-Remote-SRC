package com.screen.remote.android.feature.session.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.screen.remote.android.core.common.constants.AppConstants
import com.screen.remote.android.core.common.constants.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.datastore.PreferencesManager
import com.screen.remote.android.core.data.repository.GroupRepository
import com.screen.remote.android.core.domain.model.AppSettings
import com.screen.remote.android.core.domain.model.AppLanguage
import com.screen.remote.android.core.domain.model.ThemeMode
import com.screen.remote.android.app.deeplink.requireBoolean
import com.screen.remote.android.app.deeplink.toUrlRuntimeSession
import com.screen.remote.android.app.deeplink.UrlSetting
import com.screen.remote.android.app.deeplink.NewSessionPrefill
import com.screen.remote.android.core.data.storage.SessionStorage
import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.GroupType
import com.screen.remote.android.core.domain.model.ScrcpyAction
import com.screen.remote.android.core.update.UpdateChannel
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.data.repository.SessionRepository
import com.screen.remote.android.core.data.repository.toData
import com.screen.remote.android.core.domain.model.markConnectionCandidateSuccess
import com.screen.remote.android.feature.remote.presentation.ConnectionViewModel
import com.screen.remote.android.feature.remote.presentation.ControlViewModel
import com.screen.remote.android.feature.session.update.hasUnseenRecentUpdateContent
import com.screen.remote.android.feature.settings.viewmodel.SettingsViewModel
import com.screen.remote.android.feature.device.viewmodel.AdbKeysViewModel
import com.screen.remote.android.infrastructure.adb.connection.AdbBridge
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.adb.connection.raceAdbConnections
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionPurpose
import com.screen.remote.android.infrastructure.adb.mdns.MdnsSessionDiscoveryManager
import com.screen.remote.android.infrastructure.scrcpy.client.ScrcpyClient
import com.screen.remote.android.service.ScrcpyForegroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

sealed class ManagementConnectStatus {
    data object Idle : ManagementConnectStatus()

    data class Connecting(
        val sessionId: String,
    ) : ManagementConnectStatus()

    data class Connected(
        val sessionId: String,
    ) : ManagementConnectStatus()

    data class Failed(
        val sessionId: String,
    ) : ManagementConnectStatus()
}

enum class SessionOnboardingState {
    LOADING,
    INTRODUCTION,
    RECENT_UPDATES,
    HIDDEN,
}

internal fun resolveSessionOnboardingState(
    lastSeenVersion: String?,
    currentVersion: String,
): SessionOnboardingState =
    when {
        lastSeenVersion == null -> SessionOnboardingState.INTRODUCTION
        hasUnseenRecentUpdateContent(lastSeenVersion, currentVersion) -> SessionOnboardingState.RECENT_UPDATES
        else -> SessionOnboardingState.HIDDEN
    }

/**
 * 主 ViewModel（协调层）
 * 职责：聚合各子 ViewModel、提供统一访问接口、管理共享状态
 *
 * 注意：大部分功能已拆分到专用 ViewModel：
 * - SessionViewModel: 会话管理
 * - GroupViewModel: 分组管理
 * - ConnectionViewModel: 连接管理
 * - ControlViewModel: 设备控制
 * - AdbKeysViewModel: ADB 密钥管理
 * - SettingsViewModel: 设置管理
 *
 * MainViewModel 作为协调层，聚合这些专用 ViewModel 的功能
 */
class MainViewModel(
    private val dependencies: MainViewModelDependencies,
) : ViewModel() {
    private var managementConnectJob: Job? = null
    private val automationState = MainAutomationState()
    private val managementConnection = ManagementAdbSessionController(dependencies, viewModelScope)

    val sessionRepository = dependencies.sessionRepository
    val scrcpyClient = dependencies.scrcpyClient

    // ============ 聚合专用 ViewModel ============

    val sessionViewModel = SessionViewModel(sessionRepository)
    val groupViewModel = GroupViewModel(dependencies.groupRepository, sessionRepository)
    val connectionViewModel = ConnectionViewModel(scrcpyClient, sessionRepository)
    val settingsViewModel = SettingsViewModel(dependencies.preferencesManager)
    val controlViewModel = ControlViewModel(scrcpyClient, dependencies.adbConnectionManager)
    private val _urlSessionData = MutableStateFlow<SessionData?>(null)
    val urlSessionData: StateFlow<SessionData?> = _urlSessionData.asStateFlow()

    // ============ 会话数据（直接委托，避免重复订阅） ============

    // 从 GroupViewModel 获取（它内部已经订阅了 sessionRepository）
    val sessionDataList: StateFlow<List<SessionData>> get() = groupViewModel.filteredSessions

    private val currentOnboardingVersion = AppConstants.APP_VERSION

    val sessionOnboardingState: StateFlow<SessionOnboardingState> =
        dependencies.preferencesManager.lastSeenOnboardingVersionFlow
            .map { lastSeenVersion ->
                resolveSessionOnboardingState(
                    lastSeenVersion = lastSeenVersion,
                    currentVersion = currentOnboardingVersion,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = SessionOnboardingState.LOADING,
            )

    // ============ 分组管理（直接委托） ============

    val groups: StateFlow<List<DeviceGroup>> get() = groupViewModel.groups
    val selectedGroupPath: StateFlow<String> get() = groupViewModel.selectedGroupPath
    val selectedAutomationGroupPath: StateFlow<String> get() = groupViewModel.selectedAutomationGroupPath
    val filteredSessions: StateFlow<List<SessionData>> get() = groupViewModel.filteredSessions
    val mdnsSessionPresence get() = MdnsSessionDiscoveryManager.get().state
    val usbDevices get() = dependencies.adbConnectionManager.getUsbDevices()
    val connectedAdbDevices get() = dependencies.adbConnectionManager.connectedDevices

    fun currentRemoteDeviceId(): String? = scrcpyClient.getCurrentDeviceId()

    fun selectGroup(groupPath: String) = groupViewModel.selectGroup(groupPath)

    fun selectAutomationGroup(groupPath: String) = groupViewModel.selectAutomationGroup(groupPath)

    fun addGroup(
        name: String,
        parentPath: String,
        type: GroupType = GroupType.SESSION,
    ) = groupViewModel.addGroup(name, parentPath, type)

    fun updateGroup(group: DeviceGroup) = groupViewModel.updateGroup(group)

    fun removeGroup(groupId: String) = groupViewModel.removeGroup(groupId)

    fun getSessionCountByGroup(): Map<String, Int> = groupViewModel.getSessionCountByGroup()

    // ============ 会话对话框管理（直接委托） ============

    val showAddSessionDialog: StateFlow<Boolean> get() = sessionViewModel.showAddSessionDialog
    val editingSessionId: StateFlow<String?> get() = sessionViewModel.editingSessionId
    val newSessionPrefill: StateFlow<NewSessionPrefill> get() = sessionViewModel.newSessionPrefill

    fun showAddSessionDialog(prefill: NewSessionPrefill = NewSessionPrefill()) = sessionViewModel.showAddSessionDialog(prefill)

    fun completeSessionOnboarding() {
        viewModelScope.launch {
            dependencies.preferencesManager.markOnboardingVersionSeen(currentOnboardingVersion)
        }
    }

    fun showEditSessionDialog(sessionId: String) = sessionViewModel.showEditSessionDialog(sessionId)

    fun hideAddSessionDialog() = sessionViewModel.hideAddSessionDialog()

    fun saveSessionData(sessionData: SessionData) = sessionViewModel.saveSessionData(sessionData)

    fun removeSession(id: String) = sessionViewModel.removeSession(id)

    fun copySession(sessionData: SessionData) = sessionViewModel.copySession(sessionData)

    suspend fun resetSessionConnectionAndDetection(sessionId: String): Result<Unit> =
        runCatching {
            withContext(Dispatchers.IO) {
                val sessionData =
                    sessionRepository.getSessionData(sessionId)
                        ?: error("会话不存在")
                val connectedDeviceIds =
                    sessionData
                        .toConnectionCandidates()
                        .mapTo(linkedSetOf()) { it.deviceIdentifier() }

                managementConnection.disconnectIfSession(sessionId)
                connectedDeviceIds.forEach { deviceId ->
                    val connection = dependencies.adbConnectionManager.getConnection(deviceId)
                    if (connection != null) {
                        dependencies.adbConnectionManager
                            .disconnectDevice(deviceId)
                            .getOrThrow()
                    }
                    ScrcpyForegroundService.unprotectDevice(dependencies.appContext, deviceId)
                    managementConnection.clearDeviceReference(deviceId)
                }

                sessionRepository.updateSessionFields(sessionId) { current ->
                    current.clearAutoDetectedCodecState()
                }
            }
        }

    // ============ 连接状态管理（直接委托） ============

    val connectedSessionId: StateFlow<String?> get() = connectionViewModel.connectedSessionId
    val connectStatus get() = connectionViewModel.connectStatus
    val connectionProgress get() = connectionViewModel.connectionProgress

    fun connectSession(sessionId: String) = connectionViewModel.connectSession(sessionId)

    fun connectUrlSession(
        sessionData: SessionData,
        parameters: Map<String, String>,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val effectiveOptions = SessionStorage(dependencies.appContext).getOptions(sessionData.id)
            sessionData
                .toUrlRuntimeSession(
                    runtimeId = "url:${java.util.UUID.randomUUID()}",
                    parameters = parameters,
                    effectiveOptions = effectiveOptions,
                ).onSuccess(connectionViewModel::connectSession)
                .onFailure { error ->
                    LogManager.w(LogTags.CONNECTION_VM, "Invalid scrcpy URL parameters: ${error.message}")
                }
        }
    }

    fun clearConnectStatus() = connectionViewModel.clearConnectStatus()

    fun disconnectFromDevice() = connectionViewModel.disconnectFromDevice()

    fun cancelConnect() = connectionViewModel.cancelConnect()

    fun handleConnectionLost() = connectionViewModel.handleConnectionLost()

    // ============ 管理页 ADB 连接（仅 ADB，不启动 scrcpy） ============

    val managementConnectStatus: StateFlow<ManagementConnectStatus> = managementConnection.status
    val managementDeviceId: StateFlow<String?> = managementConnection.deviceId

    fun connectManagementSession(sessionId: String) {
        managementConnectJob?.cancel()
        managementConnectJob = viewModelScope.launch(Dispatchers.IO) {
            managementConnection.connect(sessionId)
        }
    }

    fun connectManagementSession(sessionData: SessionData) {
        _urlSessionData.value = sessionData
        managementConnectJob?.cancel()
        managementConnectJob = viewModelScope.launch(Dispatchers.IO) {
            managementConnection.connect(sessionData)
        }
    }

    fun generateAdbKeys() {
        viewModelScope.launch {
            AdbKeysViewModel(dependencies.appContext, dependencies.adbConnectionManager)
                .generateAdbKeys()
        }
    }

    fun applyUrlSetting(
        setting: String,
        value: String,
    ): Boolean =
        runCatching {
            val current = settingsViewModel.settings.value
            val urlSetting = UrlSetting.fromName(setting) ?: error("Unsupported URL setting: $setting")
            val updated =
                when (urlSetting) {
                    UrlSetting.DEBUG_MODE -> current.copy(enableDebugMode = value.requireBoolean(setting))
                    UrlSetting.ACTIVITY_LOG -> current.copy(enableActivityLog = value.requireBoolean(setting))
                    UrlSetting.AUDIO_LOG -> current.copy(enableAudioStreamLog = value.requireBoolean(setting))
                    UrlSetting.VIDEO_LOG -> current.copy(enableVideoStreamLog = value.requireBoolean(setting))
                    UrlSetting.CONTROL_LOG -> current.copy(enableControlStreamLog = value.requireBoolean(setting))
                    UrlSetting.EVENT_LOG -> current.copy(enableEventStreamLog = value.requireBoolean(setting))
                    UrlSetting.SHELL_LOG -> current.copy(enableShellStreamLog = value.requireBoolean(setting))
                    UrlSetting.MANAGEMENT_LOG -> current.copy(enableManagementLog = value.requireBoolean(setting))
                    UrlSetting.HAPTIC -> current.copy(enableFloatingHapticFeedback = value.requireBoolean(setting))
                    UrlSetting.PERFORMANCE_STATS -> current.copy(showPerformanceStats = value.requireBoolean(setting))
                    UrlSetting.AUTO_UPDATE -> current.copy(autoCheckUpdates = value.requireBoolean(setting))
                    UrlSetting.UPDATE_CHANNEL -> current.copy(updateChannel = UpdateChannel.valueOf(value.uppercase()))
                    UrlSetting.THEME -> current.copy(themeMode = ThemeMode.valueOf(value.uppercase()))
                    UrlSetting.LANGUAGE -> current.copy(language = AppLanguage.valueOf(value.uppercase()))
                }
            settingsViewModel.updateSettings(updated)
        }.onFailure { error ->
            LogManager.w(LogTags.SETTINGS_VM, "Failed to apply URL setting: ${error.message}")
        }.isSuccess

    fun cancelManagementConnect(sessionId: String) {
        managementConnectJob?.cancel()
        managementConnectJob = null
        managementConnection.cancelConnect(sessionId)
    }

    fun clearManagementConnectStatus() {
        managementConnection.clearStatus()
    }

    fun disconnectManagementDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            managementConnection.disconnect()
        }
    }

    // ============ 设置管理（委托给 SettingsViewModel） ============

    val settings get() = settingsViewModel.settings

    fun updateSettings(settings: AppSettings) = settingsViewModel.updateSettings(settings)

    // ============ 设备控制（委托给 ControlViewModel） ============

    suspend fun sendKeyEvent(keyCode: Int) = controlViewModel.sendKeyEvent(keyCode)

    suspend fun sendKeyEvent(
        keyCode: Int,
        action: Int,
        metaState: Int,
    ) = controlViewModel.sendKeyEvent(keyCode, action, metaState)

    suspend fun sendText(text: String) = controlViewModel.sendText(text)

    fun sendTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1.0f,
    ) = controlViewModel.sendTouchEvent(action, pointerId, x, y, screenWidth, screenHeight, pressure)

    suspend fun sendSwipeGesture(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long = 300,
    ) = controlViewModel.sendSwipeGesture(startX, startY, endX, endY, duration)

    suspend fun wakeUpScreen() = controlViewModel.wakeUpScreen()

    suspend fun executeShellCommand(command: String) = controlViewModel.executeShellCommand(command)

    // ============ 自动化功能（待拆分到 AutomationViewModel） ============

    val actions: StateFlow<List<ScrcpyAction>> = automationState.actions
    val showAddActionDialog: StateFlow<Boolean> = automationState.showAddActionDialog

    fun showAddActionDialog() = automationState.showAddActionDialog()

    fun hideAddActionDialog() = automationState.hideAddActionDialog()

    fun addAction(action: ScrcpyAction) = automationState.addAction(action)

    fun removeAction(id: String) = automationState.removeAction(id)

    // ============ Factory ============

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val dependencies = MainViewModelDependencies.fromContext(context.applicationContext)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(dependencies) as T
            }
        }

        fun provideFactory(dependencies: MainViewModelDependencies): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(dependencies) as T
            }
    }
}

data class MainViewModelDependencies(
    val appContext: Context,
    val sessionRepository: SessionRepository,
    val groupRepository: GroupRepository,
    val preferencesManager: PreferencesManager,
    val adbConnectionManager: AdbConnectionManager,
    val scrcpyClient: ScrcpyClient,
) {
    companion object {
        fun fromContext(context: Context): MainViewModelDependencies {
            val appContext = context.applicationContext
            val adbConnectionManager = AdbConnectionManager.getInstance(appContext)
            val sessionRepository = SessionRepository(appContext)
            return MainViewModelDependencies(
                appContext = appContext,
                sessionRepository = sessionRepository,
                groupRepository = GroupRepository(appContext),
                preferencesManager = PreferencesManager(appContext),
                adbConnectionManager = adbConnectionManager,
                scrcpyClient = ScrcpyClient(appContext, adbConnectionManager),
            )
        }
    }
}

private class MainAutomationState {
    private val _actions = MutableStateFlow<List<ScrcpyAction>>(emptyList())
    val actions: StateFlow<List<ScrcpyAction>> = _actions.asStateFlow()

    private val _showAddActionDialog = MutableStateFlow(false)
    val showAddActionDialog: StateFlow<Boolean> = _showAddActionDialog.asStateFlow()

    fun showAddActionDialog() {
        _showAddActionDialog.value = true
    }

    fun hideAddActionDialog() {
        _showAddActionDialog.value = false
    }

    fun addAction(action: ScrcpyAction) {
        _actions.value = _actions.value + action
        hideAddActionDialog()
    }

    fun removeAction(id: String) {
        _actions.value = _actions.value.filter { it.id != id }
    }
}

private class ManagementAdbSessionController(
    private val dependencies: MainViewModelDependencies,
    private val scope: CoroutineScope,
) {
    private val adbConnectionManager = dependencies.adbConnectionManager
    private val sessionRepository = dependencies.sessionRepository
    private val bridge = ManagementAdbBridge()
    private val connectGeneration = AtomicLong(0)

    private val _status = MutableStateFlow<ManagementConnectStatus>(ManagementConnectStatus.Idle)
    val status: StateFlow<ManagementConnectStatus> = _status.asStateFlow()

    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceId: StateFlow<String?> = _deviceId.asStateFlow()

    suspend fun connect(sessionId: String) {
        val sessionData = sessionRepository.getSessionData(sessionId)
        if (sessionData == null) {
            LogManager.e(LogTags.MANAGEMENT, "Management connection failed: sessionId=$sessionId session does not exist")
            _status.value = ManagementConnectStatus.Failed(sessionId)
            return
        }
        connect(sessionData)
    }

    suspend fun connect(sessionData: SessionData) {
        val sessionId = sessionData.id
        val generation = connectGeneration.incrementAndGet()
        LogManager.i(LogTags.MANAGEMENT, "Start establishing a management connection: sessionId=$sessionId generation=$generation")
        val existingStatus = _status.value
        val existingDeviceId = _deviceId.value
        val existingBridgeConnection = bridge.currentConnection()
        var existingConnectionHandled = false

        if (
            existingStatus is ManagementConnectStatus.Connected &&
            existingStatus.sessionId == sessionId &&
            existingDeviceId != null &&
            existingBridgeConnection?.deviceId == existingDeviceId
        ) {
            val existingConnection = adbConnectionManager.getConnection(existingDeviceId)
            if (
                existingConnection != null &&
                existingConnection.verifyWithoutSessionEvents().isSuccess
            ) {
                if (connectGeneration.get() == generation) {
                    LogManager.i(LogTags.MANAGEMENT, "Reuse authenticated management connection: deviceId=$existingDeviceId")
                    _status.value = existingStatus
                }
                return
            }
            if (connectGeneration.get() != generation) return
            disconnectDevice(existingDeviceId)
            existingConnectionHandled = true
        }

        _status.value = ManagementConnectStatus.Connecting(sessionId)

        if (!existingConnectionHandled && existingDeviceId != null) {
            val candidateDeviceIds =
                sessionData
                    .toConnectionCandidates()
                    .mapTo(linkedSetOf(), ConnectionCandidate::deviceIdentifier)
            val matchesCandidate = existingDeviceId in candidateDeviceIds
            val existingConnection = adbConnectionManager.getConnection(existingDeviceId)
            if (
                matchesCandidate &&
                existingConnection != null &&
                existingConnection.verifyWithoutSessionEvents().isSuccess
            ) {
                if (connectGeneration.get() == generation) {
                    LogManager.i(LogTags.MANAGEMENT, "Reuse existing ADB connections in candidates: deviceId=$existingDeviceId")
                    activateConnection(sessionData, existingDeviceId, generation)
                }
                return
            }
            if (connectGeneration.get() != generation) return
            disconnectDevice(existingDeviceId)
        }

        val connectedDeviceId =
            connectAdb(sessionData, generation)
                .getOrElse { error ->
                    if (connectGeneration.get() != generation) return
                    LogManager.e(LogTags.MANAGEMENT, "Management connection failed: sessionId=$sessionId ${error.message}", error)
                    _status.value =
                        ManagementConnectStatus.Failed(
                            sessionId,
                        )
                    return
                }

        if (connectGeneration.get() != generation) return
        activateConnection(sessionData, connectedDeviceId, generation)
    }

    private suspend fun activateConnection(
        sessionData: SessionData,
        connectedDeviceId: String,
        generation: Long,
    ) {
        if (connectGeneration.get() != generation) return
        val connection = adbConnectionManager.getConnection(connectedDeviceId)
        if (connection == null) {
            _status.value = ManagementConnectStatus.Failed(sessionData.id)
            return
        }

        recordSuccessfulCandidate(sessionData, connectedDeviceId)
        if (connectGeneration.get() != generation) return
        bridge.setConnection(connection)
        ScrcpyForegroundService.protectDevice(
            context = dependencies.appContext,
            deviceId = connectedDeviceId,
            deviceName = connection.deviceInfo.name,
        )
        _deviceId.value = connectedDeviceId
        _status.value = ManagementConnectStatus.Connected(sessionData.id)
        LogManager.i(LogTags.MANAGEMENT, "Management connection is ready: sessionId=${sessionData.id} deviceId=$connectedDeviceId")
    }

    private suspend fun recordSuccessfulCandidate(
        sessionData: SessionData,
        connectedDeviceId: String,
    ) {
        sessionRepository.updateSessionFields(sessionData.id) { current ->
            val candidates = current.toConnectionCandidates()
            val successfulCandidate =
                candidates.firstOrNull { it.deviceIdentifier() == connectedDeviceId }
                    ?: return@updateSessionFields current
            current.copy(
                connectionCandidates =
                    markConnectionCandidateSuccess(
                        candidates = candidates,
                        successful = successfulCandidate,
                        nowMillis = System.currentTimeMillis(),
                    ).map { it.toData() },
            )
        }
    }

    fun clearStatus() {
        _status.value = ManagementConnectStatus.Idle
    }

    fun cancelConnect(sessionId: String) {
        val generation = connectGeneration.incrementAndGet()
        LogManager.i(LogTags.MANAGEMENT, "Cancel management connection: sessionId=$sessionId generation=$generation")
        val currentStatus = _status.value
        if (currentStatus is ManagementConnectStatus.Connecting && currentStatus.sessionId == sessionId) {
            _status.value = ManagementConnectStatus.Idle
        }
    }

    suspend fun disconnect() {
        connectGeneration.incrementAndGet()
        _deviceId.value?.let { disconnectDevice(it) }
        _deviceId.value = null
        _status.value = ManagementConnectStatus.Idle
    }

    suspend fun disconnectIfSession(sessionId: String) {
        val belongsToSession =
            when (val currentStatus = _status.value) {
                is ManagementConnectStatus.Connected -> currentStatus.sessionId == sessionId
                is ManagementConnectStatus.Connecting -> currentStatus.sessionId == sessionId
                is ManagementConnectStatus.Failed -> currentStatus.sessionId == sessionId
                ManagementConnectStatus.Idle -> false
            }
        if (belongsToSession) {
            disconnect()
        }
    }

    fun clearDeviceReference(deviceId: String) {
        bridge.clearIfCurrentDevice(deviceId)
        if (_deviceId.value == deviceId) {
            _deviceId.value = null
        }
    }

    private suspend fun connectAdb(
        sessionData: SessionData,
        generation: Long,
    ): Result<String> =
        try {
            val selected =
                raceAdbConnections(
                    candidates = sessionData.toConnectionCandidates(),
                    purpose = AdbConnectionPurpose.MANAGEMENT,
                    connectionManager = adbConnectionManager,
                    attemptScope = scope,
                    cleanupScope = scope,
                    logTag = LogTags.MANAGEMENT,
                    logLabel = "management ADB",
                    deviceName = sessionData.name.takeIf { it.isNotBlank() },
                    isCurrentRace = { connectGeneration.get() == generation },
                )
            Result.success(selected.result.getOrThrow().deviceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }

    private suspend fun disconnectDevice(deviceId: String) {
        runCatching { adbConnectionManager.disconnectDevice(deviceId) }
        runCatching { ScrcpyForegroundService.unprotectDevice(dependencies.appContext, deviceId) }
        bridge.clearIfCurrentDevice(deviceId)
        _deviceId.value = null
    }
}

private class ManagementAdbBridge {
    fun currentConnection(): AdbConnection? = AdbBridge.getConnection()

    fun setConnection(connection: AdbConnection) {
        AdbBridge.setConnection(connection)
    }

    fun clearIfCurrentDevice(deviceId: String) {
        if (currentConnection()?.deviceId == deviceId) {
            AdbBridge.clearConnection()
        }
    }
}
