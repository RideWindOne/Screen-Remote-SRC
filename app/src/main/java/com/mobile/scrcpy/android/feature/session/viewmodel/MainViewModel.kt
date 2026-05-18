package com.mobile.scrcpy.android.feature.session.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mobile.scrcpy.android.app.ScreenRemoteApp
import com.mobile.scrcpy.android.core.data.datastore.PreferencesManager
import com.mobile.scrcpy.android.core.data.repository.GroupRepository
import com.mobile.scrcpy.android.core.domain.model.AppSettings
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupType
import com.mobile.scrcpy.android.core.domain.model.ScrcpyAction
import com.mobile.scrcpy.android.core.data.repository.SessionData
import com.mobile.scrcpy.android.core.data.repository.SessionRepository
import com.mobile.scrcpy.android.feature.remote.presentation.ConnectionViewModel
import com.mobile.scrcpy.android.feature.remote.presentation.ControlViewModel
import com.mobile.scrcpy.android.feature.settings.viewmodel.SettingsViewModel
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbBridge
import com.mobile.scrcpy.android.infrastructure.scrcpy.client.ScrcpyClient
import com.mobile.scrcpy.android.service.ScrcpyForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ManagementConnectStatus {
    data object Idle : ManagementConnectStatus()

    data class Connecting(
        val sessionId: String,
    ) : ManagementConnectStatus()

    data class Connected(
        val sessionId: String,
        val deviceId: String,
    ) : ManagementConnectStatus()

    data class Failed(
        val sessionId: String,
        val error: String,
    ) : ManagementConnectStatus()
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
class MainViewModel : ViewModel() {
    private val dependencies = MainViewModelDependencies.fromApp(ScreenRemoteApp.instance)
    private val automationState = MainAutomationState()

    val sessionRepository = dependencies.sessionRepository
    val scrcpyClient = dependencies.scrcpyClient
    private val adbConnectionManager = dependencies.adbConnectionManager

    // ============ 聚合专用 ViewModel ============

    val sessionViewModel = SessionViewModel(sessionRepository)
    val groupViewModel = GroupViewModel(dependencies.groupRepository, sessionRepository)
    val connectionViewModel = ConnectionViewModel(scrcpyClient, sessionRepository)
    val settingsViewModel = SettingsViewModel(dependencies.preferencesManager)
    val controlViewModel = ControlViewModel(scrcpyClient, dependencies.adbConnectionManager)

    // ============ 会话数据（直接委托，避免重复订阅） ============

    // 从 GroupViewModel 获取（它内部已经订阅了 sessionRepository）
    val sessionDataList: StateFlow<List<SessionData>> get() = groupViewModel.filteredSessions

    // ============ 分组管理（直接委托） ============

    val groups: StateFlow<List<DeviceGroup>> get() = groupViewModel.groups
    val selectedGroupPath: StateFlow<String> get() = groupViewModel.selectedGroupPath
    val selectedAutomationGroupPath: StateFlow<String> get() = groupViewModel.selectedAutomationGroupPath
    val filteredSessions: StateFlow<List<SessionData>> get() = groupViewModel.filteredSessions

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

    fun showAddSessionDialog() = sessionViewModel.showAddSessionDialog()

    fun showEditSessionDialog(sessionId: String) = sessionViewModel.showEditSessionDialog(sessionId)

    fun hideAddSessionDialog() = sessionViewModel.hideAddSessionDialog()

    fun saveSessionData(sessionData: SessionData) = sessionViewModel.saveSessionData(sessionData)

    fun removeSession(id: String) = sessionViewModel.removeSession(id)

    fun copySession(sessionData: SessionData) = sessionViewModel.copySession(sessionData)

    // ============ 连接状态管理（直接委托） ============

    val connectedSessionId: StateFlow<String?> get() = connectionViewModel.connectedSessionId
    val connectStatus get() = connectionViewModel.connectStatus
    val connectionProgress get() = connectionViewModel.connectionProgress

    fun connectSession(sessionId: String) = connectionViewModel.connectSession(sessionId)

    fun clearConnectStatus() = connectionViewModel.clearConnectStatus()

    fun disconnectFromDevice() = connectionViewModel.disconnectFromDevice()

    fun cancelConnect() = connectionViewModel.cancelConnect()

    fun handleConnectionLost() = connectionViewModel.handleConnectionLost()

    // ============ 管理页 ADB 连接（仅 ADB，不启动 scrcpy） ============

    private val _managementConnectStatus = MutableStateFlow<ManagementConnectStatus>(ManagementConnectStatus.Idle)
    val managementConnectStatus: StateFlow<ManagementConnectStatus> = _managementConnectStatus.asStateFlow()

    private val _managementDeviceId = MutableStateFlow<String?>(null)
    val managementDeviceId: StateFlow<String?> = _managementDeviceId.asStateFlow()

    fun connectManagementSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existingStatus = _managementConnectStatus.value
            val existingDeviceId = _managementDeviceId.value
            val existingBridgeConnection = AdbBridge.getConnection()
            var existingConnectionHandled = false

            if (
                existingStatus is ManagementConnectStatus.Connected &&
                existingStatus.sessionId == sessionId &&
                existingDeviceId != null &&
                existingBridgeConnection?.deviceId == existingDeviceId
            ) {
                val existingConn = adbConnectionManager.getConnection(existingDeviceId)
                // 管理功能需要 delayed_ack，若当前连接已满足则直接复用
                if (existingConn != null && existingConn.supportsDelayedAck()) {
                    _managementConnectStatus.value = existingStatus
                    return@launch
                }
                // 无连接或不含 delayed_ack，断开重建
                runCatching { adbConnectionManager.disconnectDevice(existingDeviceId) }
                if (AdbBridge.getConnection()?.deviceId == existingDeviceId) {
                    AdbBridge.clearConnection()
                }
                _managementDeviceId.value = null
                existingConnectionHandled = true
            }

            _managementConnectStatus.value = ManagementConnectStatus.Connecting(sessionId)

            val sessionData = sessionRepository.getSessionData(sessionId)
            if (sessionData == null) {
                _managementConnectStatus.value = ManagementConnectStatus.Failed(sessionId, "会话不存在")
                return@launch
            }

            if (!existingConnectionHandled && existingDeviceId != null) {
                runCatching { adbConnectionManager.disconnectDevice(existingDeviceId) }
                if (AdbBridge.getConnection()?.deviceId == existingDeviceId) {
                    AdbBridge.clearConnection()
                }
                _managementDeviceId.value = null
            }

            val result =
                if (sessionData.isUsbConnection()) {
                    adbConnectionManager.connectUsbDeviceById(
                        deviceId = sessionData.getUsbSerialNumber() ?: sessionData.host,
                        deviceName = sessionData.name.takeIf { it.isNotBlank() },
                    )
                } else {
                    adbConnectionManager.connectDevice(
                        host = sessionData.host,
                        port = sessionData.port.toIntOrNull() ?: 5555,
                        deviceName = sessionData.name.takeIf { it.isNotBlank() },
                    )
                }

            val deviceId = result.getOrElse { error ->
                _managementConnectStatus.value =
                    ManagementConnectStatus.Failed(
                        sessionId,
                        error.message ?: "ADB 连接失败",
                    )
                return@launch
            }

            val connection = adbConnectionManager.getConnection(deviceId)
            if (connection == null) {
                _managementConnectStatus.value =
                    ManagementConnectStatus.Failed(sessionId, "ADB 连接已建立，但未找到连接对象")
                return@launch
            }

            AdbBridge.setConnection(connection)
            ScrcpyForegroundService.protectDevice(
                context = ScreenRemoteApp.instance,
                deviceId = deviceId,
                deviceName = connection.deviceInfo.name,
                delayedAck = true,
                host = sessionData.host,
                port = sessionData.port.toIntOrNull() ?: 0,
                isUsbConnection = sessionData.isUsbConnection(),
            )
            _managementDeviceId.value = deviceId
            _managementConnectStatus.value = ManagementConnectStatus.Connected(sessionId, deviceId)
        }
    }

    fun clearManagementConnectStatus() {
        _managementConnectStatus.value = ManagementConnectStatus.Idle
    }

    fun disconnectManagementDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            val deviceId = _managementDeviceId.value
            if (deviceId != null) {
                runCatching { adbConnectionManager.disconnectDevice(deviceId) }
                runCatching { ScrcpyForegroundService.unprotectDevice(ScreenRemoteApp.instance, deviceId) }
                if (AdbBridge.getConnection()?.deviceId == deviceId) {
                    AdbBridge.clearConnection()
                }
            }
            _managementDeviceId.value = null
            _managementConnectStatus.value = ManagementConnectStatus.Idle
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

    suspend fun sendTouchEvent(
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
        fun provideFactory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel() as T
            }
    }
}

private data class MainViewModelDependencies(
    val sessionRepository: SessionRepository,
    val groupRepository: GroupRepository,
    val preferencesManager: PreferencesManager,
    val adbConnectionManager: AdbConnectionManager,
    val scrcpyClient: ScrcpyClient,
) {
    companion object {
        fun fromApp(app: ScreenRemoteApp): MainViewModelDependencies {
            val sessionRepository = SessionRepository(app)
            return MainViewModelDependencies(
                sessionRepository = sessionRepository,
                groupRepository = GroupRepository(app),
                preferencesManager = PreferencesManager(app),
                adbConnectionManager = app.adbConnectionManager,
                scrcpyClient = ScrcpyClient(app, app.adbConnectionManager),
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
