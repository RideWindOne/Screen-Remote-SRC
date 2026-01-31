package com.mobile.scrcpy.android.core.data.datastore

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    val deviceId: String = "", // 本地设备 ANDROID_ID
    val videoDecoders: List<String> = emptyList(), // 视频解码器列表
    val audioDecoders: List<String> = emptyList(), // 音频解码器列表
) {
    /**
     * 判断数据是否有效
     * @param currentDeviceId 当前设备 ID
     * @return 数据是否有效
     */
    fun isValid(currentDeviceId: String): Boolean {
        if (deviceId.isBlank() || currentDeviceId.isBlank()) return false
        return deviceId == currentDeviceId
    }
}

/**
 * 本地解码器管理器
 * 用于持久化保存本地设备的音视频解码器列表
 * 注意：解码器是本地设备的能力，所有会话共享同一份数据
 */
class LocalDecoderManager(
    private val context: Context,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val VIDEO_DECODERS = stringPreferencesKey("video_decoders")
        val AUDIO_DECODERS = stringPreferencesKey("audio_decoders")
    }

    /**
     * 获取本地设备 ID（ANDROID_ID）
     */
    @SuppressLint("HardwareIds")
    fun getLocalDeviceId(): String =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: ""

    /**
     * 数据流
     */
    val dataFlow: Flow<LocalDecoderData> =
        context.localDecoderDataStore.data.map { preferences ->
            LocalDecoderData(
                deviceId = preferences[Keys.DEVICE_ID] ?: "",
                videoDecoders =
                    try {
                        val json = preferences[Keys.VIDEO_DECODERS] ?: "[]"
                        this.json.decodeFromString<List<String>>(json)
                    } catch (e: Exception) {
                        emptyList()
                    },
                audioDecoders =
                    try {
                        val json = preferences[Keys.AUDIO_DECODERS] ?: "[]"
                        this.json.decodeFromString<List<String>>(json)
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
    suspend fun saveVideoDecoders(decoders: List<String>) {
        val deviceId = getLocalDeviceId()
        context.localDecoderDataStore.edit { preferences ->
            preferences[Keys.DEVICE_ID] = deviceId
            preferences[Keys.VIDEO_DECODERS] = json.encodeToString(decoders)
        }
    }

    /**
     * 保存音频解码器列表
     */
    suspend fun saveAudioDecoders(decoders: List<String>) {
        val deviceId = getLocalDeviceId()
        context.localDecoderDataStore.edit { preferences ->
            preferences[Keys.DEVICE_ID] = deviceId
            preferences[Keys.AUDIO_DECODERS] = json.encodeToString(decoders)
        }
    }

    /**
     * 保存完整数据
     */
    suspend fun saveData(data: LocalDecoderData) {
        context.localDecoderDataStore.edit { preferences ->
            preferences[Keys.DEVICE_ID] = data.deviceId
            preferences[Keys.VIDEO_DECODERS] = json.encodeToString(data.videoDecoders)
            preferences[Keys.AUDIO_DECODERS] = json.encodeToString(data.audioDecoders)
        }
    }

    /**
     * 清空数据
     */
    suspend fun clearData() {
        context.localDecoderDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
