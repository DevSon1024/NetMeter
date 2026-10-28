package com.devson.netmeter.features.dashboard

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DashboardUiState(
    val isMonitoringActive: Boolean = false,
    val currentDownloadSpeedPlaceholder: String = "0.0 KB/s",
    val currentUploadSpeedPlaceholder: String = "0.0 KB/s",
    val dailyMobileDataPlaceholder: String = "0.00 MB",
    val dailyWifiDataPlaceholder: String = "0.00 MB",
    val statusMessage: String = "Ready to start network meter monitoring"
)

class DashboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun toggleMonitoring() {
        val currentState = _uiState.value
        val newState = !currentState.isMonitoringActive
        _uiState.value = currentState.copy(
            isMonitoringActive = newState,
            statusMessage = if (newState) "Monitoring service simulation active" else "Monitoring paused"
        )
    }
}
