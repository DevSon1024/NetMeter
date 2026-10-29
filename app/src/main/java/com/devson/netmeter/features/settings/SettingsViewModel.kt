package com.devson.netmeter.features.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.netmeter.core.data.preferences.DataStoreUserSettingsRepository
import com.devson.netmeter.core.data.preferences.UserSettings
import com.devson.netmeter.core.data.preferences.UserSettingsRepository
import com.devson.netmeter.core.data.preferences.settingsDataStore
import com.devson.netmeter.core.data.repository.AndroidNetworkStatsRepository
import com.devson.netmeter.core.data.snapshot.HistorySnapshotStore
import com.devson.netmeter.core.data.snapshot.SqliteHistorySnapshotStore
import com.devson.netmeter.core.data.speedtest.SpeedTestHistoryRepository
import com.devson.netmeter.core.data.speedtest.SqliteSpeedTestHistoryRepository
import com.devson.netmeter.core.domain.model.DailyDataStatus
import com.devson.netmeter.core.domain.model.DailyUsageRecord
import com.devson.netmeter.core.domain.model.HistoryDataSource
import com.devson.netmeter.core.domain.model.HistoryPeriod
import com.devson.netmeter.core.domain.model.NetworkType
import com.devson.netmeter.core.domain.model.SpeedTestRecord
import com.devson.netmeter.core.domain.model.UsageQueryResult
import com.devson.netmeter.core.domain.repository.NetworkStatsRepository
import com.devson.netmeter.core.theme.ThemeMode
import com.devson.netmeter.core.util.CsvExportHelper
import com.devson.netmeter.core.util.HistoryDateHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

data class SettingsUiState(
    val selectedThemeMode: ThemeMode = ThemeMode.SYSTEM,
    val quotaEnabled: Boolean = false,
    val quotaInputMb: String = "",
    val quotaInputError: String? = null,
    val warningThresholdPercent: Int = 80,
    val savedQuotaBytes: Long = 0L,
    val savedThresholdPercent: Int = 80,
    val currentMonthMobileUsageBytes: Long = 0L,
    val isQuotaWarningTriggered: Boolean = false,
    val isSaving: Boolean = false,
    val snackbarMessage: String? = null,
    val appVersion: String = "1.0.0",
    val applicationId: String = "com.devson.netmeter",
    val minSdk: Int = 26,
    val targetSdk: Int = 37
)

class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val userSettingsRepository: UserSettingsRepository = DataStoreUserSettingsRepository(application.applicationContext.settingsDataStore),
    private val historySnapshotStore: HistorySnapshotStore = SqliteHistorySnapshotStore(application.applicationContext),
    private val speedTestHistoryRepository: SpeedTestHistoryRepository = SqliteSpeedTestHistoryRepository(application.applicationContext),
    private val networkStatsRepository: NetworkStatsRepository = AndroidNetworkStatsRepository(application.applicationContext),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    val themeMode: StateFlow<ThemeMode> = userSettingsRepository.settingsFlow
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        refreshCurrentMonthUsage()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            userSettingsRepository.settingsFlow.collect { settings ->
                _uiState.update { current ->
                    val quotaMbString = if (settings.mobileQuotaBytes > 0L) {
                        (settings.mobileQuotaBytes / (1024L * 1024L)).toString()
                    } else {
                        current.quotaInputMb.ifBlank { "5000" }
                    }

                    val warningTriggered = settings.isThresholdTriggered(current.currentMonthMobileUsageBytes)

                    current.copy(
                        selectedThemeMode = settings.themeMode,
                        quotaEnabled = settings.quotaEnabled,
                        quotaInputMb = quotaMbString,
                        warningThresholdPercent = settings.warningThresholdPercent,
                        savedQuotaBytes = settings.mobileQuotaBytes,
                        savedThresholdPercent = settings.warningThresholdPercent,
                        isQuotaWarningTriggered = warningTriggered
                    )
                }
            }
        }
    }

    fun refreshCurrentMonthUsage() {
        viewModelScope.launch(ioDispatcher) {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfMonth = calendar.timeInMillis
            val now = System.currentTimeMillis()

            val result = networkStatsRepository.getDeviceUsage(NetworkType.MOBILE, startOfMonth, now)
            val mobileBytes = (result as? UsageQueryResult.Success)?.data?.totalBytes ?: 0L

            _uiState.update { current ->
                val warning = current.quotaEnabled &&
                    current.savedQuotaBytes > 0L &&
                    mobileBytes >= (current.savedQuotaBytes * current.savedThresholdPercent) / 100L

                current.copy(
                    currentMonthMobileUsageBytes = mobileBytes,
                    isQuotaWarningTriggered = warning
                )
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch(ioDispatcher) {
            userSettingsRepository.setThemeMode(mode)
        }
    }

    fun setQuotaEnabled(enabled: Boolean) {
        _uiState.update { it.copy(quotaEnabled = enabled) }
        saveQuotaSettings()
    }

    fun setQuotaInput(input: String) {
        val trimmed = input.trim()
        val error = validateQuotaInput(trimmed)
        _uiState.update { it.copy(quotaInputMb = trimmed, quotaInputError = error) }
    }

    fun setWarningThreshold(percent: Int) {
        val clamped = percent.coerceIn(1, 100)
        _uiState.update { it.copy(warningThresholdPercent = clamped) }
    }

    private fun validateQuotaInput(input: String): String? {
        if (input.isBlank()) {
            return "Quota limit cannot be empty"
        }
        val value = input.toLongOrNull()
        return when {
            value == null -> "Please enter a valid numeric value"
            value <= 0L -> "Quota must be greater than 0 MB"
            value > 10_000_000L -> "Quota exceeds maximum limit (10,000,000 MB)"
            else -> null
        }
    }

    fun saveQuotaSettings() {
        val currentState = _uiState.value
        val error = validateQuotaInput(currentState.quotaInputMb)
        if (error != null && currentState.quotaEnabled) {
            _uiState.update { it.copy(quotaInputError = error) }
            return
        }

        val quotaMb = currentState.quotaInputMb.toLongOrNull() ?: 0L
        val quotaBytes = quotaMb * 1024L * 1024L
        val threshold = currentState.warningThresholdPercent.coerceIn(1, 100)

        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isSaving = true) }
            userSettingsRepository.setQuotaSettings(
                enabled = currentState.quotaEnabled,
                quotaBytes = quotaBytes,
                warningThresholdPercent = threshold
            )
            _uiState.update {
                it.copy(
                    isSaving = false,
                    savedQuotaBytes = quotaBytes,
                    savedThresholdPercent = threshold,
                    quotaInputError = null,
                    snackbarMessage = "Quota settings saved"
                )
            }
        }
    }

    suspend fun getHistoricalRecordsForExport(): List<DailyUsageRecord> = withContext(ioDispatcher) {
        val snapshots = historySnapshotStore.getAllSnapshots().associateBy { it.dateString }
        val boundaries = HistoryDateHelper.calculateDaysForPeriod(HistoryPeriod.LAST_30_DAYS)
        val resultList = mutableListOf<DailyUsageRecord>()

        for (boundary in boundaries) {
            val existing = snapshots[boundary.dateString]
            if (existing != null) {
                resultList.add(existing)
            } else {
                val totalResult = networkStatsRepository.getDeviceUsage(NetworkType.ALL, boundary.startMillis, boundary.endMillis)
                val wifiResult = networkStatsRepository.getDeviceUsage(NetworkType.WIFI, boundary.startMillis, boundary.endMillis)
                val mobileResult = networkStatsRepository.getDeviceUsage(NetworkType.MOBILE, boundary.startMillis, boundary.endMillis)

                val rx = (totalResult as? UsageQueryResult.Success)?.data?.rxBytes ?: 0L
                val tx = (totalResult as? UsageQueryResult.Success)?.data?.txBytes ?: 0L
                val wifiRx = (wifiResult as? UsageQueryResult.Success)?.data?.rxBytes ?: 0L
                val wifiTx = (wifiResult as? UsageQueryResult.Success)?.data?.txBytes ?: 0L
                val mobileRx = (mobileResult as? UsageQueryResult.Success)?.data?.rxBytes ?: 0L
                val mobileTx = (mobileResult as? UsageQueryResult.Success)?.data?.txBytes ?: 0L

                resultList.add(
                    DailyUsageRecord(
                        dateString = boundary.dateString,
                        displayLabel = boundary.displayLabel,
                        shortLabel = boundary.shortLabel,
                        startMillis = boundary.startMillis,
                        endMillis = boundary.endMillis,
                        rxBytes = rx,
                        txBytes = tx,
                        wifiRxBytes = wifiRx,
                        wifiTxBytes = wifiTx,
                        mobileRxBytes = mobileRx,
                        mobileTxBytes = mobileTx,
                        status = DailyDataStatus.COMPLETE,
                        dataSource = HistoryDataSource.ANDROID_SYSTEM
                    )
                )
            }
        }

        resultList.sortedBy { it.startMillis }
    }

    suspend fun getSpeedTestRecordsForExport(): List<SpeedTestRecord> = withContext(ioDispatcher) {
        speedTestHistoryRepository.getAllHistory(limit = 1000)
    }

    fun exportDailyUsageToUri(context: Context, uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val records = getHistoricalRecordsForExport()
                val csv = CsvExportHelper.buildHistoryCsv(records)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(csv.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
                _uiState.update { it.copy(snackbarMessage = "Usage history exported successfully (${records.size} days)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Export failed: ${e.localizedMessage ?: "Unknown error"}") }
            }
        }
    }

    fun exportSpeedTestsToUri(context: Context, uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val records = getSpeedTestRecordsForExport()
                val csv = CsvExportHelper.buildSpeedTestCsv(records)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(csv.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
                _uiState.update { it.copy(snackbarMessage = "Speed tests exported successfully (${records.size} tests)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Export failed: ${e.localizedMessage ?: "Unknown error"}") }
            }
        }
    }

    fun shareDailyUsageCsv(context: Context) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val records = getHistoricalRecordsForExport()
                val csv = CsvExportHelper.buildHistoryCsv(records)
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val exportFile = File(exportDir, "netmeter_usage_history.csv")
                FileOutputStream(exportFile).use { output ->
                    output.write(csv.toByteArray(Charsets.UTF_8))
                }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "NetMeter Historical Usage CSV")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooserIntent = Intent.createChooser(shareIntent, "Share Historical Usage CSV").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooserIntent)
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Sharing failed: ${e.localizedMessage ?: "Unknown error"}") }
            }
        }
    }

    fun shareSpeedTestCsv(context: Context) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val records = getSpeedTestRecordsForExport()
                val csv = CsvExportHelper.buildSpeedTestCsv(records)
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val exportFile = File(exportDir, "netmeter_speed_tests.csv")
                FileOutputStream(exportFile).use { output ->
                    output.write(csv.toByteArray(Charsets.UTF_8))
                }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "NetMeter Speed Tests CSV")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooserIntent = Intent.createChooser(shareIntent, "Share Speed Tests CSV").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooserIntent)
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Sharing failed: ${e.localizedMessage ?: "Unknown error"}") }
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
