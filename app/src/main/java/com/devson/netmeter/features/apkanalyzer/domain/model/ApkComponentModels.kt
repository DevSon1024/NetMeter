package com.devson.netmeter.features.apkanalyzer.domain.model

enum class ApkComponentType(val label: String) {
    ACTIVITY("Activity"),
    SERVICE("Service"),
    RECEIVER("Broadcast Receiver"),
    PROVIDER("Content Provider")
}

data class ApkIntentFilter(
    val actions: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val dataSchemes: List<String> = emptyList()
)

data class ApkComponent(
    val name: String,
    val className: String,
    val type: ApkComponentType,
    val isExported: Boolean,
    val isEnabled: Boolean = true,
    val permission: String? = null,
    val process: String? = null,
    val isDirectBootAware: Boolean = false,
    val intentFilters: List<ApkIntentFilter> = emptyList()
)

data class ApkComponentsSummary(
    val activitiesCount: Int,
    val servicesCount: Int,
    val receiversCount: Int,
    val providersCount: Int,
    val exportedCount: Int,
    val exportedWithoutPermissionCount: Int
)
