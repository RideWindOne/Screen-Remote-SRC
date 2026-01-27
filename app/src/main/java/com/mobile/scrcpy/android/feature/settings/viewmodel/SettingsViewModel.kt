package com.mobile.scrcpy.android.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mobile.scrcpy.android.core.common.AppConstants
import com.mobile.scrcpy.android.core.data.datastore.PreferencesManager
import com.mobile.scrcpy.android.core.domain.model.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置管理 ViewModel
 * 职责：应用设置读写
 */
class SettingsViewModel(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    // ============ 设置数据 ============

    val settings: StateFlow<AppSettings> = preferencesManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(AppConstants.STATEFLOW_SUBSCRIBE_TIMEOUT_MS),
            initialValue = AppSettings()
        )

    // ============ 设置更新 ============

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            preferencesManager.updateSettings(settings)
        }
    }

    // ============ Factory ============

    companion object {
        fun provideFactory(
            preferencesManager: PreferencesManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(preferencesManager) as T
            }
        }
    }
}
