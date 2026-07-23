package com.screen.remote.android.core.telemetry

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.telemetryDataStore: DataStore<Preferences> by preferencesDataStore(name = "telemetry")

data class TelemetryState(
    val enabled: Boolean,
    val lastUploadedLogDate: String?,
)

class TelemetryPreferences(
    context: Context,
) {
    private val dataStore = context.applicationContext.telemetryDataStore

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val INSTALLATION_ID = stringPreferencesKey("installation_id")
        val LAST_UPLOADED_LOG_DATE = stringPreferencesKey("last_uploaded_log_date")
    }

    val stateFlow: Flow<TelemetryState> =
        dataStore.data.map { preferences ->
            TelemetryState(
                enabled = preferences[Keys.ENABLED] ?: true,
                lastUploadedLogDate = preferences[Keys.LAST_UPLOADED_LOG_DATE],
            )
        }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.ENABLED] = enabled
        }
    }

    suspend fun getOrCreateInstallationId(): String {
        dataStore.data.first()[Keys.INSTALLATION_ID]?.let { return it }
        val generated = UUID.randomUUID().toString()
        var stored = generated
        dataStore.edit { preferences ->
            stored = preferences[Keys.INSTALLATION_ID] ?: generated
            preferences[Keys.INSTALLATION_ID] = stored
        }
        return stored
    }

    suspend fun markLogUploaded(logDate: String) {
        dataStore.edit { preferences ->
            preferences[Keys.LAST_UPLOADED_LOG_DATE] = logDate
        }
    }

}
