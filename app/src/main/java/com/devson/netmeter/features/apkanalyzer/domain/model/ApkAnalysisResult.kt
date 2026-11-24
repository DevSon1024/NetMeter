package com.devson.netmeter.features.apkanalyzer.domain.model

data class ApkAnalysisResult(
    val id: String,
    val analyzedAt: Long,
    val metadata: ApkMetadata,
    val permissions: List<ApkDeclaredPermission>,
    val permissionSummary: ApkPermissionSummary,
    val components: List<ApkComponent>,
    val componentsSummary: ApkComponentsSummary,
    val manifest: ApkManifestData,
    val signature: ApkSignatureInfo,
    val archive: ApkArchiveInfo,
    val security: ApkSecurityReport,
    val limitations: List<String>
)
