package com.screen.remote.android.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screen.remote.android.core.domain.model.AppLanguage
import com.screen.remote.android.core.domain.model.AppSettings
import com.screen.remote.android.core.domain.model.CustomShellCommand
import com.screen.remote.android.core.domain.model.ThemeMode
import com.screen.remote.android.core.update.UpdateChannel
import com.screen.remote.android.core.update.GitHubReleaseInfo
import com.screen.remote.android.core.update.UpdateCheckCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(
    private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val ENABLE_ACTIVITY_LOG = booleanPreferencesKey("enable_activity_log")
        val ENABLE_AUDIO_STREAM_LOG = booleanPreferencesKey("enable_audio_stream_log")
        val ENABLE_VIDEO_STREAM_LOG = booleanPreferencesKey("enable_video_stream_log")
        val ENABLE_CONTROL_STREAM_LOG = booleanPreferencesKey("enable_control_stream_log")
        val ENABLE_EVENT_STREAM_LOG = booleanPreferencesKey("enable_event_stream_log")
        val ENABLE_SHELL_STREAM_LOG = booleanPreferencesKey("enable_shell_stream_log")
        val ENABLE_MANAGEMENT_LOG = booleanPreferencesKey("enable_management_log")
        val ENABLE_DEBUG_MODE = booleanPreferencesKey("enable_debug_mode")
        val ENABLE_FLOATING_HAPTIC_FEEDBACK = booleanPreferencesKey("enable_floating_haptic_feedback")
        val SHOW_PERFORMANCE_STATS = booleanPreferencesKey("show_performance_stats")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val LAST_SEEN_ONBOARDING_VERSION = stringPreferencesKey("last_seen_onboarding_version")
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
        val LAST_UPDATE_VERSION = stringPreferencesKey("last_update_version")
        val LAST_UPDATE_RELEASE_URL = stringPreferencesKey("last_update_release_url")
        val SKIPPED_UPDATE_VERSION = stringPreferencesKey("skipped_update_version")
        val CUSTOM_SHELL_COMMANDS = stringPreferencesKey("custom_shell_commands")
        val REPLACE_DEFAULT_SHELL_COMMANDS = booleanPreferencesKey("replace_default_shell_commands")
    }

    val lastSeenOnboardingVersionFlow: Flow<String?> =
        context.dataStore.data.map {
            preferences -> preferences[Keys.LAST_SEEN_ONBOARDING_VERSION]
//            "v4.4.2" // 修改这里临时预览更新页面
        }

    suspend fun markOnboardingVersionSeen(version: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_SEEN_ONBOARDING_VERSION] = version
        }
    }

    val updateCheckCacheFlow: Flow<UpdateCheckCache> =
        context.dataStore.data.map { preferences ->
            UpdateCheckCache(
                checkedAtEpochMillis = preferences[Keys.LAST_UPDATE_CHECK_AT] ?: 0,
                latestVersion = preferences[Keys.LAST_UPDATE_VERSION],
                releaseUrl = preferences[Keys.LAST_UPDATE_RELEASE_URL],
                skippedVersion = preferences[Keys.SKIPPED_UPDATE_VERSION],
            )
        }

    suspend fun recordUpdateCheck(
        checkedAtEpochMillis: Long,
        release: GitHubReleaseInfo?,
    ) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_UPDATE_CHECK_AT] = checkedAtEpochMillis
            if (release == null) {
                preferences.remove(Keys.LAST_UPDATE_VERSION)
                preferences.remove(Keys.LAST_UPDATE_RELEASE_URL)
            } else {
                preferences[Keys.LAST_UPDATE_VERSION] = release.tagName
                preferences[Keys.LAST_UPDATE_RELEASE_URL] = release.htmlUrl
            }
        }
    }

    suspend fun markUpdateVersionSkipped(version: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SKIPPED_UPDATE_VERSION] = version
        }
    }

    val settingsFlow: Flow<AppSettings> =
        context.dataStore.data.map { preferences ->
            AppSettings(
                themeMode =
                    preferences[Keys.THEME_MODE]?.let {
                        try {
                            ThemeMode.valueOf(it)
                        } catch (_: IllegalArgumentException) {
                            ThemeMode.SYSTEM
                        }
                    } ?: ThemeMode.SYSTEM,
                language =
                    preferences[Keys.LANGUAGE]?.let {
                        try {
                            AppLanguage.valueOf(it)
                        } catch (_: IllegalArgumentException) {
                            AppLanguage.AUTO
                        }
                    } ?: AppLanguage.AUTO,
                enableActivityLog = preferences[Keys.ENABLE_ACTIVITY_LOG] ?: true,
                enableAudioStreamLog = preferences[Keys.ENABLE_AUDIO_STREAM_LOG] ?: false,
                enableVideoStreamLog = preferences[Keys.ENABLE_VIDEO_STREAM_LOG] ?: false,
                enableControlStreamLog = preferences[Keys.ENABLE_CONTROL_STREAM_LOG] ?: false,
                enableEventStreamLog = preferences[Keys.ENABLE_EVENT_STREAM_LOG] ?: false,
                enableShellStreamLog = preferences[Keys.ENABLE_SHELL_STREAM_LOG] ?: false,
                enableManagementLog = preferences[Keys.ENABLE_MANAGEMENT_LOG] ?: false,
                enableDebugMode = preferences[Keys.ENABLE_DEBUG_MODE] ?: false,
                enableFloatingHapticFeedback = preferences[Keys.ENABLE_FLOATING_HAPTIC_FEEDBACK] ?: true,
                showPerformanceStats = preferences[Keys.SHOW_PERFORMANCE_STATS] ?: false,
                autoCheckUpdates = preferences[Keys.AUTO_CHECK_UPDATES] ?: true,
                updateChannel =
                    preferences[Keys.UPDATE_CHANNEL]?.let {
                        runCatching { UpdateChannel.valueOf(it) }.getOrDefault(UpdateChannel.STABLE)
                    } ?: UpdateChannel.STABLE,
                customShellCommands =
                    preferences[Keys.CUSTOM_SHELL_COMMANDS]
                        ?.let { encoded -> runCatching { Json.decodeFromString<List<CustomShellCommand>>(encoded) }.getOrNull() }
                        .orEmpty(),
                replaceDefaultShellCommands = preferences[Keys.REPLACE_DEFAULT_SHELL_COMMANDS] ?: false,
            )
        }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = settings.themeMode.name
            preferences[Keys.LANGUAGE] = settings.language.name
            preferences[Keys.ENABLE_ACTIVITY_LOG] = settings.enableActivityLog
            preferences[Keys.ENABLE_AUDIO_STREAM_LOG] = settings.enableAudioStreamLog
            preferences[Keys.ENABLE_VIDEO_STREAM_LOG] = settings.enableVideoStreamLog
            preferences[Keys.ENABLE_CONTROL_STREAM_LOG] = settings.enableControlStreamLog
            preferences[Keys.ENABLE_EVENT_STREAM_LOG] = settings.enableEventStreamLog
            preferences[Keys.ENABLE_SHELL_STREAM_LOG] = settings.enableShellStreamLog
            preferences[Keys.ENABLE_MANAGEMENT_LOG] = settings.enableManagementLog
            preferences[Keys.ENABLE_DEBUG_MODE] = settings.enableDebugMode
            preferences[Keys.ENABLE_FLOATING_HAPTIC_FEEDBACK] = settings.enableFloatingHapticFeedback
            preferences[Keys.SHOW_PERFORMANCE_STATS] = settings.showPerformanceStats
            preferences[Keys.AUTO_CHECK_UPDATES] = settings.autoCheckUpdates
            preferences[Keys.UPDATE_CHANNEL] = settings.updateChannel.name
            preferences[Keys.CUSTOM_SHELL_COMMANDS] = Json.encodeToString(settings.customShellCommands)
            preferences[Keys.REPLACE_DEFAULT_SHELL_COMMANDS] = settings.replaceDefaultShellCommands
        }
    }
}
