package com.screen.remote.android.core.data.storage

import android.content.Context
import com.screen.remote.android.core.data.repository.ScrcpyProfileRepository
import com.screen.remote.android.core.data.repository.SessionRepository
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.core.domain.model.withProfile

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
 * storage.updateOptions(sessionId) { it.copy(config = it.config.copy(maxSize = 1080)) }
 *
 * // 获取所有会话
 * val allSessions = storage.getAllSessions()
 * ```
 */
class SessionStorage(
    context: Context,
) {
    private val repository = SessionRepository(context)
    private val profileRepository = ScrcpyProfileRepository(context)

    /**
     * 获取配置
     */
    suspend fun getOptions(sessionId: String): ScrcpyOptions? {
        val sessionData = repository.getSessionData(sessionId) ?: return null
        return sessionData.toScrcpyOptions().applyProfileIfNeeded()
    }

    /**
     * 更新配置（部分更新）
     */
    suspend fun updateOptions(
        sessionId: String,
        update: (ScrcpyOptions) -> ScrcpyOptions,
    ) {
        // 在 DataStore 的原子 edit 中读取并写回，避免多个后台能力更新各自拿着
        // 旧快照覆盖对方。这里只更新会话自身存储值，不把 profile 展开后写回。
        repository.updateSessionFields(sessionId) { current ->
            current.fromScrcpyOptions(update(current.toScrcpyOptions()))
        }
    }

    private suspend fun ScrcpyOptions.applyProfileIfNeeded(): ScrcpyOptions {
        val profile = profileId.takeIf { it.isNotBlank() }?.let { profileRepository.getProfile(it) } ?: return this
        return withProfile(profile).copy(
            sessionId = sessionId,
            profileId = profileId,
            connectionCandidates = connectionCandidates,
        )
    }
}
