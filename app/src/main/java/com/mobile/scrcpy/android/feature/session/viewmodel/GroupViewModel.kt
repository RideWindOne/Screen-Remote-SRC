package com.mobile.scrcpy.android.feature.session.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mobile.scrcpy.android.core.common.AppConstants
import com.mobile.scrcpy.android.core.domain.model.DefaultGroups
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupType
import com.mobile.scrcpy.android.core.data.repository.GroupData
import com.mobile.scrcpy.android.core.data.repository.GroupRepository
import com.mobile.scrcpy.android.core.data.repository.SessionData
import com.mobile.scrcpy.android.core.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 分组管理 ViewModel
 * 职责：分组 CRUD、分组筛选、会话统计
 */
class GroupViewModel(
    private val groupRepository: GroupRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    // ============ 分组数据 ============

    val groups: StateFlow<List<DeviceGroup>> =
        groupRepository.groupsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(AppConstants.STATEFLOW_SUBSCRIBE_TIMEOUT_MS),
                initialValue = emptyList(),
            )

    // ============ 分组筛选状态 ============

    // 当前选中的分组路径（用于首页筛选）
    private val _selectedGroupPath = MutableStateFlow(DefaultGroups.ALL_DEVICES)
    val selectedGroupPath: StateFlow<String> = _selectedGroupPath.asStateFlow()

    // 自动化页面的分组路径（独立管理）
    private val _selectedAutomationGroupPath = MutableStateFlow(DefaultGroups.ALL_DEVICES)
    val selectedAutomationGroupPath: StateFlow<String> = _selectedAutomationGroupPath.asStateFlow()

    // ============ 筛选后的会话列表 ============

    private val sessionDataList: StateFlow<List<SessionData>> =
        sessionRepository.sessionDataFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(AppConstants.STATEFLOW_SUBSCRIBE_TIMEOUT_MS),
                initialValue = emptyList(),
            )

    val filteredSessions: StateFlow<List<SessionData>> =
        combine(
            sessionDataList,
            _selectedGroupPath,
            groups,
        ) { sessions, groupPath, groupsList ->
            when (groupPath) {
                DefaultGroups.ALL_DEVICES -> {
                    sessions
                }

                DefaultGroups.UNGROUPED -> {
                    sessions.filter { it.groupIds.isEmpty() }
                }

                else -> {
                    sessions.filter { session ->
                        // 检查 groupIds 是否包含当前分组或其子分组
                        val currentGroup = groupsList.find { it.path == groupPath }
                        if (currentGroup != null) {
                            session.groupIds.any { groupId ->
                                val group = groupsList.find { it.id == groupId }
                                group != null && (group.path == groupPath || group.path.startsWith("$groupPath/"))
                            }
                        } else {
                            false
                        }
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(AppConstants.STATEFLOW_SUBSCRIBE_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    // ============ 分组选择 ============

    /**
     * 选择分组路径（用于首页筛选）
     */
    fun selectGroup(groupPath: String) {
        _selectedGroupPath.value = groupPath
    }

    /**
     * 选择自动化分组路径
     */
    fun selectAutomationGroup(groupPath: String) {
        _selectedAutomationGroupPath.value = groupPath
    }

    // ============ 分组 CRUD ============

    /**
     * 添加分组
     */
    fun addGroup(
        name: String,
        parentPath: String,
        type: GroupType = GroupType.SESSION,
    ) {
        viewModelScope.launch {
            val path = if (parentPath == "/") "/$name" else "$parentPath/$name"
            val groupData =
                GroupData(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = type.name,
                    path = path,
                    parentPath = parentPath,
                    description = "",
                )
            groupRepository.addGroup(groupData)
        }
    }

    /**
     * 更新分组
     */
    fun updateGroup(group: DeviceGroup) {
        viewModelScope.launch {
            val groupData =
                GroupData(
                    id = group.id,
                    name = group.name,
                    type = group.type.name,
                    path = group.path,
                    parentPath = group.parentPath,
                    description = group.description,
                    createdAt = group.createdAt,
                )
            groupRepository.updateGroup(groupData)
        }
    }

    /**
     * 删除分组
     */
    fun removeGroup(groupId: String) {
        viewModelScope.launch {
            groupRepository.removeGroup(groupId)
        }
    }

    // ============ 统计功能 ============

    /**
     * 统计每个分组下的会话数量
     */
    fun getSessionCountByGroup(): Map<String, Int> {
        val countMap = mutableMapOf<String, Int>()
        sessionDataList.value.forEach { session ->
            session.groupIds.forEach { groupId ->
                countMap[groupId] = (countMap[groupId] ?: 0) + 1
            }
        }
        return countMap
    }

    // ============ Factory ============

    companion object {
        fun provideFactory(
            groupRepository: GroupRepository,
            sessionRepository: SessionRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GroupViewModel(groupRepository, sessionRepository) as T
            }
    }
}
