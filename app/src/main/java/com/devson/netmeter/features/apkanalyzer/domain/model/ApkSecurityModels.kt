package com.devson.netmeter.features.apkanalyzer.domain.model

enum class SecuritySeverity(val label: String) {
    HIGH_RISK("High Risk"),
    WARNING("Warning"),
    INFORMATIONAL("Informational")
}

data class ApkSecurityFinding(
    val id: String,
    val title: String,
    val severity: SecuritySeverity,
    val explanation: String,
    val whyItMatters: String,
    val relevantEntry: String,
    val recommendation: String
)

data class ApkSecurityReport(
    val findings: List<ApkSecurityFinding>,
    val highRiskCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val disclaimer: String = "Static analysis findings reflect declared metadata and package configuration. They do not constitute malware certification or a safety percentage guarantee."
)
