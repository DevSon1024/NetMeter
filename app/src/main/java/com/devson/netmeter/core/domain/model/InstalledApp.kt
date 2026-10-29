package com.devson.netmeter.core.domain.model

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val versionName: String?,
    val versionCode: Long,
    val isLaunchable: Boolean = false,
    val targetSdkVersion: Int? = null,
    val minSdkVersion: Int? = null,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L
)
