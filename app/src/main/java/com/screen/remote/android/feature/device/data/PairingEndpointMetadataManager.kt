package com.screen.remote.android.feature.device.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screen.remote.android.core.common.util.formatHostPort
import com.screen.remote.android.core.common.util.normalizeEndpointHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.pairingEndpointMetadataDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pairing_endpoint_metadata",
)

@Serializable
data class PairingEndpointMetadata(
    val endpoint: String,
    val lastPairingPort: String = "",
    val deviceName: String = "Android Device",
    val updatedAtEpochMillis: Long = 0L,
)

class PairingEndpointMetadataManager(
    private val context: Context,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private object Keys {
        val METADATA = stringPreferencesKey("pairing_endpoint_metadata")
    }

    suspend fun getAll(): Map<String, PairingEndpointMetadata> =
        context.pairingEndpointMetadataDataStore.data
            .map { preferences ->
                val raw = preferences[Keys.METADATA] ?: "[]"
                runCatching {
                    json.decodeFromString<List<PairingEndpointMetadata>>(raw)
                        .associateBy { it.endpoint.trim().lowercase() }
                }.getOrDefault(emptyMap())
                }.first()

    suspend fun saveSuccessfulPairing(
        endpoint: String,
        port: String,
        deviceName: String = "Android Device",
    ) {
        val normalizedEndpoint = normalizeEndpointHost(endpoint).lowercase()
        val normalizedPort = port.trim()
        if (normalizedEndpoint.isBlank() || normalizedPort.isBlank()) {
            return
        }

        context.pairingEndpointMetadataDataStore.edit { preferences ->
            val current = decode(preferences[Keys.METADATA])
            val updated =
                current
                    .filterNot { it.endpoint == normalizedEndpoint } +
                    PairingEndpointMetadata(
                        endpoint = normalizedEndpoint,
                        lastPairingPort = normalizedPort,
                        deviceName = deviceName.ifBlank { "Android Device" },
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    )
            preferences[Keys.METADATA] = json.encodeToString(updated)
        }
    }

    suspend fun listRecentSuccessfulPairings(): List<PairingHistoryItem> =
        getAll()
            .values
            .sortedByDescending { it.updatedAtEpochMillis }
            .map { metadata ->
                PairingHistoryItem(
                    hostPort =
                        if (metadata.lastPairingPort.isNotBlank()) {
                            formatHostPort(metadata.endpoint, metadata.lastPairingPort)
                        } else {
                            metadata.endpoint
                        },
                    timestamp =
                        metadata.updatedAtEpochMillis.takeIf { value -> value > 0 }
                            ?: System.currentTimeMillis(),
                )
            }

    suspend fun removeEndpoint(endpoint: String) {
        val normalizedEndpoint = normalizeEndpointHost(endpoint).lowercase()
        if (normalizedEndpoint.isBlank()) {
            return
        }

        context.pairingEndpointMetadataDataStore.edit { preferences ->
            val updated = decode(preferences[Keys.METADATA]).filterNot { it.endpoint == normalizedEndpoint }
            preferences[Keys.METADATA] = json.encodeToString(updated)
        }
    }

    suspend fun clear() {
        context.pairingEndpointMetadataDataStore.edit { preferences ->
            preferences.remove(Keys.METADATA)
        }
    }

    private fun decode(raw: String?): List<PairingEndpointMetadata> =
        runCatching {
            json.decodeFromString<List<PairingEndpointMetadata>>(raw ?: "[]")
        }.getOrDefault(emptyList())
}
