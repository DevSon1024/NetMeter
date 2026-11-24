package com.devson.netmeter.features.apkanalyzer.data.security

import com.devson.netmeter.features.apkanalyzer.domain.model.ApkArchiveInfo
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkComponent
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkComponentType
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkDeclaredPermission
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkMetadata
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkPermissionProtection
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkSecurityFinding
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkSecurityReport
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkSignatureInfo
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkSigningScheme
import com.devson.netmeter.features.apkanalyzer.domain.model.SecuritySeverity

/**
 * Extensible static security analysis engine for APK packages.
 * Evaluates manifest declarations, component exposure, permission combinations,
 * archive architecture, and signing configuration.
 */
class ApkSecurityEngine {

    fun evaluate(
        metadata: ApkMetadata,
        permissions: List<ApkDeclaredPermission>,
        components: List<ApkComponent>,
        signature: ApkSignatureInfo,
        archive: ApkArchiveInfo
    ): ApkSecurityReport {
        val findings = mutableListOf<ApkSecurityFinding>()

        // 1. Debuggable Configuration
        if (metadata.isDebuggable) {
            findings.add(
                ApkSecurityFinding(
                    id = "CONF_DEBUGGABLE",
                    title = "Application is Debuggable",
                    severity = SecuritySeverity.HIGH_RISK,
                    explanation = "The android:debuggable flag is explicitly enabled in the manifest.",
                    whyItMatters = "Allows anyone with physical or ADB access to attach a debugger, inspect memory, extract private app storage, and execute arbitrary code in the app context.",
                    relevantEntry = "android:debuggable=\"true\"",
                    recommendation = "Ensure debuggable is disabled in release builds (set android:debuggable=\"false\")."
                )
            )
        }

        // 2. Cleartext Traffic
        if (metadata.usesCleartextTraffic == true) {
            findings.add(
                ApkSecurityFinding(
                    id = "NET_CLEARTEXT_TRAFFIC",
                    title = "Cleartext HTTP Traffic Allowed",
                    severity = SecuritySeverity.HIGH_RISK,
                    explanation = "The app explicitly allows unencrypted cleartext HTTP network traffic.",
                    whyItMatters = "Transmitted data (credentials, personal details, API tokens) can be intercepted and modified via Man-in-the-Middle (MitM) attacks on Wi-Fi and mobile networks.",
                    relevantEntry = "android:usesCleartextTraffic=\"true\"",
                    recommendation = "Enforce HTTPS/TLS for all endpoints and configure a strict Network Security Config."
                )
            )
        }

        // 3. Signature Status
        if (!signature.isSigned || !signature.isSignatureValid) {
            findings.add(
                ApkSecurityFinding(
                    id = "SIG_UNSIGNED",
                    title = "Unsigned or Corrupted APK Signature",
                    severity = SecuritySeverity.HIGH_RISK,
                    explanation = "No valid APK signing block or JAR signatures could be verified.",
                    whyItMatters = "Android refuses to install unsigned APKs, or the package might be tampered with or malformed.",
                    relevantEntry = "META-INF / APK Signing Block",
                    recommendation = "Sign the APK with a valid signing key using apksigner or standard release build configuration."
                )
            )
        } else {
            // Check debug certificate
            val isDebugCert = signature.certificates.any {
                it.subjectDn.contains("Android Debug", ignoreCase = true) ||
                        it.issuerDn.contains("Android Debug", ignoreCase = true)
            }
            if (isDebugCert) {
                findings.add(
                    ApkSecurityFinding(
                        id = "SIG_DEBUG_KEY",
                        title = "Signed with Android Debug Key",
                        severity = SecuritySeverity.WARNING,
                        explanation = "The APK is signed with the well-known Android debug certificate.",
                        whyItMatters = "Anyone has access to the standard Android debug keystore and could craft updates spoofing this application if distributed publicly.",
                        relevantEntry = "Subject: ${signature.certificates.firstOrNull()?.subjectDn.orEmpty()}",
                        recommendation = "Sign production releases with a unique, protected production keystore."
                    )
                )
            }

            // Check signature schemes
            val hasModernScheme = signature.supportedSchemes.contains(ApkSigningScheme.V2_APK) ||
                    signature.supportedSchemes.contains(ApkSigningScheme.V3_APK) ||
                    signature.supportedSchemes.contains(ApkSigningScheme.V3_1_APK)

            if (!hasModernScheme && signature.supportedSchemes.contains(ApkSigningScheme.V1_JAR)) {
                findings.add(
                    ApkSecurityFinding(
                        id = "SIG_V1_ONLY",
                        title = "Legacy V1 (JAR) Signature Scheme Only",
                        severity = SecuritySeverity.WARNING,
                        explanation = "The APK uses only JAR signing without APK Signature Scheme v2 or v3.",
                        whyItMatters = "V1 signing is vulnerable to Janus vulnerability (CVE-2017-13156) on older Android versions and slows down verification times.",
                        relevantEntry = "Signing Schemes: ${signature.supportedSchemes.joinToString { it.title }}",
                        recommendation = "Enable APK Signature Scheme v2 and v3 in Gradle signing configuration."
                    )
                )
            }

            // Check certificate validity expiration
            signature.certificates.forEach { cert ->
                if (cert.isExpired) {
                    findings.add(
                        ApkSecurityFinding(
                            id = "SIG_CERT_EXPIRED",
                            title = "Signing Certificate Expired",
                            severity = SecuritySeverity.WARNING,
                            explanation = "The signing certificate expired on ${java.util.Date(cert.validTo)}.",
                            whyItMatters = "Existing installations may continue running, but certificate validation issues can arise during deployment or verification.",
                            relevantEntry = "Valid until: ${java.util.Date(cert.validTo)}",
                            recommendation = "Renew signing key with adequate multi-year validity period."
                        )
                    )
                }
            }
        }

        // 4. Exported Components Without Permissions
        val exportedServicesWithoutPerm = components.filter {
            it.type == ApkComponentType.SERVICE && it.isExported && it.permission.isNullOrBlank()
        }
        if (exportedServicesWithoutPerm.isNotEmpty()) {
            findings.add(
                ApkSecurityFinding(
                    id = "COMP_EXPORTED_SERVICES",
                    title = "${exportedServicesWithoutPerm.size} Exported Service(s) Without Permission",
                    severity = SecuritySeverity.HIGH_RISK,
                    explanation = "One or more background Services are exported without requiring any caller permission: ${exportedServicesWithoutPerm.take(3).joinToString { it.name }}.",
                    whyItMatters = "Any third-party application installed on the device can bind to or start these services, triggering background tasks or private logic.",
                    relevantEntry = "<service android:name=\"${exportedServicesWithoutPerm.first().className}\" android:exported=\"true\" />",
                    recommendation = "Set android:exported=\"false\" if the service is internal, or require a signature/system permission."
                )
            )
        }

        val exportedProvidersWithoutPerm = components.filter {
            it.type == ApkComponentType.PROVIDER && it.isExported && it.permission.isNullOrBlank()
        }
        if (exportedProvidersWithoutPerm.isNotEmpty()) {
            findings.add(
                ApkSecurityFinding(
                    id = "COMP_EXPORTED_PROVIDERS",
                    title = "${exportedProvidersWithoutPerm.size} Exported Content Provider(s) Without Permission",
                    severity = SecuritySeverity.HIGH_RISK,
                    explanation = "Content Provider(s) are exported to all apps without read/write permission requirements: ${exportedProvidersWithoutPerm.take(3).joinToString { it.name }}.",
                    whyItMatters = "External applications can query, insert, or modify data in the provider's database or files without restriction.",
                    relevantEntry = "<provider android:name=\"${exportedProvidersWithoutPerm.first().className}\" android:exported=\"true\" />",
                    recommendation = "Enforce android:readPermission and android:writePermission, or set android:exported=\"false\"."
                )
            )
        }

        val exportedReceiversWithoutPerm = components.filter {
            it.type == ApkComponentType.RECEIVER && it.isExported && it.permission.isNullOrBlank()
        }
        if (exportedReceiversWithoutPerm.isNotEmpty()) {
            findings.add(
                ApkSecurityFinding(
                    id = "COMP_EXPORTED_RECEIVERS",
                    title = "${exportedReceiversWithoutPerm.size} Exported Broadcast Receiver(s) Without Permission",
                    severity = SecuritySeverity.WARNING,
                    explanation = "Broadcast Receiver(s) are exported without caller permission protection: ${exportedReceiversWithoutPerm.take(3).joinToString { it.name }}.",
                    whyItMatters = "Malicious or third-party apps can spoof broadcast intents to trigger internal app events or handlers.",
                    relevantEntry = "<receiver android:name=\"${exportedReceiversWithoutPerm.first().className}\" android:exported=\"true\" />",
                    recommendation = "Verify intent actions, validate intent sender authenticity, or restrict with permissions."
                )
            )
        }

        // 5. Allow Backup Configuration
        if (metadata.allowBackup) {
            findings.add(
                ApkSecurityFinding(
                    id = "CONF_ALLOW_BACKUP",
                    title = "Application Data Backup Enabled",
                    severity = SecuritySeverity.WARNING,
                    explanation = "The android:allowBackup attribute is set to true.",
                    whyItMatters = "App private data, databases, and preferences can be extracted via ADB backup (adb backup) unless excluded in backup rules.",
                    relevantEntry = "android:allowBackup=\"true\"",
                    recommendation = "Set android:allowBackup=\"false\" or define strict android:fullBackupContent rules."
                )
            )
        }

        // 6. Test Only Flag
        if (metadata.isTestOnly) {
            findings.add(
                ApkSecurityFinding(
                    id = "CONF_TEST_ONLY",
                    title = "Test-Only Flag Set",
                    severity = SecuritySeverity.WARNING,
                    explanation = "The android:testOnly flag is enabled.",
                    whyItMatters = "Prevents standard installation via Google Play or standard package installer without -t flag.",
                    relevantEntry = "android:testOnly=\"true\"",
                    recommendation = "Remove android:testOnly before distributing release packages."
                )
            )
        }

        // 7. Target SDK Evaluation
        val targetSdk = metadata.targetSdkVersion ?: 0
        if (targetSdk > 0 && targetSdk < 28) {
            findings.add(
                ApkSecurityFinding(
                    id = "SDK_TARGET_VERY_LOW",
                    title = "Critically Low Target SDK (API $targetSdk)",
                    severity = SecuritySeverity.HIGH_RISK,
                    explanation = "The application targets Android API $targetSdk, which is significantly below modern Android standards.",
                    whyItMatters = "Bypasses runtime permission protections, background execution limits, and modern TLS requirements.",
                    relevantEntry = "android:targetSdkVersion=\"$targetSdk\"",
                    recommendation = "Upgrade targetSdkVersion to at least API 34+."
                )
            )
        } else if (targetSdk in 28..30) {
            findings.add(
                ApkSecurityFinding(
                    id = "SDK_TARGET_OUTDATED",
                    title = "Outdated Target SDK (API $targetSdk)",
                    severity = SecuritySeverity.WARNING,
                    explanation = "The application targets API $targetSdk (Android 9-11).",
                    whyItMatters = "Misses newer privacy enhancements such as scoped storage restrictions and explicit exported requirements.",
                    relevantEntry = "android:targetSdkVersion=\"$targetSdk\"",
                    recommendation = "Upgrade targetSdkVersion to the current Google Play target requirements."
                )
            )
        }

        // 8. Permission Combinations
        val permNames = permissions.map { it.name }.toSet()

        val hasInternet = "android.permission.INTERNET" in permNames
        val hasSms = "android.permission.SEND_SMS" in permNames || "android.permission.READ_SMS" in permNames || "android.permission.RECEIVE_SMS" in permNames
        val hasContacts = "android.permission.READ_CONTACTS" in permNames
        val hasLocation = "android.permission.ACCESS_FINE_LOCATION" in permNames || "android.permission.ACCESS_COARSE_LOCATION" in permNames
        val hasBackgroundLoc = "android.permission.ACCESS_BACKGROUND_LOCATION" in permNames
        val hasCamera = "android.permission.CAMERA" in permNames
        val hasAudio = "android.permission.RECORD_AUDIO" in permNames

        if (hasInternet && hasSms) {
            findings.add(
                ApkSecurityFinding(
                    id = "PERM_SMS_INTERNET",
                    title = "SMS and Internet Access Declared",
                    severity = SecuritySeverity.WARNING,
                    explanation = "The application requests both SMS manipulation and network connectivity.",
                    whyItMatters = "Can intercept 2FA codes, send premium SMS, or exfiltrate private messages to remote servers.",
                    relevantEntry = "SMS + INTERNET permissions",
                    recommendation = "Audit whether SMS permissions are genuinely necessary for core app features."
                )
            )
        }

        if (hasInternet && hasContacts && (hasLocation || hasSms)) {
            findings.add(
                ApkSecurityFinding(
                    id = "PERM_PRIVACY_EXFILTRATION",
                    title = "Broad Personal Data & Network Access",
                    severity = SecuritySeverity.WARNING,
                    explanation = "The app combines Contacts with Location or SMS, alongside Internet access.",
                    whyItMatters = "Enables harvesting device user profiles, contact books, and physical telemetry.",
                    relevantEntry = "Contacts + Location/SMS + Internet",
                    recommendation = "Ensure appropriate privacy policy disclosures and user consent mechanisms are present."
                )
            )
        }

        if (hasLocation && hasBackgroundLoc) {
            findings.add(
                ApkSecurityFinding(
                    id = "PERM_BACKGROUND_LOCATION",
                    title = "Persistent Background Location Tracking",
                    severity = SecuritySeverity.WARNING,
                    explanation = "The app requests ACCESS_BACKGROUND_LOCATION.",
                    whyItMatters = "Allows gathering user coordinates even when the app is minimized or closed, posing privacy and battery consumption risks.",
                    relevantEntry = "android.permission.ACCESS_BACKGROUND_LOCATION",
                    recommendation = "Verify that background location is strictly required and prominently disclosed."
                )
            )
        }

        // 9. Dangerous Permissions Summary
        val dangerousCount = permissions.count { it.protection == ApkPermissionProtection.DANGEROUS_RUNTIME }
        if (dangerousCount >= 10) {
            findings.add(
                ApkSecurityFinding(
                    id = "PERM_EXCESSIVE_DANGEROUS",
                    title = "High Volume of Dangerous Permissions ($dangerousCount)",
                    severity = SecuritySeverity.WARNING,
                    explanation = "The application requests $dangerousCount runtime permissions requiring explicit user approval.",
                    whyItMatters = "Excessive permission requests increase the attack surface and potential exposure of sensitive device APIs.",
                    relevantEntry = "$dangerousCount runtime permissions",
                    recommendation = "Minimize requested permissions by utilizing system pickers and contract intents."
                )
            )
        }

        // 10. Native Libraries & ABI Support
        if (archive.nativeLibraries.isNotEmpty()) {
            val abis = archive.supportedAbis
            val has64Bit = "arm64-v8a" in abis || "x86_64" in abis
            val has32Bit = "armeabi-v7a" in abis || "x86" in abis

            if (has32Bit && !has64Bit) {
                findings.add(
                    ApkSecurityFinding(
                        id = "ARCH_NO_64BIT",
                        title = "Missing 64-Bit Native Architecture",
                        severity = SecuritySeverity.WARNING,
                        explanation = "The APK includes 32-bit native libraries (${abis.joinToString()}) without 64-bit binaries.",
                        whyItMatters = "Modern Android devices running 64-bit-only operating systems cannot execute 32-bit binaries.",
                        relevantEntry = "lib/: ${archive.nativeLibraries.take(5).joinToString()}",
                        recommendation = "Compile and package arm64-v8a native libraries."
                    )
                )
            } else {
                findings.add(
                    ApkSecurityFinding(
                        id = "ARCH_NATIVE_LIBS",
                        title = "Native Libraries Included (${archive.nativeLibraries.size} .so files)",
                        severity = SecuritySeverity.INFORMATIONAL,
                        explanation = "Package contains native compiled shared objects across: ${abis.joinToString()}.",
                        whyItMatters = "Native C/C++ code executes directly on CPU, bypassing managed ART memory safety.",
                        relevantEntry = "lib/ (${archive.nativeLibraries.size} libraries)",
                        recommendation = "Verify memory safety and security hardening (stack canary, RELRO, fortify source)."
                    )
                )
            }
        }

        // 11. Informational Positive Indicators
        if (signature.supportedSchemes.contains(ApkSigningScheme.V2_APK) || signature.supportedSchemes.contains(ApkSigningScheme.V3_APK)) {
            findings.add(
                ApkSecurityFinding(
                    id = "SIG_MODERN_VERIFIED",
                    title = "Modern Whole-File Signature Scheme Active",
                    severity = SecuritySeverity.INFORMATIONAL,
                    explanation = "Signed using ${signature.supportedSchemes.joinToString { it.title }}.",
                    whyItMatters = "Guarantees integrity of the entire APK archive including ZIP metadata and binary entries.",
                    relevantEntry = "APK Signature Scheme v2/v3",
                    recommendation = "Continue using whole-file APK signature schemes."
                )
            )
        }

        val highRiskCount = findings.count { it.severity == SecuritySeverity.HIGH_RISK }
        val warningCount = findings.count { it.severity == SecuritySeverity.WARNING }
        val infoCount = findings.count { it.severity == SecuritySeverity.INFORMATIONAL }

        return ApkSecurityReport(
            findings = findings.sortedWith(
                compareBy<ApkSecurityFinding> { it.severity.ordinal }
                    .thenBy { it.title }
            ),
            highRiskCount = highRiskCount,
            warningCount = warningCount,
            infoCount = infoCount
        )
    }
}
