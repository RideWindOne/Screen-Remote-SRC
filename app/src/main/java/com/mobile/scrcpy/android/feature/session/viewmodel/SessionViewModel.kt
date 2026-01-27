package com.mobile.scrcpy.android.feature.session.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mobile.scrcpy.android.feature.session.data.repository.SessionData
import com.mobile.scrcpy.android.feature.session.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 会话管理 ViewModel
 * 职责：会话 CRUD、对话框状态、编解码器缓存
 */
class SessionViewModel(
    private val sessionRepository: SessionRepository
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
        _editingSessionId.value = sessionId
        _showAddSessionDialog.value = true
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
     * 更新会话的编解码器缓存
     * @param sessionId 会话 ID
     * @param videoDecoder 视频解码器名称（null 表示不更新）
     * @param audioDecoder 音频解码器名称（null 表示不更新）
     */
    suspend fun updateCodecCache(
        sessionId: String,
        videoDecoder: String? = null,
        audioDecoder: String? = null
    ) {
        val currentData = sessionRepository.getSessionData(sessionId) ?: return

        val updatedData = currentData.copy(
            cachedVideoDecoder = videoDecoder ?: currentData.cachedVideoDecoder,
            cachedAudioDecoder = audioDecoder ?: currentData.cachedAudioDecoder,
            codecCacheTimestamp = System.currentTimeMillis()
        )

        sessionRepository.updateSession(updatedData)
    }

    // ============ Factory ============

    companion object {
        fun provideFactory(
            sessionRepository: SessionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SessionViewModel(sessionRepository) as T
            }
        }
    }
}
