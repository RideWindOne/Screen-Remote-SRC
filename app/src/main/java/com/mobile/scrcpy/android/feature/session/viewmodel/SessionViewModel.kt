package com.mobile.scrcpy.android.feature.session.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mobile.scrcpy.android.core.data.repository.SessionData
import com.mobile.scrcpy.android.core.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 会话管理 ViewModel
 * 职责：会话 CRUD、对话框状态、编解码器缓存
 */
class SessionViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    // ============ 对话框状态 ============

    private val _showAddSessionDialog = MutableStateFlow(false)
    val showAddSessionDialog: StateFlow<Boolean> = _showAddSessionDialog.asStateFlow()

    private val _editingSessionId = MutableStateFlow<String?>(null)
    val editingSessionId: StateFlow<String?> = _editingSessionId.asStateFlow()

    // ============ 对话框操作 ============

    fun showAddSessionDialog() {
        _editingSessionId.value = null
        _showAddSessionDialog.value = true
    }

    fun showEditSessionDialog(sessionId: String) {
        // 如果当前正在编辑其他会话，先关闭对话框再打开新的
        // 这样可以确保不会丢失原始数据（临时编辑数据会被丢弃）
        if (_showAddSessionDialog.value && _editingSessionId.value != sessionId) {
            _showAddSessionDialog.value = false
            _editingSessionId.value = null
            // 使用延迟确保对话框完全关闭后再打开新的
            viewModelScope.launch {
                kotlinx.coroutines.delay(100)
                _editingSessionId.value = sessionId
                _showAddSessionDialog.value = true
            }
        } else {
            _editingSessionId.value = sessionId
            _showAddSessionDialog.value = true
        }
    }

    fun hideAddSessionDialog() {
        _showAddSessionDialog.value = false
        _editingSessionId.value = null
    }

    // ============ 会话 CRUD ============

    fun getSessionData(sessionId: String) = sessionRepository.getSessionDataFlow(sessionId)

    fun saveSessionData(sessionData: SessionData) {
        if (_editingSessionId.value != null) {
            updateSessionData(sessionData)
        } else {
            addSessionData(sessionData)
        }
    }

    private fun addSessionData(sessionData: SessionData) {
        viewModelScope.launch {
            sessionRepository.addSession(sessionData)
            hideAddSessionDialog()
        }
    }

    private fun updateSessionData(sessionData: SessionData) {
        viewModelScope.launch {
            sessionRepository.updateSession(sessionData)
            hideAddSessionDialog()
        }
    }

    fun removeSession(id: String) {
        viewModelScope.launch {
            sessionRepository.removeSession(id)
        }
    }

    /**
     * 复制会话，生成新名字（原名 + _N）
     * @param sessionData 要复制的会话数据
     */
    fun copySession(sessionData: SessionData) {
        viewModelScope.launch {
            // 获取所有会话
            val allSessions = sessionRepository.sessionDataFlow.first()

            // 生成新名字（原名 + _N）
            val newName = generateCopyName(sessionData.name, allSessions)

            // 创建新会话（新 ID，新名字）
            val newSession =
                sessionData.copy(
                    id =
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                    name = newName,
                )

            sessionRepository.addSession(newSession)
        }
    }

    /**
     * 生成复制会话的名字（原名 + _N）
     * 例如：会话1 -> 会话1_2, 会话1_2 -> 会话1_3
     */
    private fun generateCopyName(
        originalName: String,
        existingSessions: List<SessionData>,
    ): String {
        val existingNames = existingSessions.map { it.name }.toSet()

        // 提取基础名字（去掉 _N 后缀）
        val baseName = originalName.replace(Regex("_\\d+$"), "")

        // 查找所有相同基础名字的会话
        val pattern = Regex("^${Regex.escape(baseName)}(?:_(\\d+))?$")
        val existingNumbers =
            existingNames.mapNotNull { name ->
                pattern
                    .matchEntire(name)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull() ?: 0
            }

        // 找到最大的数字，生成新的数字
        val maxNumber = existingNumbers.maxOrNull() ?: 0
        val newNumber = maxNumber + 1

        return "${baseName}_$newNumber"
    }

    /**
     * 更新会话的选中解码器
     * @param sessionId 会话 ID
     * @param videoDecoder 选中的视频解码器名称（null 表示不更新）
     * @param audioDecoder 选中的音频解码器名称（null 表示不更新）
     */
    suspend fun updateSelectedDecoders(
        sessionId: String,
        videoDecoder: String? = null,
        audioDecoder: String? = null,
    ) {
        val currentData = sessionRepository.getSessionData(sessionId) ?: return

        val updatedData =
            currentData.copy(
                selectedVideoDecoder =
                    videoDecoder ?: currentData.selectedVideoDecoder,
                selectedAudioDecoder =
                    audioDecoder ?: currentData.selectedAudioDecoder,
            )

        sessionRepository.updateSession(updatedData)
    }

    /**
     * 更新会话的远程编码器列表
     * @param sessionId 会话 ID
     * @param videoEncoders 远程视频编码器列表
     * @param audioEncoders 远程音频编码器列表
     */
    suspend fun updateRemoteEncoders(
        sessionId: String,
        videoEncoders: List<String>,
        audioEncoders: List<String>,
    ) {
        val currentData = sessionRepository.getSessionData(sessionId) ?: return

        val updatedData =
            currentData.copy(
                remoteVideoEncoders = videoEncoders,
                remoteAudioEncoders = audioEncoders,
            )

        sessionRepository.updateSession(updatedData)
    }

    // ============ Factory ============

    companion object {
        fun provideFactory(sessionRepository: SessionRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionViewModel(sessionRepository) as T
            }
    }
}
