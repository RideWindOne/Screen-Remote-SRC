package com.mobile.scrcpy.android.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobile.scrcpy.android.core.common.FilePathConstants.DEFAULT_FILE_TRANSFER_PATH
import com.mobile.scrcpy.android.core.domain.model.AppLanguage
import com.mobile.scrcpy.android.core.domain.model.AppSettings
import com.mobile.scrcpy.android.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val KEEP_ALIVE_MINUTES = intPreferencesKey("keep_alive_minutes")
        val SHOW_ON_LOCK_SCREEN = booleanPreferencesKey("show_on_lock_screen")
        val ENABLE_ACTIVITY_LOG = booleanPreferencesKey("enable_activity_log")
        val FILE_TRANSFER_PATH = stringPreferencesKey("file_transfer_path")
        val ENABLE_FLOATING_HAPTIC_FEEDBACK = booleanPreferencesKey("enable_floating_haptic_feedback")
    }
    
    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            themeMode = preferences[Keys.THEME_MODE]?.let { 
                try {
                    ThemeMode.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    ThemeMode.SYSTEM
                }
            } ?: ThemeMode.SYSTEM,
            language = preferences[Keys.LANGUAGE]?.let {
                try {
                    AppLanguage.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    AppLanguage.AUTO
                }
            } ?: AppLanguage.AUTO,
            keepAliveMinutes = preferences[Keys.KEEP_ALIVE_MINUTES] ?: 5,
            showOnLockScreen = preferences[Keys.SHOW_ON_LOCK_SCREEN] ?: false,
            enableActivityLog = preferences[Keys.ENABLE_ACTIVITY_LOG] ?: true,
            fileTransferPath = preferences[Keys.FILE_TRANSFER_PATH] ?: DEFAULT_FILE_TRANSFER_PATH,
            enableFloatingHapticFeedback = preferences[Keys.ENABLE_FLOATING_HAPTIC_FEEDBACK] ?: true
        )
    }
    
    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = settings.themeMode.name
            preferences[Keys.LANGUAGE] = settings.language.name
            preferences[Keys.KEEP_ALIVE_MINUTES] = settings.keepAliveMinutes
            preferences[Keys.SHOW_ON_LOCK_SCREEN] = settings.showOnLockScreen
            preferences[Keys.ENABLE_ACTIVITY_LOG] = settings.enableActivityLog
            preferences[Keys.FILE_TRANSFER_PATH] = settings.fileTransferPath
            preferences[Keys.ENABLE_FLOATING_HAPTIC_FEEDBACK] = settings.enableFloatingHapticFeedback
        }
    }
}
