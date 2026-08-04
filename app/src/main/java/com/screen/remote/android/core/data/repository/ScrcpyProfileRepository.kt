package com.screen.remote.android.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screen.remote.android.core.domain.model.ScrcpyProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

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

    private fun decodeProfiles(raw: String?): List<ScrcpyProfile> =
        runCatching {
            json.decodeFromString<List<ScrcpyProfile>>(raw ?: "[]")
        }.getOrDefault(emptyList())
            .ensureDefaultProfile()
            .sortedWith(compareBy<ScrcpyProfile> { it.sortOrder }.thenBy { it.name })

    private fun List<ScrcpyProfile>.ensureDefaultProfile(): List<ScrcpyProfile> =
        if (any { it.id == ScrcpyProfile.DEFAULT_ID }) this else listOf(ScrcpyProfile.default()) + this
}
