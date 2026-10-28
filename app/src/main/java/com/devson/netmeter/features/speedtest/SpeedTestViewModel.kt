package com.devson.netmeter.features.speedtest

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpeedTestUiState(
    val isTesting: Boolean = false,
    val pingMsPlaceholder: String = "--",
    val downloadSpeedPlaceholder: String = "0.0",
    val uploadSpeedPlaceholder: String = "0.0",
    val statusText: String = "Tap start to test connection speed"
)

class SpeedTestViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SpeedTestUiState())
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()

    fun runTestSimulation() {
        if (_uiState.value.isTesting) {
            _uiState.value = _uiState.value.copy(
                isTesting = false,
                statusText = "Speed test cancelled"
            )
        } else {
            _uiState.value = _uiState.value.copy(
                isTesting = true,
                pingMsPlaceholder = "18 ms",
                downloadSpeedPlaceholder = "45.2",
                uploadSpeedPlaceholder = "12.8",
                statusText = "Speed test completed (Simulation)"
            )
        }
    }
}
