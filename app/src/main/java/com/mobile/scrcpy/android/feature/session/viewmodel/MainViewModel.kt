package com.mobile.scrcpy.android.feature.session.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobile.scrcpy.android.app.ScreenRemoteApp
import com.mobile.scrcpy.android.core.data.datastore.PreferencesManager
import com.mobile.scrcpy.android.core.domain.model.AppSettings
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupType
import com.mobile.scrcpy.android.core.domain.model.ScrcpyAction
import com.mobile.scrcpy.android.feature.remote.viewmodel.ConnectionViewModel
import com.mobile.scrcpy.android.feature.remote.viewmodel.ControlViewModel
import com.mobile.scrcpy.android.feature.session.data.repository.GroupRepository
import com.mobile.scrcpy.android.feature.session.data.repository.SessionData
import com.mobile.scrcpy.android.feature.session.data.repository.SessionRepository
import com.mobile.scrcpy.android.feature.settings.viewmodel.SettingsViewModel
import com.mobile.scrcpy.android.infrastructure.scrcpy.client.feature.scrcpy.ScrcpyClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val sessionRepository = SessionRepository(ScreenRemoteApp.instance)
    private val groupRepository = GroupRepository(ScreenRemoteApp.instance)
    private val preferencesManager = PreferencesManager(ScreenRemoteApp.instance)

    // 暴露 scrcpyClient 供 RemoteDisplayScreen 使用
    val scrcpyClient =
        ScrcpyClient(
            ScreenRemoteApp.instance,
            ScreenRemoteApp.instance.adbConnectionManager,
        )

    // ============ 聚合专用 ViewModel ============

    val sessionViewModel = SessionViewModel(sessionRepository)
    val groupViewModel = GroupViewModel(groupRepository, sessionRepository)
    val connectionViewModel = ConnectionViewModel(scrcpyClient, sessionRepository)
    val settingsViewModel = SettingsViewModel(preferencesManager)
    val controlViewModel = ControlViewModel(scrcpyClient, ScreenRemoteApp.instance.adbConnectionManager)

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

    // ============ Factory ============

    companion object {
        fun provideFactory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel() as T
            }
    }
}
