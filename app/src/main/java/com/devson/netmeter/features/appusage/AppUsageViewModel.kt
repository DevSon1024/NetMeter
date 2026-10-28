package com.devson.netmeter.features.appusage

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UsagePeriod(val label: String) {
    TODAY("Today"),
    WEEK("This Week"),
    MONTH("This Month")
}

data class AppUsageItem(
    val appName: String,
    val packageName: String,
    val usageFormatted: String,
    val percentage: Float
)

data class AppUsageUiState(
    val selectedPeriod: UsagePeriod = UsagePeriod.TODAY,
    val totalUsagePlaceholder: String = "0.00 MB",
    val placeholderApps: List<AppUsageItem> = emptyList(),
    val infoMessage: String = "App-specific network traffic will be tracked here once network statistics permissions are granted in subsequent updates."
)

class AppUsageViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        AppUsageUiState(
            placeholderApps = listOf(
                AppUsageItem("Chrome Browser", "com.android.chrome", "0.00 MB", 0.0f),
                AppUsageItem("YouTube", "com.google.android.youtube", "0.00 MB", 0.0f),
                AppUsageItem("Google Play Services", "com.google.android.gms", "0.00 MB", 0.0f)
            )
        )
    )
    val uiState: StateFlow<AppUsageUiState> = _uiState.asStateFlow()

    fun selectPeriod(period: UsagePeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period)
    }
}
