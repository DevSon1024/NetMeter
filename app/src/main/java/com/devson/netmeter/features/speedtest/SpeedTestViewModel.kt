package com.devson.netmeter.features.speedtest

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.netmeter.core.data.speedtest.HttpUrlConnectionSpeedTestEngine
import com.devson.netmeter.core.data.speedtest.SpeedTestEngine
import com.devson.netmeter.core.data.speedtest.SpeedTestHistoryRepository
import com.devson.netmeter.core.data.speedtest.SqliteSpeedTestHistoryRepository
import com.devson.netmeter.core.domain.model.SpeedTestConfig
import com.devson.netmeter.core.domain.model.SpeedTestEvent
import com.devson.netmeter.core.domain.model.SpeedTestFinalReport
import com.devson.netmeter.core.domain.model.SpeedTestPhase
import com.devson.netmeter.core.domain.model.SpeedTestRecord
import com.devson.netmeter.core.util.AndroidConnectivityObserver
import com.devson.netmeter.core.util.ConnectivityObserver
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
    val currentNetworkType: String = "Online",
    val errorMessage: String? = null,
    val completedReport: SpeedTestFinalReport? = null,
    val historyRecords: List<SpeedTestRecord> = emptyList()
)

class SpeedTestViewModel @JvmOverloads constructor(
    application: Application,
    private val speedTestEngine: SpeedTestEngine = HttpUrlConnectionSpeedTestEngine(),
    private val historyRepository: SpeedTestHistoryRepository = SqliteSpeedTestHistoryRepository(application.applicationContext),
    private val connectivityObserver: ConnectivityObserver = AndroidConnectivityObserver(application.applicationContext)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        SpeedTestUiState(
            currentNetworkType = connectivityObserver.getCurrentState().type.displayName
        )
    )
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()

    private var activeTestJob: Job? = null

    init {
        observeHistory()
        observeConnectivity()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            historyRepository.getHistoryFlow().collect { records ->
                _uiState.update { it.copy(historyRecords = records) }
            }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.networkState.collect { connectionState ->
                _uiState.update { it.copy(currentNetworkType = connectionState.type.displayName) }
            }
        }
    }

    fun startSpeedTest(config: SpeedTestConfig = SpeedTestConfig.DEFAULT) {
        // Prevent duplicate concurrent tests
        if (_uiState.value.isTesting) return

        activeTestJob?.cancel()
        val networkType = connectivityObserver.getCurrentState().type.displayName

        _uiState.update {
            it.copy(
                isTesting = true,
                phase = SpeedTestPhase.PREPARING,
                progressFraction = 0.02f,
                currentSpeedMbps = 0.0,
                downloadMbps = 0.0,
                uploadMbps = 0.0,
                httpLatencyMs = 0.0,
                jitterMs = 0.0,
                totalBytesConsumed = 0L,
                statusMessage = "Preparing test sockets and connecting to ${config.hostDisplayName}...",
                serverHost = config.hostDisplayName,
                currentNetworkType = networkType,
                errorMessage = null,
                completedReport = null
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
                                downloadMbps = if (event.progress.phase == SpeedTestPhase.DOWNLOADING) {
                                    event.progress.currentMbps
                                } else {
                                    event.progress.downloadSpeedMbps
                                },
                                uploadMbps = if (event.progress.phase == SpeedTestPhase.UPLOADING) {
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

                        // Persist only genuine completed results to SQLite history
                        val historyRecord = SpeedTestRecord(
                            timestampMillis = event.report.timestampMillis,
                            networkType = networkType,
                            latencyMs = event.report.latency.averageMs,
                            jitterMs = event.report.latency.jitterMs,
                            downloadMbps = event.report.downloadMbps,
                            uploadMbps = event.report.uploadMbps,
                            totalBytesConsumed = event.report.totalBytesConsumed,
                            serverHost = event.report.endpointHost
                        )
                        historyRepository.saveTestResult(historyRecord)
                    }

                    is SpeedTestEvent.Failed -> {
                        _uiState.update { state ->
                            state.copy(
                                isTesting = false,
                                phase = SpeedTestPhase.FAILED,
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
                    statusMessage = "Speed test cancelled by user"
                )
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }

    fun resetState() {
        if (!_uiState.value.isTesting) {
            _uiState.update {
                it.copy(
                    phase = SpeedTestPhase.IDLE,
                    progressFraction = 0f,
                    currentSpeedMbps = 0.0,
                    downloadMbps = 0.0,
                    uploadMbps = 0.0,
                    httpLatencyMs = 0.0,
                    jitterMs = 0.0,
                    statusMessage = "Ready to test connection speed",
                    errorMessage = null,
                    completedReport = null
                )
            }
        }
    }

    override fun onCleared() {
        // Prevent orphaned network sockets when ViewModel is cleared
        activeTestJob?.cancel()
    }
}
