package com.devson.netmeter.features.apkanalyzer.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.netmeter.features.apkanalyzer.data.export.ApkReportExporter
import com.devson.netmeter.features.apkanalyzer.data.repository.ApkAnalyzerRepositoryImpl
import com.devson.netmeter.features.apkanalyzer.data.repository.SqliteApkHistoryRepository
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkComponentType
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkHistoryItem
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkPermissionCategory
import com.devson.netmeter.features.apkanalyzer.domain.model.SecuritySeverity
import com.devson.netmeter.features.apkanalyzer.domain.repository.ApkAnalysisProgress
import com.devson.netmeter.features.apkanalyzer.domain.repository.ApkAnalyzerRepository
import com.devson.netmeter.features.apkanalyzer.domain.repository.ApkHistoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ApkAnalyzerViewModel @JvmOverloads constructor(
    application: Application,
    private val analyzerRepository: ApkAnalyzerRepository = ApkAnalyzerRepositoryImpl(application),
    private val historyRepository: ApkHistoryRepository = SqliteApkHistoryRepository(application),
    private val reportExporter: ApkReportExporter = ApkReportExporter()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ApkAnalyzerUiState())
    val uiState: StateFlow<ApkAnalyzerUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    init {
        viewModelScope.launch {
            historyRepository.getHistoryFlow().collect { historyList ->
                _uiState.update { it.copy(historyItems = historyList) }
            }
        }
    }

    fun onApkSelected(uri: Uri) {
        val context = getApplication<Application>()
        var fileName = "selected.apk"
        var fileSize = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {
            // Fallback for custom content providers
        }

        analyzeApk(uri, fileName, fileSize)
    }

    fun analyzeApk(uri: Uri, fileName: String, fileSizeBytes: Long) {
        analysisJob?.cancel()

        _uiState.update {
            it.copy(
                isLoading = true,
                loadingStep = "Initializing analysis engine...",
                loadingProgress = 0.05f,
                errorMessage = null,
                selectedTab = ApkDetailTab.OVERVIEW
            )
        }

        analysisJob = viewModelScope.launch {
            analyzerRepository.analyzeApkFlow(uri, fileName, fileSizeBytes).collect { progress ->
                when (progress) {
                    is ApkAnalysisProgress.Progress -> {
                        _uiState.update {
                            it.copy(
                                loadingStep = progress.step,
                                loadingProgress = progress.percentage
                            )
                        }
                    }
                    is ApkAnalysisProgress.Completed -> {
                        val result = progress.result
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                analysisResult = result,
                                errorMessage = null
                            )
                        }

                        // Persist analysis summary to local history
                        try {
                            val historyItem = ApkHistoryItem(
                                fileName = result.metadata.fileName,
                                packageName = result.metadata.packageName,
                                appName = result.metadata.appName,
                                versionName = result.metadata.versionName,
                                versionCode = result.metadata.versionCode,
                                fileSizeBytes = result.metadata.fileSizeBytes,
                                analyzedAt = result.analyzedAt,
                                totalPermissions = result.permissionSummary.totalCount,
                                dangerousPermissions = result.permissionSummary.dangerousCount,
                                exportedComponents = result.componentsSummary.exportedCount,
                                securityWarnings = result.security.warningCount,
                                highRiskFindings = result.security.highRiskCount
                            )
                            historyRepository.saveHistory(historyItem)
                        } catch (_: Exception) {
                            // Non-fatal history persistence failure
                        }
                    }
                    is ApkAnalysisProgress.Failed -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = progress.error.localizedMessage ?: "Failed to analyze APK package"
                            )
                        }
                    }
                }
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = false,
                loadingStep = "",
                loadingProgress = 0f,
                errorMessage = "Analysis cancelled by user"
            )
        }
    }

    fun clearCurrentAnalysis() {
        analysisJob?.cancel()
        _uiState.update {
            it.copy(
                analysisResult = null,
                errorMessage = null,
                selectedTab = ApkDetailTab.OVERVIEW
            )
        }
    }

    fun selectTab(tab: ApkDetailTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setPermissionSearchQuery(query: String) {
        _uiState.update { it.copy(permissionSearchQuery = query) }
    }

    fun setPermissionCategory(category: ApkPermissionCategory?) {
        _uiState.update { it.copy(selectedPermissionCategory = category) }
    }

    fun toggleDangerousPermissionsOnly() {
        _uiState.update { it.copy(showOnlyDangerousPermissions = !it.showOnlyDangerousPermissions) }
    }

    fun setComponentSearchQuery(query: String) {
        _uiState.update { it.copy(componentSearchQuery = query) }
    }

    fun setComponentType(type: ApkComponentType?) {
        _uiState.update { it.copy(selectedComponentType = type) }
    }

    fun toggleExportedComponentsOnly() {
        _uiState.update { it.copy(showOnlyExportedComponents = !it.showOnlyExportedComponents) }
    }

    fun setSecuritySeverity(severity: SecuritySeverity?) {
        _uiState.update { it.copy(selectedSecuritySeverity = severity) }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteHistoryItem(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }

    fun exportReport(destinationUri: Uri) {
        val result = _uiState.value.analysisResult ?: return
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    reportExporter.exportToJson(result, output)
                }
                _uiState.update { it.copy(exportStatusMessage = "Report exported successfully") }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportStatusMessage = "Failed to export report: ${e.localizedMessage}") }
            }
        }
    }

    fun clearExportStatus() {
        _uiState.update { it.copy(exportStatusMessage = null) }
    }
}
