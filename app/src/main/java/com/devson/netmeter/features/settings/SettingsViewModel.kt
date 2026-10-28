package com.devson.netmeter.features.settings

import androidx.lifecycle.ViewModel
import com.devson.netmeter.core.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val selectedThemeMode: ThemeMode = ThemeMode.SYSTEM,
    val appVersion: String = "1.0.0",
    val applicationId: String = "com.devson.netmeter",
    val minSdk: Int = 26,
    val targetSdk: Int = 37
)

class SettingsViewModel : ViewModel() {
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        _uiState.value = _uiState.value.copy(selectedThemeMode = mode)
    }
}
