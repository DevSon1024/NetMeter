package com.devson.netmeter.features.history

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HistoryEntry(
    val id: String,
    val dateLabel: String,
    val wifiUsage: String,
    val mobileUsage: String,
    val totalUsage: String
)

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val isEmptyState: Boolean = true,
    val placeholderMessage: String = "No traffic history recorded yet. Historical trends and logs will populate as data monitoring operates."
)

class HistoryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        HistoryUiState(
            entries = listOf(
                HistoryEntry("1", "Yesterday", "0.00 MB", "0.00 MB", "0.00 MB"),
                HistoryEntry("2", "Oct 26, 2026", "0.00 MB", "0.00 MB", "0.00 MB"),
                HistoryEntry("3", "Oct 25, 2026", "0.00 MB", "0.00 MB", "0.00 MB")
            ),
            isEmptyState = false
        )
    )
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
}
