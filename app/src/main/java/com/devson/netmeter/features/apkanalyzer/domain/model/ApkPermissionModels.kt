package com.devson.netmeter.features.apkanalyzer.domain.model

enum class ApkPermissionCategory(val displayName: String) {
    NETWORK("Network & Internet"),
    STORAGE("Storage & Files"),
    CAMERA_MIC("Camera & Microphone"),
    LOCATION("Location"),
    CONTACTS_CALENDAR("Contacts & Calendar"),
    PHONE_SMS("Phone & SMS"),
    NOTIFICATIONS("Notifications"),
    BACKGROUND("Background Execution"),
    SYSTEM_SPECIAL("System & Special Access"),
    OTHER("Other Permissions")
}

enum class ApkPermissionProtection(val label: String) {
    DANGEROUS_RUNTIME("Dangerous (Runtime)"),
    NORMAL_INSTALL("Normal (Install-time)"),
    SIGNATURE_PRIVILEGED("Signature / Privileged"),
    CUSTOM_UNKNOWN("Custom / Unknown")
}

data class ApkDeclaredPermission(
    val name: String,
    val label: String?,
    val description: String?,
    val category: ApkPermissionCategory,
    val protection: ApkPermissionProtection,
    val maxSdkVersion: Int? = null,
    val isCustom: Boolean = false
)

data class ApkPermissionSummary(
    val totalCount: Int,
    val dangerousCount: Int,
    val normalCount: Int,
    val signatureCount: Int,
    val customCount: Int,
    val byCategory: Map<ApkPermissionCategory, Int>
)
