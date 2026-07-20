package com.screen.remote.android.feature.device.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.core.common.util.formatHostPort
import com.screen.remote.android.core.common.util.normalizeEndpointHost
import kotlinx.coroutines.flow.Flow
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

@Serializable
data class PairingEndpointMetadataBackup(
    val metadata: List<PairingEndpointMetadata> = emptyList(),
    val mdnsPairings: List<MdnsPairingBackupRecord> = emptyList(),
)

@Serializable
data class MdnsPairingBackupRecord(
    val deviceKey: String,
    val endpoint: String,
    val updatedAtEpochMillis: Long = 0L,
)

@Serializable
private data class MdnsPairingRecord(
    val deviceKey: String,
    val endpoint: String,
    val updatedAtEpochMillis: Long,
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
        val MDNS_PAIRINGS = stringPreferencesKey("mdns_pairing_records")
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

    suspend fun exportBackup(): PairingEndpointMetadataBackup =
        context.pairingEndpointMetadataDataStore.data
            .map { preferences ->
                PairingEndpointMetadataBackup(
                    metadata = decode(preferences[Keys.METADATA]),
                    mdnsPairings =
                        decodeMdnsPairings(preferences[Keys.MDNS_PAIRINGS])
                            .map { record ->
                                MdnsPairingBackupRecord(
                                    deviceKey = record.deviceKey,
                                    endpoint = record.endpoint,
                                    updatedAtEpochMillis = record.updatedAtEpochMillis,
                                )
                            },
                )
            }.first()

    suspend fun importBackup(backup: PairingEndpointMetadataBackup) {
        context.pairingEndpointMetadataDataStore.edit { preferences ->
            preferences[Keys.METADATA] =
                json.encodeToString(
                    backup.metadata
                        .mapNotNull { metadata ->
                            val endpoint = normalizeEndpointHost(metadata.endpoint).lowercase()
                            if (endpoint.isBlank()) {
                                null
                            } else {
                                metadata.copy(endpoint = endpoint)
                            }
                        }.distinctBy { it.endpoint },
                )
            preferences[Keys.MDNS_PAIRINGS] =
                json.encodeToString(
                    backup.mdnsPairings
                        .mapNotNull { record ->
                            val deviceKey = DeviceTransportSerial.mdnsDeviceKey(record.deviceKey)
                            val endpoint = normalizeEndpointHost(record.endpoint).lowercase()
                            if (DeviceTransportSerial.mdnsDeviceSerial(deviceKey).isBlank() || endpoint.isBlank()) {
                                null
                            } else {
                                MdnsPairingRecord(
                                    deviceKey = deviceKey,
                                    endpoint = endpoint,
                                    updatedAtEpochMillis = record.updatedAtEpochMillis,
                                )
                            }
                        }.distinctBy { it.deviceKey },
                )
        }
    }

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

    suspend fun saveSuccessfulMdnsPairing(
        deviceSerial: String,
        endpoint: String,
    ) {
        val deviceKey = DeviceTransportSerial.mdnsDeviceKey(deviceSerial)
        val normalizedEndpoint = normalizeEndpointHost(endpoint).lowercase()
        if (DeviceTransportSerial.mdnsDeviceSerial(deviceKey).isBlank() || normalizedEndpoint.isBlank()) {
            return
        }

        context.pairingEndpointMetadataDataStore.edit { preferences ->
            val current = decodeMdnsPairings(preferences[Keys.MDNS_PAIRINGS])
            val updated =
                current.filterNot { it.deviceKey == deviceKey } +
                    MdnsPairingRecord(
                        deviceKey = deviceKey,
                        endpoint = normalizedEndpoint,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    )
            preferences[Keys.MDNS_PAIRINGS] = json.encodeToString(updated)
        }
    }

    val pairedMdnsDeviceKeysFlow: Flow<Set<String>> =
        context.pairingEndpointMetadataDataStore.data
            .map { preferences ->
                decodeMdnsPairings(preferences[Keys.MDNS_PAIRINGS])
                    .mapTo(linkedSetOf()) { it.deviceKey }
            }

    suspend fun getPairedMdnsDeviceKeys(): Set<String> = pairedMdnsDeviceKeysFlow.first()

    suspend fun listRecentSuccessfulPairings(): List<PairingHistoryItem> =
        context.pairingEndpointMetadataDataStore.data
            .map { preferences ->
                val endpointItems =
                    decode(preferences[Keys.METADATA]).map { metadata ->
                        PairingHistoryItem(
                            hostPort =
                                if (metadata.lastPairingPort.isNotBlank()) {
                                    formatHostPort(metadata.endpoint, metadata.lastPairingPort)
                                } else {
                                    metadata.endpoint
                                },
                            timestamp = metadata.updatedAtEpochMillis,
                        )
                    }
                val mdnsItems =
                    decodeMdnsPairings(preferences[Keys.MDNS_PAIRINGS]).map { record ->
                        PairingHistoryItem(
                            hostPort = record.endpoint,
                            timestamp = record.updatedAtEpochMillis,
                        )
                    }
                (endpointItems + mdnsItems)
                    .sortedByDescending(PairingHistoryItem::timestamp)
                    .distinctBy(PairingHistoryItem::hostPort)
            }.first()

    suspend fun removeEndpoint(endpoint: String) {
        val normalizedEndpoint = normalizeEndpointHost(endpoint).lowercase()
        if (normalizedEndpoint.isBlank()) {
            return
        }

        context.pairingEndpointMetadataDataStore.edit { preferences ->
            val updated = decode(preferences[Keys.METADATA]).filterNot { it.endpoint == normalizedEndpoint }
            preferences[Keys.METADATA] = json.encodeToString(updated)
            val updatedMdnsPairings =
                decodeMdnsPairings(preferences[Keys.MDNS_PAIRINGS])
                    .filterNot { it.endpoint == normalizedEndpoint }
            preferences[Keys.MDNS_PAIRINGS] = json.encodeToString(updatedMdnsPairings)
        }
    }

    suspend fun clear() {
        context.pairingEndpointMetadataDataStore.edit { preferences ->
            preferences.remove(Keys.METADATA)
            preferences.remove(Keys.MDNS_PAIRINGS)
        }
    }

    private fun decode(raw: String?): List<PairingEndpointMetadata> =
        runCatching {
            json.decodeFromString<List<PairingEndpointMetadata>>(raw ?: "[]")
        }.getOrDefault(emptyList())

    private fun decodeMdnsPairings(raw: String?): List<MdnsPairingRecord> =
        runCatching {
            json.decodeFromString<List<MdnsPairingRecord>>(raw ?: "[]")
        }.getOrDefault(emptyList())
}
