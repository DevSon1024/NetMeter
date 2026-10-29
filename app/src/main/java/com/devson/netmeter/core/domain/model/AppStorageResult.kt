package com.devson.netmeter.core.domain.model

sealed interface AppStorageResult {
    data class Success(
        val appBytes: Long,
        val dataBytes: Long,
        val cacheBytes: Long,
        val totalBytes: Long
    ) : AppStorageResult

    data object PermissionRequired : AppStorageResult
    data class Unavailable(val reason: String) : AppStorageResult
}
