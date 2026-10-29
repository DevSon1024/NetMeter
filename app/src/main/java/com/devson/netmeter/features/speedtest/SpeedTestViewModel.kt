package com.devson.netmeter.features.speedtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.netmeter.core.data.speedtest.HttpUrlConnectionSpeedTestEngine
import com.devson.netmeter.core.data.speedtest.SpeedTestEngine
import com.devson.netmeter.core.domain.model.LatencyMeasurement
import com.devson.netmeter.core.domain.model.SpeedTestConfig
import com.devson.netmeter.core.domain.model.SpeedTestErrorType
import com.devson.netmeter.core.domain.model.SpeedTestEvent
import com.devson.netmeter.core.domain.model.SpeedTestFinalReport
import com.devson.netmeter.core.domain.model.SpeedTestPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpeedTestUiState(
    val isTesting: Boolean = false,
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val progressFraction: Float = 0f,
    val currentSpeedMbps: Double = 0.0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val httpLatencyMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val latencySamples: List<Double> = emptyList(),
    val totalBytesConsumed: Long = 0L,
    val statusMessage: String = "Ready to test connection speed",
    val serverHost: String = SpeedTestConfig.DEFAULT.hostDisplayName,
    val errorMessage: String? = null,
    val completedReport: SpeedTestFinalReport? = null
)

class SpeedTestViewModel(
    private val speedTestEngine: SpeedTestEngine = HttpUrlConnectionSpeedTestEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeedTestUiState())
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()

    private var activeTestJob: Job? = null

    fun startSpeedTest(config: SpeedTestConfig = SpeedTestConfig.DEFAULT) {
        if (_uiState.value.isTesting) return

        activeTestJob?.cancel()
        _uiState.update {
            SpeedTestUiState(
                isTesting = true,
                phase = SpeedTestPhase.LATENCY,
                progressFraction = 0.02f,
                statusMessage = "Connecting to ${config.hostDisplayName}...",
                serverHost = config.hostDisplayName
            )
        }

        activeTestJob = viewModelScope.launch {
            speedTestEngine.executeSpeedTest(config).collect { event ->
                when (event) {
                    is SpeedTestEvent.ProgressUpdate -> {
                        _uiState.update { state ->
                            state.copy(
                                isTesting = true,
                                phase = event.progress.phase,
                                progressFraction = event.progress.progressFraction,
                                currentSpeedMbps = event.progress.currentMbps,
                                downloadMbps = if (event.progress.phase == SpeedTestPhase.DOWNLOAD) {
                                    event.progress.currentMbps
                                } else {
                                    event.progress.downloadSpeedMbps
                                },
                                uploadMbps = if (event.progress.phase == SpeedTestPhase.UPLOAD) {
                                    event.progress.currentMbps
                                } else {
                                    event.progress.uploadSpeedMbps
                                },
                                httpLatencyMs = if (event.progress.phase == SpeedTestPhase.LATENCY && event.progress.currentLatencyMs > 0.0) {
                                    event.progress.currentLatencyMs
                                } else {
                                    event.progress.averageLatencyMs
                                },
                                jitterMs = event.progress.jitterMs,
                                totalBytesConsumed = event.progress.totalBytesConsumed,
                                statusMessage = event.progress.statusText,
                                errorMessage = null
                            )
                        }
                    }

                    is SpeedTestEvent.Completed -> {
                        _uiState.update { state ->
                            state.copy(
                                isTesting = false,
                                phase = SpeedTestPhase.COMPLETED,
                                progressFraction = 1f,
                                currentSpeedMbps = event.report.downloadMbps,
                                downloadMbps = event.report.downloadMbps,
                                uploadMbps = event.report.uploadMbps,
                                httpLatencyMs = event.report.latency.averageMs,
                                jitterMs = event.report.latency.jitterMs,
                                latencySamples = event.report.latency.samplesMs,
                                totalBytesConsumed = event.report.totalBytesConsumed,
                                statusMessage = "Speed test completed successfully",
                                errorMessage = null,
                                completedReport = event.report
                            )
                        }
                    }

                    is SpeedTestEvent.Failed -> {
                        _uiState.update { state ->
                            state.copy(
                                isTesting = false,
                                phase = SpeedTestPhase.ERROR,
                                statusMessage = "Speed test failed",
                                errorMessage = event.errorType.userMessage
                            )
                        }
                    }

                    SpeedTestEvent.Cancelled -> {
                        _uiState.update { state ->
                            state.copy(
                                isTesting = false,
                                phase = SpeedTestPhase.CANCELLED,
                                statusMessage = "Speed test cancelled by user"
                            )
                        }
                    }
                }
            }
        }
    }

    fun cancelSpeedTest() {
        if (_uiState.value.isTesting) {
            activeTestJob?.cancel()
            _uiState.update { state ->
                state.copy(
                    isTesting = false,
                    phase = SpeedTestPhase.CANCELLED,
                    statusMessage = "Speed test cancelled"
                )
            }
        }
    }

    fun resetState() {
        if (!_uiState.value.isTesting) {
            _uiState.value = SpeedTestUiState()
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeTestJob?.cancel()
    }
}
