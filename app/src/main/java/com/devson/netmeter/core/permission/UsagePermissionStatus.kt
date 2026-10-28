package com.devson.netmeter.core.permission

/**
 * Represents the current status of the Usage Access (PACKAGE_USAGE_STATS) permission.
 */
sealed interface UsagePermissionStatus {
    data object Granted : UsagePermissionStatus
    data object Required : UsagePermissionStatus
    data class Error(val message: String) : UsagePermissionStatus
}
