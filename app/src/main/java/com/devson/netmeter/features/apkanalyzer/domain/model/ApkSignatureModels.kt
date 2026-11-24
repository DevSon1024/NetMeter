package com.devson.netmeter.features.apkanalyzer.domain.model

enum class ApkSigningScheme(val title: String, val description: String) {
    V1_JAR("JAR (v1)", "Legacy JAR signature scheme (META-INF files)"),
    V2_APK("APK Signature Scheme v2", "Whole-file APK signing introduced in Android 7.0 (API 24)"),
    V3_APK("APK Signature Scheme v3", "Key rotation signing introduced in Android 9.0 (API 28)"),
    V3_1_APK("APK Signature Scheme v3.1", "Targeted SDK signing introduced in Android 13 (API 33)")
}

data class ApkCertificate(
    val subjectDn: String,
    val issuerDn: String,
    val serialNumber: String,
    val validFrom: Long,
    val validTo: Long,
    val isExpired: Boolean,
    val isNotYetValid: Boolean,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val publicKeyAlgorithm: String,
    val publicKeySize: Int?,
    val isSelfSigned: Boolean
)

data class ApkSignatureInfo(
    val supportedSchemes: Set<ApkSigningScheme>,
    val certificates: List<ApkCertificate>,
    val isSignatureValid: Boolean,
    val verificationMessage: String,
    val isSigned: Boolean
)
