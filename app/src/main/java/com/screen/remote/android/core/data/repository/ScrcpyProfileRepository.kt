package com.screen.remote.android.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screen.remote.android.core.domain.model.ScrcpyProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.scrcpyProfileDataStore: DataStore<Preferences> by preferencesDataStore(name = "scrcpy_profiles")

class ScrcpyProfileRepository(
    private val context: Context,
) {
    private object Keys {
        val PROFILES = stringPreferencesKey("profiles")
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    val profilesFlow: Flow<List<ScrcpyProfile>> =
        context.scrcpyProfileDataStore.data.map { preferences ->
            decodeProfiles(preferences[Keys.PROFILES])
        }

    suspend fun getProfiles(): List<ScrcpyProfile> = profilesFlow.first()

    suspend fun getProfile(id: String): ScrcpyProfile? =
        getProfiles().firstOrNull { it.id == id }

    suspend fun upsertProfile(profile: ScrcpyProfile) {
        context.scrcpyProfileDataStore.edit { preferences ->
            val current = decodeProfiles(preferences[Keys.PROFILES])
            val updated =
                (current.filterNot { it.id == profile.id } + profile)
                    .ensureDefaultProfile()
                    .sortedWith(compareBy<ScrcpyProfile> { it.sortOrder }.thenBy { it.name })
            preferences[Keys.PROFILES] = json.encodeToString(updated)
        }
    }

    suspend fun copyProfile(
        sourceId: String,
        name: String,
    ): ScrcpyProfile? {
        val source = getProfile(sourceId) ?: return null
        val copy =
            source.copy(
                id = UUID.randomUUID().toString(),
                name = name,
                sortOrder = (getProfiles().maxOfOrNull { it.sortOrder } ?: 0) + 1,
            )
        upsertProfile(copy)
        return copy
    }

    suspend fun renameProfile(
        id: String,
        name: String,
    ) {
        val current = getProfile(id) ?: return
        upsertProfile(current.copy(name = name.ifBlank { current.name }))
    }

    suspend fun reorderProfiles(orderedIds: List<String>) {
        context.scrcpyProfileDataStore.edit { preferences ->
            val order = orderedIds.withIndex().associate { it.value to it.index }
            val updated =
                decodeProfiles(preferences[Keys.PROFILES])
                    .map { profile ->
                        if (profile.id == ScrcpyProfile.DEFAULT_ID) {
                            profile.copy(sortOrder = Int.MIN_VALUE)
                        } else {
                            profile.copy(sortOrder = order[profile.id] ?: profile.sortOrder)
                        }
                    }.ensureDefaultProfile()
            preferences[Keys.PROFILES] = json.encodeToString(updated)
        }
    }

    suspend fun deleteProfile(id: String) {
        if (id == ScrcpyProfile.DEFAULT_ID) return
        context.scrcpyProfileDataStore.edit { preferences ->
            val updated = decodeProfiles(preferences[Keys.PROFILES]).filterNot { it.id == id }.ensureDefaultProfile()
            preferences[Keys.PROFILES] = json.encodeToString(updated)
        }
    }

    private fun decodeProfiles(raw: String?): List<ScrcpyProfile> =
        runCatching {
            json.decodeFromString<List<ScrcpyProfile>>(raw ?: "[]")
        }.getOrDefault(emptyList())
            .ensureDefaultProfile()
            .sortedWith(compareBy<ScrcpyProfile> { it.sortOrder }.thenBy { it.name })

    private fun List<ScrcpyProfile>.ensureDefaultProfile(): List<ScrcpyProfile> =
        if (any { it.id == ScrcpyProfile.DEFAULT_ID }) this else listOf(ScrcpyProfile.default()) + this
}
