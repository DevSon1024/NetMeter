package com.devson.netmeter.core.permission

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UsagePermissionViewModel(
    private val checker: UsagePermissionChecker = DefaultUsagePermissionChecker()
) : ViewModel() {

    private val _permissionStatus = MutableStateFlow<UsagePermissionStatus>(UsagePermissionStatus.Required)
    val permissionStatus: StateFlow<UsagePermissionStatus> = _permissionStatus.asStateFlow()

    private val _showExplanationDialog = MutableStateFlow(false)
    val showExplanationDialog: StateFlow<Boolean> = _showExplanationDialog.asStateFlow()

    fun checkPermission(context: Context) {
        _permissionStatus.value = checker.checkPermission(context)
    }

    fun updateStatus(status: UsagePermissionStatus) {
        _permissionStatus.value = status
    }

    fun openExplanationDialog() {
        _showExplanationDialog.value = true
    }

    fun dismissExplanationDialog() {
        _showExplanationDialog.value = false
    }

    fun proceedToSettings(context: Context) {
        _showExplanationDialog.value = false
        UsagePermissionHelper.openUsageAccessSettings(context)
    }
}
