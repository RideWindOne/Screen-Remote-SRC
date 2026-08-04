package com.screen.remote.android.infrastructure.adb

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.adbEndpointTlsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "adb_endpoint_tls_state",
)

class AdbEndpointTlsDataStore(
    private val context: Context,
) {
    suspend fun findObservedConnectTlsPublicKey(endpoint: String): String? =
        findRecord(endpoint)?.observedConnectTlsPublicKeySha256Base64

    suspend fun clear(): Boolean {
        val hadState = listRecords().isNotEmpty()
        context.adbEndpointTlsDataStore.edit { preferences ->
            preferences.remove(Keys.STATE_JSON)
        }
        return hadState
    }

    suspend fun exportJson(): String? =
        context.adbEndpointTlsDataStore.data
            .map { preferences -> preferences[Keys.STATE_JSON] }
            .first()
            ?.takeIf { it.isNotBlank() }

    suspend fun importJson(rawJson: String?) {
        val normalizedRawJson = normalizeRawJson(rawJson)
        context.adbEndpointTlsDataStore.edit { preferences ->
            if (normalizedRawJson.isBlank()) {
                preferences.remove(Keys.STATE_JSON)
            } else {
                preferences[Keys.STATE_JSON] = normalizedRawJson
            }
        }
    }

    private suspend fun listRecords(): List<TlsEndpointRecord> =
        context.adbEndpointTlsDataStore.data
            .map { preferences -> decode(preferences[Keys.STATE_JSON]) }
            .first()
            .sortedBy { it.endpoint }

    private suspend fun findRecord(endpoint: String): TlsEndpointRecord? {
        val normalizedEndpoint = normalizeEndpoint(endpoint) ?: return null
        return listRecords().firstOrNull { it.endpoint == normalizedEndpoint }
    }

    private fun encode(records: List<TlsEndpointRecord>): String =
        JSONObject()
            .put(KEY_VERSION, CURRENT_VERSION)
            .put(
                KEY_ENDPOINT_RECORDS,
                JSONArray().apply {
                    records.sortedBy { it.endpoint }.forEach { put(it.toJson()) }
                },
            ).toString()

    private fun decode(raw: String?): List<TlsEndpointRecord> {
        val normalizedRaw = raw?.trim().orEmpty()
        if (normalizedRaw.isEmpty()) {
            return emptyList()
        }

        return runCatching {
            val document = JSONObject(normalizedRaw)
            val array = document.optJSONArray(KEY_ENDPOINT_RECORDS) ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    TlsEndpointRecord.fromJson(item)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun normalizeRawJson(rawJson: String?): String =
        encode(decode(rawJson))

    private fun normalizeEndpoint(endpoint: String): String? =
        endpoint.trim().takeIf { it.isNotEmpty() }?.lowercase()

    private object Keys {
        val STATE_JSON = stringPreferencesKey("adb_endpoint_tls_state_json")
    }

    private data class TlsEndpointRecord(
        val endpoint: String,
        val observedConnectTlsPublicKeySha256Base64: String?,
        val updatedAtEpochMillis: Long,
        val lastKnownTransport: String,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put(KEY_ENDPOINT, endpoint)
                .put(KEY_TRANSPORT, lastKnownTransport)
                .put(KEY_UPDATED_AT_EPOCH_MILLIS, updatedAtEpochMillis)
                .putOpt(KEY_OBSERVED_CONNECT_TLS_PUBLIC_KEY_SHA256_BASE64, observedConnectTlsPublicKeySha256Base64)

        companion object {
            fun fromJson(json: JSONObject): TlsEndpointRecord? {
                val endpoint = json.optString(KEY_ENDPOINT).trim().lowercase()
                if (endpoint.isEmpty()) {
                    return null
                }

                return TlsEndpointRecord(
                    endpoint = endpoint,
                    observedConnectTlsPublicKeySha256Base64 =
                        json.optString(KEY_OBSERVED_CONNECT_TLS_PUBLIC_KEY_SHA256_BASE64).ifBlank { null },
                    updatedAtEpochMillis = json.optLong(KEY_UPDATED_AT_EPOCH_MILLIS, 0),
                    lastKnownTransport = json.optString(KEY_TRANSPORT).ifBlank { TRANSPORT_TLS },
                )
            }
        }
    }

    private companion object {
        private const val CURRENT_VERSION = 3
        private const val KEY_VERSION = "version"
        private const val KEY_ENDPOINT_RECORDS = "endpointHints"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_TRANSPORT = "transport"
        private const val KEY_UPDATED_AT_EPOCH_MILLIS = "updatedAtEpochMillis"
        private const val KEY_OBSERVED_CONNECT_TLS_PUBLIC_KEY_SHA256_BASE64 = "connectTlsPublicKeySha256Base64"
        private const val TRANSPORT_TLS = "tls"
    }
}
