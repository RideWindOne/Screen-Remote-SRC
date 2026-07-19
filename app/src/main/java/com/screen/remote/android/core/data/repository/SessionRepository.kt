package com.screen.remote.android.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screen.remote.android.core.common.ScrcpyConstants
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.DeviceCapabilityCache
import com.screen.remote.android.core.domain.model.ScrcpyConfig
import com.screen.remote.android.core.domain.model.toAddressEndpoint
import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.core.domain.model.ScrcpySession
import com.screen.remote.android.core.domain.model.SessionColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sessions")

@Serializable
data class TcpPortForwardRule(
    val targetHost: String = "192.168.1.1",
    val targetPort: Int = 80,
    val localPort: Int = 18080,
)

@Serializable
data class SessionData(
    val id: String,
    val name: String,
    val connectionCandidates: List<ConnectionCandidateData>,
    val color: String,
    val config: ScrcpyConfig = ScrcpyConfig(),
    val capabilityCache: DeviceCapabilityCache = DeviceCapabilityCache(),
    val profileId: String = "",
    val useProfileDefaults: Boolean = false,
    val tcpPortForwardRules: List<TcpPortForwardRule> = listOf(TcpPortForwardRule()),
    val groupIds: List<String> = emptyList(), // 所属分组 ID 列表，支持多分组
) {
    init {
        require(connectionCandidates.isNotEmpty()) { "会话必须至少包含一个 connectionCandidate" }
    }

    private fun primaryConnectionCandidate(): ConnectionCandidate =
        toConnectionCandidates().minBy(ConnectionCandidate::priority)

    /**
     * 判断是否为 USB 连接
     */
    fun isUsbConnection(): Boolean = primaryConnectionCandidate().transport == ConnectionTransport.USB

    fun isMdnsConnection(): Boolean = primaryConnectionCandidate().transport == ConnectionTransport.MDNS

    /**
     * 获取 USB 序列号（仅 USB 模式有效）
     */
    fun getUsbSerialNumber(): String? =
        if (isUsbConnection()) {
            DeviceTransportSerial.stripUsbPrefix(primaryConnectionCandidate().host)
        } else {
            null
        }

    /**
     * 清空连接过程中自动探测出的设备身份和编解码器能力。
     *
     * 用户手动选择的编解码器属于配置，不属于探测缓存，必须保留。
     * 清空设备序列号可确保下次连接从设备身份校验开始完整执行探测流程。
     */
    fun clearAutoDetectedCodecState(): SessionData =
        copy(capabilityCache = DeviceCapabilityCache())

    /**
     * 获取设备唯一标识
     * USB/TCP/mDNS 模式都使用带 transport 前缀的 ADB serial
     */
    fun getDeviceIdentifier(): String = primaryConnectionCandidate().deviceIdentifier()

    fun primaryConnectionEndpointForDisplay(): String = primaryConnectionCandidate().toAddressEndpoint()

    fun allConnectionEndpointsForDisplay(): List<String> =
        toConnectionCandidates().sortedBy(ConnectionCandidate::priority).map { it.toAddressEndpoint() }

    fun toConnectionCandidates(): List<ConnectionCandidate> {
        return connectionCandidates.map { it.toDomain() }
    }

    /**
     * 转换为 ScrcpyOptions
     */
    fun toScrcpyOptions(): ScrcpyOptions =
        ScrcpyOptions(
            sessionId = id,
            profileId = profileId.takeIf { useProfileDefaults }.orEmpty(),
            connectionCandidates = toConnectionCandidates(),
            config = config,
            capabilityCache = capabilityCache,
        )

    /**
     * 从 ScrcpyOptions 更新字段
     */
    fun fromScrcpyOptions(options: ScrcpyOptions): SessionData =
        copy(
            profileId = options.profileId,
            useProfileDefaults = options.profileId.isNotBlank(),
            connectionCandidates = options.connectionCandidates.map { it.toData() },
            config = options.config,
            capabilityCache = options.capabilityCache,
        )
}

@Serializable
data class ConnectionCandidateData(
    val transport: String,
    val host: String,
    val port: Int = 0,
    val priority: Int = 0,
    val lastSuccessfulAtMillis: Long = 0L,
    val failureCount: Int = 0,
) {
    fun toDomain(): ConnectionCandidate {
        val parsedTransport = runCatching { ConnectionTransport.valueOf(transport) }.getOrDefault(ConnectionTransport.TCP)
        val normalizedHost =
            if (parsedTransport == ConnectionTransport.MDNS) {
                DeviceTransportSerial.mdnsDeviceSerial(host)
            } else {
                host
            }
        return ConnectionCandidate(
            transport = parsedTransport,
            host = normalizedHost,
            port = port,
            priority = priority,
            lastSuccessfulAtMillis = lastSuccessfulAtMillis,
            failureCount = failureCount,
        )
    }
}

fun ConnectionCandidate.toData(): ConnectionCandidateData =
    ConnectionCandidateData(
        transport = transport.name,
        host = if (transport == ConnectionTransport.MDNS) DeviceTransportSerial.mdnsDeviceSerial(host) else host,
        port = port,
        priority = priority,
        lastSuccessfulAtMillis = lastSuccessfulAtMillis,
        failureCount = failureCount,
    )

internal fun parseBitRate(rawValue: String): Int? {
    val value = rawValue.trim()
    if (value.isEmpty()) return null

    val multiplier =
        when (value.last().lowercaseChar()) {
            'm' -> 1_000_000L
            'k' -> 1_000L
            else -> 1L
        }
    val numericPart =
        if (multiplier == 1L) {
            value
        } else {
            value.dropLast(1)
        }

    val parsed = numericPart.toDoubleOrNull() ?: return null
    val bitsPerSecond = (parsed * multiplier).toLong()
    return bitsPerSecond.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * ScrcpyOptions uses bps integers, while SessionData keeps the text entered by the user.
 * Preserve that text when an unrelated options update round-trips through the integer model.
 */
internal fun preserveBitRateText(
    currentText: String,
    bitsPerSecond: Int,
): String =
    if (parseBitRate(currentText) == bitsPerSecond) {
        currentText
    } else {
        bitsPerSecond.toString()
    }

/** Keep an empty optional limit empty while it round-trips through the integer options model. */
internal fun preserveOptionalLimitText(
    currentText: String,
    value: Int,
): String =
    when {
        currentText.isBlank() && value <= 0 -> currentText
        currentText.toIntOrNull() == value -> currentText
        value > 0 -> value.toString()
        else -> ""
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

    suspend fun removeGroupReferences(groupId: String) {
        context.sessionDataStore.edit { preferences ->
            val currentJson = preferences[Keys.SESSIONS] ?: "[]"
            val currentList =
                try {
                    json.decodeFromString<List<SessionData>>(currentJson)
                } catch (e: Exception) {
                    emptyList()
                }
            val updatedList = removeGroupReferences(currentList, groupId)
            if (updatedList != currentList) {
                preferences[Keys.SESSIONS] = json.encodeToString(updatedList)
            }
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
     * 按会话 ID 原子保存：已有 ID 时更新，不存在时新增。
     *
     * 保存语义不能依赖当前是否打开编辑弹窗，否则列表快捷操作会被误判为新增会话。
     */
    suspend fun upsertSession(sessionData: SessionData) {
        context.sessionDataStore.edit { preferences ->
            val currentJson = preferences[Keys.SESSIONS] ?: "[]"
            val currentList =
                try {
                    json.decodeFromString<List<SessionData>>(currentJson)
                } catch (e: Exception) {
                    emptyList()
                }
            preferences[Keys.SESSIONS] =
                json.encodeToString(upsertSessionById(currentList, sessionData))
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
            hasWifi = toConnectionCandidates().any { it.transport != ConnectionTransport.USB },
            hasWarning = false,
        )
}

/** Replace one logical session and collapse any duplicate rows carrying the same ID. */
internal fun upsertSessionById(
    current: List<SessionData>,
    sessionData: SessionData,
): List<SessionData> =
    buildList(current.size + 1) {
        var replaced = false
        current.forEach { item ->
            if (item.id != sessionData.id) {
                add(item)
            } else if (!replaced) {
                add(sessionData)
                replaced = true
            }
        }
        if (!replaced) add(sessionData)
    }

internal fun removeGroupReferences(
    sessions: List<SessionData>,
    groupId: String,
): List<SessionData> =
    sessions.map { session ->
        val validGroupIds = session.groupIds.filterNot { it == groupId }.distinct()
        if (validGroupIds == session.groupIds) session else session.copy(groupIds = validGroupIds)
    }
