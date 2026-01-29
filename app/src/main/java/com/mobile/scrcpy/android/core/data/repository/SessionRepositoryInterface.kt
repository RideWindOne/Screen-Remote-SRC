package com.mobile.scrcpy.android.core.data.repository

import com.mobile.scrcpy.android.core.domain.model.ScrcpySession
import com.mobile.scrcpy.android.feature.session.data.repository.SessionData
import kotlinx.coroutines.flow.Flow

/**
 * Session Repository 接口定义
 *
 * 定义会话数据的 CRUD 操作接口，遵循依赖倒置原则
 */
interface SessionRepositoryInterface {
    /**
     * 获取所有会话的 Flow
     */
    val sessionsFlow: Flow<List<ScrcpySession>>

    /**
     * 获取所有会话数据的 Flow
     */
    val sessionDataFlow: Flow<List<SessionData>>

    /**
     * 添加新会话
     */
    suspend fun addSession(sessionData: SessionData)

    /**
     * 删除会话
     */
    suspend fun removeSession(id: String)

    /**
     * 更新会话
     */
    suspend fun updateSession(sessionData: SessionData)

    /**
     * 根据 ID 获取会话数据
     */
    suspend fun getSessionData(id: String): SessionData?

    /**
     * 根据 ID 获取会话数据的 Flow
     */
    fun getSessionDataFlow(id: String): Flow<SessionData?>
}
