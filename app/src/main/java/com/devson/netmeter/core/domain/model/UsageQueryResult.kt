package com.devson.netmeter.core.domain.model

sealed interface UsageQueryResult<out T> {
    data class Success<out T>(val data: T) : UsageQueryResult<T>
    data object PermissionRequired : UsageQueryResult<Nothing>
    data object Empty : UsageQueryResult<Nothing>
    data class Error(
        val errorType: UsageErrorType,
        val message: String,
        val cause: Throwable? = null
    ) : UsageQueryResult<Nothing>
}

enum class UsageErrorType {
    SECURITY_DENIED,
    REMOTE_SERVICE_ERROR,
    SERVICE_UNAVAILABLE,
    INVALID_TIME_RANGE,
    UNKNOWN_ERROR
}
