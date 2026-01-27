package com.mobile.scrcpy.android.feature.session.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobile.scrcpy.android.core.data.repository.GroupRepositoryInterface
import com.mobile.scrcpy.android.core.domain.model.DefaultGroups
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.groupDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_groups")

@Serializable
data class GroupData(
    val id: String,
    val name: String,              // 分组名称，如 "HZ"
    val type: String = "SESSION",  // 分组类型：SESSION 或 AUTOMATION
    val path: String,              // 完整路径，如 "/FRP/HZ"
    val parentPath: String = "/",  // 父路径，如 "/FRP"
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

class GroupRepository(private val context: Context) : GroupRepositoryInterface {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val GROUPS = stringPreferencesKey("groups_list")
    }

    override val groupsFlow: Flow<List<DeviceGroup>> = context.groupDataStore.data.map { preferences ->
        val groupsJson = preferences[Keys.GROUPS] ?: "[]"
        try {
            val groupDataList = json.decodeFromString<List<GroupData>>(groupsJson)
            groupDataList.map { it.toDeviceGroup() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 按类型过滤分组
     */
    override fun getGroupsByType(type: GroupType): Flow<List<DeviceGroup>> =
        groupsFlow.map { groups -> groups.filter { it.type == type } }

    override suspend fun addGroup(groupData: GroupData) {
        context.groupDataStore.edit { preferences ->
            val currentJson = preferences[Keys.GROUPS] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<GroupData>>(currentJson)
            } catch (e: Exception) {
                emptyList()
            }
            val updatedList = currentList + groupData
            preferences[Keys.GROUPS] = json.encodeToString(updatedList)
        }
    }

    override suspend fun removeGroup(id: String) {
        // 不允许删除默认分组
        if (id == DefaultGroups.ALL_DEVICES || id == DefaultGroups.UNGROUPED) {
            return
        }
        
        context.groupDataStore.edit { preferences ->
            val currentJson = preferences[Keys.GROUPS] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<GroupData>>(currentJson)
            } catch (e: Exception) {
                emptyList()
            }
            val updatedList = currentList.filter { it.id != id }
            preferences[Keys.GROUPS] = json.encodeToString(updatedList)
        }
    }

    override suspend fun updateGroup(groupData: GroupData) {
        context.groupDataStore.edit { preferences ->
            val currentJson = preferences[Keys.GROUPS] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<GroupData>>(currentJson)
            } catch (e: Exception) {
                emptyList()
            }
            val updatedList = currentList.map {
                if (it.id == groupData.id) groupData else it
            }
            preferences[Keys.GROUPS] = json.encodeToString(updatedList)
        }
    }

    override suspend fun getGroup(id: String): DeviceGroup? {
        val currentJson = context.groupDataStore.data.map { preferences ->
            preferences[Keys.GROUPS] ?: "[]"
        }.first()
        return try {
            json.decodeFromString<List<GroupData>>(currentJson)
                .find { it.id == id }
                ?.toDeviceGroup()
        } catch (e: Exception) {
            null
        }
    }

    private fun GroupData.toDeviceGroup() = DeviceGroup(
        id = id,
        name = name,
        type = try { 
            GroupType.valueOf(type) 
        } catch (e: Exception) { 
            GroupType.SESSION 
        },
        path = path,
        parentPath = parentPath,
        description = description,
        createdAt = createdAt
    )
}
