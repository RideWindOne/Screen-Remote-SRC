package com.screen.remote.android.core.data.datastore

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screen.remote.android.core.domain.model.DecoderCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.localDecoderDataStore: DataStore<Preferences> by preferencesDataStore(name = "local_decoders")

/**
 * 本地解码器数据
 */
@Serializable
data class LocalDecoderData(
    val runtimeSignature: String = "",
    val videoDecoders: List<DecoderCapability> = emptyList(),
    val audioDecoders: List<DecoderCapability> = emptyList(),
) {
    /**
     * 判断数据是否有效
     * @param currentDeviceId 当前设备 ID
     * @return 数据是否有效
     */
    fun isValid(currentRuntimeSignature: String): Boolean {
        if (runtimeSignature.isBlank() || currentRuntimeSignature.isBlank()) return false
        return runtimeSignature == currentRuntimeSignature
    }
}

/**
 * 本地解码器管理器
 * 用于持久化保存本地设备的音视频解码器列表
 * 注意：解码器是本地设备的能力，所有会话共享同一份数据
 */
class LocalDecoderManager(
    context: Context,
) {
    private val contentResolver = context.applicationContext.contentResolver
    private val dataStore = context.applicationContext.localDecoderDataStore

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private object Keys {
        val RUNTIME_SIGNATURE = stringPreferencesKey("codec_runtime_signature_v3")
        val VIDEO_DECODERS = stringPreferencesKey("video_decoder_capabilities_v3")
        val AUDIO_DECODERS = stringPreferencesKey("audio_decoder_capabilities_v3")
    }

    /**
     * 获取本地设备 ID（ANDROID_ID）
     */
    @SuppressLint("HardwareIds")
    fun getLocalRuntimeSignature(): String {
        val androidId =
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ANDROID_ID,
            ).orEmpty()
        return listOf(androidId, Build.FINGERPRINT, Build.VERSION.SDK_INT, Build.VERSION.SECURITY_PATCH).joinToString("|")
    }

    /**
     * 数据流
     */
    val dataFlow: Flow<LocalDecoderData> =
        dataStore.data.map { preferences ->
            LocalDecoderData(
                runtimeSignature = preferences[Keys.RUNTIME_SIGNATURE] ?: "",
                videoDecoders =
                    try {
                        val json = preferences[Keys.VIDEO_DECODERS] ?: "[]"
                        this.json.decodeFromString<List<DecoderCapability>>(json)
                    } catch (e: Exception) {
                        emptyList()
                    },
                audioDecoders =
                    try {
                        val json = preferences[Keys.AUDIO_DECODERS] ?: "[]"
                        this.json.decodeFromString<List<DecoderCapability>>(json)
                    } catch (e: Exception) {
                        emptyList()
                    },
            )
        }

    /**
     * 获取数据
     */
    suspend fun getData(): LocalDecoderData = dataFlow.first()

    /**
     * 保存视频解码器列表
     */
    suspend fun saveVideoDecoders(decoders: List<DecoderCapability>) {
        val runtimeSignature = getLocalRuntimeSignature()
        dataStore.edit { preferences ->
            invalidateCapabilitiesForNewRuntime(preferences, runtimeSignature)
            preferences[Keys.RUNTIME_SIGNATURE] = runtimeSignature
            preferences[Keys.VIDEO_DECODERS] = json.encodeToString(decoders)
        }
    }

    /**
     * 保存音频解码器列表
     */
    suspend fun saveAudioDecoders(decoders: List<DecoderCapability>) {
        val runtimeSignature = getLocalRuntimeSignature()
        dataStore.edit { preferences ->
            invalidateCapabilitiesForNewRuntime(preferences, runtimeSignature)
            preferences[Keys.RUNTIME_SIGNATURE] = runtimeSignature
            preferences[Keys.AUDIO_DECODERS] = json.encodeToString(decoders)
        }
    }

    /**
     * 保存完整数据
     */
    suspend fun saveData(data: LocalDecoderData) {
        dataStore.edit { preferences ->
            preferences[Keys.RUNTIME_SIGNATURE] = data.runtimeSignature
            preferences[Keys.VIDEO_DECODERS] = json.encodeToString(data.videoDecoders)
            preferences[Keys.AUDIO_DECODERS] = json.encodeToString(data.audioDecoders)
        }
    }

    /**
     * 清空数据
     */
    suspend fun clearData() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * 运行环境变化时，音频和视频能力必须作为同一快照一起失效。
     * 否则先刷新的媒体类型会写入新指纹，使另一媒体类型的旧列表被误判为有效。
     */
    private fun invalidateCapabilitiesForNewRuntime(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        runtimeSignature: String,
    ) {
        if (preferences[Keys.RUNTIME_SIGNATURE] == runtimeSignature) return
        preferences.remove(Keys.VIDEO_DECODERS)
        preferences.remove(Keys.AUDIO_DECODERS)
    }
}
