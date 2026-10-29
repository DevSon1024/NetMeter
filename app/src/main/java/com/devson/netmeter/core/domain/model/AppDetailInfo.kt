package com.devson.netmeter.core.domain.model

data class AppDetailInfo(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdkVersion: Int?,
    val targetSdkVersion: Int?,
    val isSystemApp: Boolean,
    val isLaunchable: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val installerPackageName: String?,
    val permissions: List<AppPermission>,
    val storageResult: AppStorageResult
)
