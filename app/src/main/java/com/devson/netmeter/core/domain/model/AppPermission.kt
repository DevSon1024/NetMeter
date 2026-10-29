package com.devson.netmeter.core.domain.model

enum class PermissionType(val label: String) {
    RUNTIME("Runtime / Dangerous"),
    NORMAL("Install-time / Normal"),
    SIGNATURE("Signature"),
    SPECIAL("Special / System"),
    UNKNOWN("Other / Custom")
}

data class AppPermission(
    val name: String,
    val label: String?,
    val description: String?,
    val isGranted: Boolean,
    val type: PermissionType
)
