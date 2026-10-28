package com.devson.netmeter.features.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.netmeter.core.data.repository.AndroidNetworkStatsRepository
import com.devson.netmeter.core.domain.model.DeviceUsage
import com.devson.netmeter.core.domain.model.NetworkType
import com.devson.netmeter.core.domain.model.UsageBucket
import com.devson.netmeter.core.domain.model.UsageQueryResult
import com.devson.netmeter.core.domain.repository.NetworkStatsRepository
import com.devson.netmeter.core.permission.DefaultUsagePermissionChecker
import com.devson.netmeter.core.permission.UsagePermissionChecker
import com.devson.netmeter.core.permission.UsagePermissionStatus
import com.devson.netmeter.core.util.AndroidConnectivityObserver
import com.devson.netmeter.core.util.AppUsagePeriod
import com.devson.netmeter.core.util.ConnectivityObserver
import com.devson.netmeter.core.util.CurrentNetworkState
import com.devson.netmeter.core.util.FormatUtils
import com.devson.netmeter.core.util.TimeRangeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UsageMetric(
    val rxBytes: Long,
    val txBytes: Long,
    val totalBytes: Long = rxBytes + txBytes,
    val isAvailable: Boolean = true
)

data class HourlyTrafficPoint(
    val hourOfDay: Int,
    val label: String,
    val rxBytes: Long,
    val txBytes: Long,
    val totalBytes: Long = rxBytes + txBytes
)

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val connectionState: CurrentNetworkState = CurrentNetworkState(),
    val todayTotalUsage: UsageMetric? = null,
    val todayWifiUsage: UsageMetric? = null,
    val todayMobileUsage: UsageMetric? = null,
    val timeRangeDescription: String = "",
    val lastRefreshedTime: String = "",
    val lastRefreshedMillis: Long? = null,
    val errorMessage: String? = null,
    val hourlyDistribution: List<HourlyTrafficPoint> = emptyList()
)

class DashboardViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: NetworkStatsRepository = AndroidNetworkStatsRepository(application.applicationContext),
    private val connectivityObserver: ConnectivityObserver = AndroidConnectivityObserver(application.applicationContext),
    private val permissionChecker: UsagePermissionChecker = DefaultUsagePermissionChecker()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            connectionState = connectivityObserver.getCurrentState()
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeConnectivity()
        loadDashboardData(isRefresh = false)
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.networkState.collect { connectionState ->
                _uiState.update { it.copy(connectionState = connectionState) }
            }
        }
    }

    fun refresh() {
        loadDashboardData(isRefresh = true)
    }

    fun loadDashboardData(isRefresh: Boolean = false) {
        val permissionStatus = permissionChecker.checkPermission(getApplication())
        val hasPermission = permissionStatus is UsagePermissionStatus.Granted
        val nowMillis = System.currentTimeMillis()
        val (startOfToday, _) = TimeRangeHelper.getTimeRange(AppUsagePeriod.TODAY, nowMillis)
        val timeDesc = "${FormatUtils.formatDate(nowMillis)} (${FormatUtils.formatTimeRange(startOfToday, nowMillis)})"

        if (!hasPermission) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isPermissionGranted = false,
                    todayTotalUsage = null,
                    todayWifiUsage = null,
                    todayMobileUsage = null,
                    timeRangeDescription = timeDesc,
                    errorMessage = null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = !isRefresh && it.todayTotalUsage == null,
                isRefreshing = isRefresh,
                isPermissionGranted = true,
                timeRangeDescription = timeDesc,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val totalResult = repository.getDeviceUsage(NetworkType.ALL, startOfToday, nowMillis)
                val wifiResult = repository.getDeviceUsage(NetworkType.WIFI, startOfToday, nowMillis)
                val mobileResult = repository.getDeviceUsage(NetworkType.MOBILE, startOfToday, nowMillis)
                val bucketsResult = repository.getTimeBuckets(NetworkType.ALL, startOfToday, nowMillis)

                if (totalResult is UsageQueryResult.PermissionRequired ||
                    wifiResult is UsageQueryResult.PermissionRequired ||
                    mobileResult is UsageQueryResult.PermissionRequired
                ) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isPermissionGranted = false,
                            todayTotalUsage = null,
                            todayWifiUsage = null,
                            todayMobileUsage = null
                        )
                    }
                    return@launch
                }

                val totalMetric = when (totalResult) {
                    is UsageQueryResult.Success -> UsageMetric(totalResult.data.rxBytes, totalResult.data.txBytes)
                    is UsageQueryResult.Empty -> UsageMetric(0L, 0L)
                    else -> null
                }

                val wifiMetric = when (wifiResult) {
                    is UsageQueryResult.Success -> UsageMetric(wifiResult.data.rxBytes, wifiResult.data.txBytes)
                    is UsageQueryResult.Empty -> UsageMetric(0L, 0L)
                    else -> UsageMetric(0L, 0L, isAvailable = false)
                }

                val mobileMetric = when (mobileResult) {
                    is UsageQueryResult.Success -> UsageMetric(mobileResult.data.rxBytes, mobileResult.data.txBytes)
                    is UsageQueryResult.Empty -> UsageMetric(0L, 0L)
                    else -> UsageMetric(0L, 0L, isAvailable = false)
                }

                val hourlyPoints = when (bucketsResult) {
                    is UsageQueryResult.Success -> computeHourlyDistribution(bucketsResult.data, startOfToday, nowMillis)
                    else -> computeDefaultHourlyDistribution()
                }

                val refreshTime = FormatUtils.formatTime(nowMillis)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isPermissionGranted = true,
                        todayTotalUsage = totalMetric,
                        todayWifiUsage = wifiMetric,
                        todayMobileUsage = mobileMetric,
                        hourlyDistribution = hourlyPoints,
                        lastRefreshedTime = refreshTime,
                        lastRefreshedMillis = nowMillis,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: "Failed to refresh dashboard network usage"
                    )
                }
            }
        }
    }

    companion object {
        fun computeHourlyDistribution(
            buckets: List<UsageBucket>,
            startOfDayMillis: Long,
            nowMillis: Long
        ): List<HourlyTrafficPoint> {
            val intervals = 6
            val intervalDurationMillis = 4L * 60 * 60 * 1000

            val rxTotals = LongArray(intervals)
            val txTotals = LongArray(intervals)

            for (bucket in buckets) {
                val bucketCenter = (bucket.startTimeMillis + bucket.endTimeMillis) / 2
                val offset = bucketCenter - startOfDayMillis
                if (offset >= 0) {
                    val index = ((offset / intervalDurationMillis).toInt()).coerceIn(0, intervals - 1)
                    rxTotals[index] += bucket.rxBytes
                    txTotals[index] += bucket.txBytes
                }
            }

            val labels = listOf("12 AM", "4 AM", "8 AM", "12 PM", "4 PM", "8 PM")
            val hours = listOf(0, 4, 8, 12, 16, 20)

            return List(intervals) { index ->
                HourlyTrafficPoint(
                    hourOfDay = hours[index],
                    label = labels[index],
                    rxBytes = rxTotals[index],
                    txBytes = txTotals[index]
                )
            }
        }

        fun computeDefaultHourlyDistribution(): List<HourlyTrafficPoint> {
            val labels = listOf("12 AM", "4 AM", "8 AM", "12 PM", "4 PM", "8 PM")
            val hours = listOf(0, 4, 8, 12, 16, 20)
            return List(6) { index ->
                HourlyTrafficPoint(
                    hourOfDay = hours[index],
                    label = labels[index],
                    rxBytes = 0L,
                    txBytes = 0L
                )
            }
        }
    }
}
