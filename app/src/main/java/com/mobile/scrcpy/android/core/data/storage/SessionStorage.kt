package com.mobile.scrcpy.android.core.data.storage

import android.content.Context
import com.mobile.scrcpy.android.core.data.repository.SessionRepository
import com.mobile.scrcpy.android.core.domain.model.ScrcpyOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 会话配置存储 - 持久化存储所有设备的 ScrcpyOptions
 * 
 * 职责：
 * - 持久化存储所有设备的配置
 * - 提供配置的增删改查
 * - 支持配置的部分更新
 * 
 * 存储时机：
 * - 首次创建：使用默认值
 * - UI 编辑：用户修改字段后保存
 * - 连接过程：检测到设备能力后保存
 * - 会话结束：配置保留，运行态清理
 * 
 * 使用示例：
 * ```kotlin
 * val storage = SessionStorage(context)
 * 
 * // 获取配置
 * val options = storage.getOptions(sessionId)
 * 
 * // 保存配置
 * storage.saveOptions(options)
 * 
 * // 更新配置
 * storage.updateOptions(sessionId) { it.copy(maxSize = 1080) }
 * 
 * // 获取所有会话
 * val allSessions = storage.getAllSessions()
 * ```
 */
class SessionStorage(
    private val context: Context,
) {
    private val repository = SessionRepository(context)

    /**
     * 获取配置
     */
    suspend fun getOptions(sessionId: String): ScrcpyOptions? {
        val sessionData = repository.getSessionData(sessionId) ?: return null
        return sessionData.toScrcpyOptions()
    }

    /**
     * 保存配置
     */
    suspend fun saveOptions(options: ScrcpyOptions) {
        val sessionData = repository.getSessionData(options.sessionId)
        if (sessionData != null) {
            // 更新现有会话
            repository.updateSessionFields(options.sessionId) { current ->
                current.fromScrcpyOptions(options)
            }
        } else {
            // 创建新会话（需要额外的会话信息）
            throw IllegalStateException("会话不存在，请先创建会话: ${options.sessionId}")
        }
    }

    /**
     * 更新配置（部分更新）
     */
    suspend fun updateOptions(
        sessionId: String,
        update: (ScrcpyOptions) -> ScrcpyOptions,
    ) {
        val current = getOptions(sessionId) ?: return
        val updated = update(current)
        saveOptions(updated)
    }

    /**
     * 删除配置
     */
    suspend fun deleteOptions(sessionId: String) {
        repository.removeSession(sessionId)
    }

    /**
     * 获取所有会话配置
     */
    suspend fun getAllSessions(): List<ScrcpyOptions> {
        return repository.sessionDataFlow
            .map { list -> list.map { it.toScrcpyOptions() } }
            .first()
    }

    /**
     * 获取配置 Flow
     */
    fun getOptionsFlow(sessionId: String): Flow<ScrcpyOptions?> {
        return repository.getSessionDataFlow(sessionId)
            .map { it?.toScrcpyOptions() }
    }

    /**
     * 获取所有会话配置 Flow
     */
    fun getAllSessionsFlow(): Flow<List<ScrcpyOptions>> {
        return repository.sessionDataFlow
            .map { list -> list.map { it.toScrcpyOptions() } }
    }
}
