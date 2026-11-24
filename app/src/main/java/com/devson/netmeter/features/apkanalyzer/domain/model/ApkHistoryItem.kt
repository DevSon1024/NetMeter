package com.devson.netmeter.features.apkanalyzer.domain.model

data class ApkHistoryItem(
    val id: Long = 0,
    val fileName: String,
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long,
    val fileSizeBytes: Long,
    val analyzedAt: Long,
    val totalPermissions: Int,
    val dangerousPermissions: Int,
    val exportedComponents: Int,
    val securityWarnings: Int,
    val highRiskFindings: Int
)
