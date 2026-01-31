package com.mobile.scrcpy.android.core.data.repository

import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupType
import com.mobile.scrcpy.android.core.data.repository.GroupData
import kotlinx.coroutines.flow.Flow

/**
 * Group Repository 接口定义
 *
 * 定义分组数据的 CRUD 操作接口，遵循依赖倒置原则
 */
interface GroupRepositoryInterface {
    /**
     * 获取所有分组的 Flow
     */
    val groupsFlow: Flow<List<DeviceGroup>>

    /**
     * 按类型过滤分组
     */
    fun getGroupsByType(type: GroupType): Flow<List<DeviceGroup>>

    /**
     * 添加新分组
     */
    suspend fun addGroup(groupData: GroupData)

    /**
     * 删除分组
     */
    suspend fun removeGroup(id: String)

    /**
     * 更新分组
     */
    suspend fun updateGroup(groupData: GroupData)

    /**
     * 根据 ID 获取分组
     */
    suspend fun getGroup(id: String): DeviceGroup?
}
