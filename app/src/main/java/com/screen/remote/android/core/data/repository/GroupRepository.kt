package com.screen.remote.android.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screen.remote.android.core.domain.model.DefaultGroups
import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.domain.model.GroupType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.groupDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_groups")

@Serializable
data class GroupData(
    val id: String,
    val name: String,
    val type: String = "SESSION",
    val path: String,
    val parentPath: String = "/",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

class GroupRepository(
    private val context: Context,
) : GroupRepositoryInterface {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private object Keys {
        val GROUPS = stringPreferencesKey("groups_list")
    }

    override val groupsFlow: Flow<List<DeviceGroup>> =
        context.groupDataStore.data.map { preferences ->
            val groupsJson = preferences[Keys.GROUPS] ?: "[]"
            try {
                val groupDataList = json.decodeFromString<List<GroupData>>(groupsJson)
                groupDataList.map { it.toDeviceGroup() }
            } catch (_: Exception) {
                emptyList()
            }
        }

    override fun getGroupsByType(type: GroupType): Flow<List<DeviceGroup>> =
        groupsFlow.map { groups -> groups.filter { it.type == type } }

    override suspend fun addGroup(groupData: GroupData) {
        context.groupDataStore.edit { preferences ->
            val currentJson = preferences[Keys.GROUPS] ?: "[]"
            val currentList =
                try {
                    json.decodeFromString<List<GroupData>>(currentJson)
                } catch (_: Exception) {
                    emptyList()
                }
            preferences[Keys.GROUPS] = json.encodeToString(currentList + groupData)
        }
    }

    override suspend fun removeGroup(id: String) {
        if (id == DefaultGroups.ALL_DEVICES || id == DefaultGroups.UNGROUPED) {
            return
        }

        context.groupDataStore.edit { preferences ->
            val currentJson = preferences[Keys.GROUPS] ?: "[]"
            val currentList =
                try {
                    json.decodeFromString<List<GroupData>>(currentJson)
                } catch (_: Exception) {
                    emptyList()
                }
            preferences[Keys.GROUPS] = json.encodeToString(currentList.filter { it.id != id })
        }
    }

    override suspend fun updateGroup(groupData: GroupData) {
        context.groupDataStore.edit { preferences ->
            val currentJson = preferences[Keys.GROUPS] ?: "[]"
            val currentList =
                try {
                    json.decodeFromString<List<GroupData>>(currentJson)
                } catch (_: Exception) {
                    emptyList()
                }
            val updatedList =
                currentList.map {
                    if (it.id == groupData.id) groupData else it
                }
            preferences[Keys.GROUPS] = json.encodeToString(updatedList)
        }
    }

    override suspend fun getGroup(id: String): DeviceGroup? {
        val currentJson =
            context.groupDataStore.data
                .map { preferences ->
                    preferences[Keys.GROUPS] ?: "[]"
                }.first()
        return try {
            json
                .decodeFromString<List<GroupData>>(currentJson)
                .find { it.id == id }
                ?.toDeviceGroup()
        } catch (_: Exception) {
            null
        }
    }

    private fun GroupData.toDeviceGroup() =
        DeviceGroup(
            id = id,
            name = name,
            type =
                try {
                    GroupType.valueOf(type)
                } catch (_: Exception) {
                    GroupType.SESSION
                },
            path = path,
            parentPath = parentPath,
            description = description,
            createdAt = createdAt,
        )
}
