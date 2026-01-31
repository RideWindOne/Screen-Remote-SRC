package com.mobile.scrcpy.android.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobile.scrcpy.android.core.common.ScrcpyConstants
import com.mobile.scrcpy.android.core.domain.model.ScrcpyOptions
import com.mobile.scrcpy.android.core.domain.model.ScrcpySession
import com.mobile.scrcpy.android.core.domain.model.SessionColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sessions")

@Serializable
data class SessionData(
    val id: String,
    val name: String,
    val host: String,
    val port: String,
    val color: String,
    val forceAdb: Boolean = false,
    val maxSize: String = "",
    val videoBitrate: String = "",
    val maxFps: String = "", // 最大帧率
    val preferredVideoCodec: String = ScrcpyConstants.DEFAULT_VIDEO_CODEC,
    val userVideoEncoder: String = "",
    val userVideoDecoder: String = "",
    val enableAudio: Boolean = false,
    val preferredAudioCodec: String = ScrcpyConstants.DEFAULT_AUDIO_CODEC,
    val userAudioEncoder: String = "",
    val userAudioDecoder: String = "",
    val audioBitrate: String = "", // 音频码率（如 128k）
    val audioBufferMs: String = "", // 音频缓冲（毫秒，自动计算）
    val videoBufferMs: String = "", // 视频缓冲（毫秒，用户配置）
    val keyFrameInterval: Int = 2, // 关键帧间隔（秒），默认 2 秒
    val stayAwake: Boolean = false, // 改为 false，不强制保持唤醒
    val turnScreenOff: Boolean = true,
    val powerOffOnClose: Boolean = false,
    val useFullScreen: Boolean = false, // 全屏模式（TextureView），默认关闭
    // 设备信息和编解码器
    val deviceSerial: String = "", // 设备序列号（通过 ro.serialno 获取），用于验证编解码器是否匹配当前设备
    val remoteVideoEncoders: List<String> = emptyList(), // 远程设备视频编码器列表
    val remoteAudioEncoders: List<String> = emptyList(), // 远程设备音频编码器列表
    val selectedVideoDecoder: String = "", // 选中的最佳视频解码器
    val selectedAudioDecoder: String = "", // 选中的最佳音频解码器
    val selectedVideoEncoder: String = "", // 选中的最佳视频编解器
    val selectedAudioEncoder: String = "", // 选中的最佳音频编解器
    // 分组信息
    val groupIds: List<String> = emptyList(), // 所属分组 ID 列表，支持多分组
) {
    /**
     * 判断是否为 USB 连接
     */
    fun isUsbConnection(): Boolean = port == "0" || port.isBlank()

    /**
     * 获取 USB 序列号（仅 USB 模式有效）
     */
    fun getUsbSerialNumber(): String? = if (isUsbConnection()) host else null

    /**
     * 判断编解码器是否匹配当前设备
     * @param deviceSerial 当前设备序列号
     * @return 是否匹配
     */
    fun isEncoderListValid(deviceSerial: String): Boolean {
        // 如果没有保存的序列号，说明从未连接过
        if (this.deviceSerial.isBlank()) return false

        // 如果当前设备序列号为空，无法验证
        if (deviceSerial.isBlank()) return false

        // 序列号必须匹配
        if (this.deviceSerial != deviceSerial) return false

        // 必须有编解码器列表
        if (remoteVideoEncoders.isEmpty() && remoteAudioEncoders.isEmpty()) return false

        return true
    }

    /**
     * 获取设备唯一标识
     * USB 模式使用序列号，TCP 模式使用 host:port
     */
    fun getDeviceIdentifier(): String = if (isUsbConnection()) host else "$host:$port"

    /**
     * 转换为 ScrcpyOptions
     */
    fun toScrcpyOptions(): ScrcpyOptions =
        ScrcpyOptions(
            sessionId = id,
            host = host,
            port = port.toIntOrNull() ?: 0,
            maxSize = maxSize.toIntOrNull() ?: 1920,
            videoBitRate = videoBitrate.toIntOrNull() ?: 8000000,
            maxFps = maxFps.toIntOrNull() ?: 60,
            preferredVideoCodec = preferredVideoCodec,
            userVideoEncoder = userVideoEncoder,
            userVideoDecoder = userVideoDecoder,
            enableAudio = enableAudio,
            preferredAudioCodec = preferredAudioCodec,
            userAudioEncoder = userAudioEncoder,
            userAudioDecoder = userAudioDecoder,
            audioBitRate = audioBitrate.toIntOrNull() ?: 128000,
            audioBufferMs = audioBufferMs.toIntOrNull(),
            keyFrameInterval = keyFrameInterval,
            stayAwake = stayAwake,
            turnScreenOff = turnScreenOff,
            powerOffOnClose = powerOffOnClose,
            selectedVideoEncoder = selectedVideoEncoder,
            selectedAudioEncoder = selectedAudioEncoder,
            selectedVideoDecoder = selectedVideoDecoder,
            selectedAudioDecoder = selectedAudioDecoder,
            deviceSerial = deviceSerial,
            remoteVideoEncoders = remoteVideoEncoders,
            remoteAudioEncoders = remoteAudioEncoders,
        )

    /**
     * 从 ScrcpyOptions 更新字段
     */
    fun fromScrcpyOptions(options: ScrcpyOptions): SessionData =
        copy(
            maxSize = options.maxSize.toString(),
            videoBitrate = options.videoBitRate.toString(),
            maxFps = options.maxFps.toString(),
            preferredVideoCodec = options.preferredVideoCodec,
            userVideoEncoder = options.userVideoEncoder,
            userVideoDecoder = options.userVideoDecoder,
            enableAudio = options.enableAudio,
            preferredAudioCodec = options.preferredAudioCodec,
            userAudioEncoder = options.userAudioEncoder,
            userAudioDecoder = options.userAudioDecoder,
            audioBitrate = options.audioBitRate.toString(),
            audioBufferMs = options.audioBufferMs?.toString() ?: "",
            keyFrameInterval = options.keyFrameInterval,
            stayAwake = options.stayAwake,
            turnScreenOff = options.turnScreenOff,
            powerOffOnClose = options.powerOffOnClose,
            selectedVideoEncoder = options.selectedVideoEncoder,
            selectedAudioEncoder = options.selectedAudioEncoder,
            selectedVideoDecoder = options.selectedVideoDecoder,
            selectedAudioDecoder = options.selectedAudioDecoder,
            deviceSerial = options.deviceSerial,
            remoteVideoEncoders = options.remoteVideoEncoders,
            remoteAudioEncoders = options.remoteAudioEncoders,
        )
}

class SessionRepository(
    private val context: Context,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private object Keys {
        val SESSIONS = stringPreferencesKey("sessions_list")
    }

    val sessionsFlow: Flow<List<ScrcpySession>> =
        context.sessionDataStore.data.map { preferences ->
            val sessionsJson = preferences[Keys.SESSIONS] ?: "[]"
            try {
                val sessionDataList = json.decodeFromString<List<SessionData>>(sessionsJson)
                sessionDataList.map { it.toScrcpySession() }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun addSession(sessionData: SessionData) {
        context.sessionDataStore.edit { preferences ->
            val currentJson = preferences[Keys.SESSIONS] ?: "[]"
            val currentList =
                try {
                    json.decodeFromString<List<SessionData>>(currentJson)
                } catch (e: Exception) {
                    emptyList()
                }
            val updatedList = currentList + sessionData
            preferences[Keys.SESSIONS] = json.encodeToString(updatedList)
        }
    }

    suspend fun removeSession(id: String) {
        context.sessionDataStore.edit { preferences ->
            val currentJson = preferences[Keys.SESSIONS] ?: "[]"
            val currentList =
                try {
                    json.decodeFromString<List<SessionData>>(currentJson)
                } catch (e: Exception) {
                    emptyList()
                }
            val updatedList = currentList.filter { it.id != id }
            preferences[Keys.SESSIONS] = json.encodeToString(updatedList)
        }
    }

    suspend fun updateSession(sessionData: SessionData) {
        context.sessionDataStore.edit { preferences ->
            val currentJson = preferences[Keys.SESSIONS] ?: "[]"
            val currentList =
                try {
                    json.decodeFromString<List<SessionData>>(currentJson)
                } catch (e: Exception) {
                    emptyList()
                }
            val updatedList =
                currentList.map {
                    if (it.id == sessionData.id) sessionData else it
                }
            preferences[Keys.SESSIONS] = json.encodeToString(updatedList)
        }
    }

    /**
     * 部分更新会话数据（只更新指定字段）
     * @param id 会话 ID
     * @param update 更新函数，接收当前 SessionData，返回更新后的 SessionData
     */
    suspend fun updateSessionFields(
        id: String,
        update: (SessionData) -> SessionData,
    ) {
        context.sessionDataStore.edit { preferences ->
            val currentJson = preferences[Keys.SESSIONS] ?: "[]"
            val currentList =
                try {
                    json.decodeFromString<List<SessionData>>(currentJson)
                } catch (e: Exception) {
                    emptyList()
                }
            val updatedList =
                currentList.map {
                    if (it.id == id) update(it) else it
                }
            preferences[Keys.SESSIONS] = json.encodeToString(updatedList)
        }
    }

    suspend fun getSessionData(id: String): SessionData? {
        val currentJson =
            context.sessionDataStore.data
                .map { preferences ->
                    preferences[Keys.SESSIONS] ?: "[]"
                }.first()
        return try {
            json.decodeFromString<List<SessionData>>(currentJson).find { it.id == id }
        } catch (e: Exception) {
            null
        }
    }

    fun getSessionDataFlow(id: String): Flow<SessionData?> =
        context.sessionDataStore.data.map { preferences ->
            val sessionsJson = preferences[Keys.SESSIONS] ?: "[]"
            try {
                json.decodeFromString<List<SessionData>>(sessionsJson).find { it.id == id }
            } catch (e: Exception) {
                null
            }
        }

    val sessionDataFlow: Flow<List<SessionData>> =
        context.sessionDataStore.data.map { preferences ->
            val sessionsJson = preferences[Keys.SESSIONS] ?: "[]"
            try {
                json.decodeFromString<List<SessionData>>(sessionsJson)
            } catch (e: Exception) {
                emptyList()
            }
        }

    private fun SessionData.toScrcpySession() =
        ScrcpySession(
            id = id,
            name = name,
            color = SessionColor.valueOf(color),
            isConnected = false,
            hasWifi = host.isNotBlank(),
            hasWarning = false,
        )
}
