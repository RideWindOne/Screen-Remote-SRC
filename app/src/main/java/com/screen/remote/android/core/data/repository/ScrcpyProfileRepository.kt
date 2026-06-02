package com.screen.remote.android.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screen.remote.android.core.common.ScrcpyConstants
import com.screen.remote.android.core.domain.model.ScrcpyProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.scrcpyProfileDataStore: DataStore<Preferences> by preferencesDataStore(name = "scrcpy_profiles")

@Serializable
data class ScrcpyProfileData(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val maxSize: Int = 1920,
    val videoBitRate: Int = ScrcpyConstants.DEFAULT_VIDEO_BITRATE_INT,
    val maxFps: Int = 60,
    val displayId: Int = 0,
    val newDisplayEnabled: Boolean = false,
    val newDisplay: String = "",
    val showTouches: Boolean = false,
    val enableClipboardSync: Boolean = true,
    val stayAwake: Boolean = false,
    val codecOptions: String = ScrcpyConstants.DEFAULT_CODEC_OPTIONS,
    val powerOffOnClose: Boolean = false,
    val cleanupOnDisconnect: Boolean = true,
    val ignoreVideoEncoderConstraints: Boolean = false,
    val enableAudio: Boolean = false,
    val audioBitRate: Int = 128000,
    val turnScreenOff: Boolean = true,
    val keepDeviceAwake: Boolean = false,
    val enableHardwareDecoding: Boolean = true,
    val followRemoteOrientation: Boolean = false,
    val preferredVideoCodec: String = ScrcpyConstants.DEFAULT_VIDEO_CODEC,
    val preferredAudioCodec: String = ScrcpyConstants.DEFAULT_AUDIO_CODEC,
    val userVideoEncoder: String = "",
    val userAudioEncoder: String = "",
    val userVideoDecoder: String = "",
    val userAudioDecoder: String = "",
) {
    fun toDomain(): ScrcpyProfile =
        ScrcpyProfile(
            id = id,
            name = name,
            sortOrder = sortOrder,
            maxSize = maxSize,
            videoBitRate = videoBitRate,
            maxFps = maxFps,
            displayId = displayId,
            newDisplayEnabled = newDisplayEnabled,
            newDisplay = newDisplay,
            showTouches = showTouches,
            enableClipboardSync = enableClipboardSync,
            stayAwake = stayAwake,
            codecOptions = codecOptions,
            powerOffOnClose = powerOffOnClose,
            cleanupOnDisconnect = cleanupOnDisconnect,
            ignoreVideoEncoderConstraints = ignoreVideoEncoderConstraints,
            enableAudio = enableAudio,
            audioBitRate = audioBitRate,
            turnScreenOff = turnScreenOff,
            keepDeviceAwake = keepDeviceAwake,
            enableHardwareDecoding = enableHardwareDecoding,
            followRemoteOrientation = followRemoteOrientation,
            preferredVideoCodec = preferredVideoCodec,
            preferredAudioCodec = preferredAudioCodec,
            userVideoEncoder = userVideoEncoder,
            userAudioEncoder = userAudioEncoder,
            userVideoDecoder = userVideoDecoder,
            userAudioDecoder = userAudioDecoder,
        )
}

fun ScrcpyProfile.toData(): ScrcpyProfileData =
    ScrcpyProfileData(
        id = id,
        name = name,
        sortOrder = sortOrder,
        maxSize = maxSize,
        videoBitRate = videoBitRate,
        maxFps = maxFps,
        displayId = displayId,
        newDisplayEnabled = newDisplayEnabled,
        newDisplay = newDisplay,
        showTouches = showTouches,
        enableClipboardSync = enableClipboardSync,
        stayAwake = stayAwake,
        codecOptions = codecOptions,
        powerOffOnClose = powerOffOnClose,
        cleanupOnDisconnect = cleanupOnDisconnect,
        ignoreVideoEncoderConstraints = ignoreVideoEncoderConstraints,
        enableAudio = enableAudio,
        audioBitRate = audioBitRate,
        turnScreenOff = turnScreenOff,
        keepDeviceAwake = keepDeviceAwake,
        enableHardwareDecoding = enableHardwareDecoding,
        followRemoteOrientation = followRemoteOrientation,
        preferredVideoCodec = preferredVideoCodec,
        preferredAudioCodec = preferredAudioCodec,
        userVideoEncoder = userVideoEncoder,
        userAudioEncoder = userAudioEncoder,
        userVideoDecoder = userVideoDecoder,
        userAudioDecoder = userAudioDecoder,
    )

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
            preferences[Keys.PROFILES] = json.encodeToString(updated.map { it.toData() })
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
            preferences[Keys.PROFILES] = json.encodeToString(updated.map { it.toData() })
        }
    }

    suspend fun deleteProfile(id: String) {
        if (id == ScrcpyProfile.DEFAULT_ID) return
        context.scrcpyProfileDataStore.edit { preferences ->
            val updated = decodeProfiles(preferences[Keys.PROFILES]).filterNot { it.id == id }.ensureDefaultProfile()
            preferences[Keys.PROFILES] = json.encodeToString(updated.map { it.toData() })
        }
    }

    private fun decodeProfiles(raw: String?): List<ScrcpyProfile> =
        runCatching {
            json.decodeFromString<List<ScrcpyProfileData>>(raw ?: "[]").map { it.toDomain() }
        }.getOrDefault(emptyList())
            .ensureDefaultProfile()
            .sortedWith(compareBy<ScrcpyProfile> { it.sortOrder }.thenBy { it.name })

    private fun List<ScrcpyProfile>.ensureDefaultProfile(): List<ScrcpyProfile> =
        if (any { it.id == ScrcpyProfile.DEFAULT_ID }) this else listOf(ScrcpyProfile.default()) + this
}
