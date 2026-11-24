package com.devson.netmeter.features.apkanalyzer.data.parser

import com.devson.netmeter.features.apkanalyzer.domain.model.ApkCertificate
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkSignatureInfo
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkSigningScheme
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

/**
 * Robust APK Signing Block and certificate inspector.
 * Inspects APK Signature Scheme v1 (JAR), v2, v3, and v3.1 blocks and parses X.509 certificates.
 */
class ApkSigningBlockParser {

    companion object {
        private const val EOCD_MAGIC = 0x06054b50
        private const val MAX_EOCD_SEARCH = 65535 + 22

        // "APK Sig Block 42" in ASCII
        private val APK_SIG_BLOCK_MAGIC = byteArrayOf(
            0x41, 0x50, 0x4b, 0x20, 0x53, 0x69, 0x67, 0x20,
            0x42, 0x6c, 0x6f, 0x63, 0x6b, 0x20, 0x34, 0x32
        )

        private const val SIGNATURE_SCHEME_V2_BLOCK_ID = 0x7109871a
        private const val SIGNATURE_SCHEME_V3_BLOCK_ID = -0xfac4340 // 0xf05368c0 as signed int
        private const val SIGNATURE_SCHEME_V3_1_BLOCK_ID = 0x1b93ad61
    }

    fun parse(apkFile: File, fallbackCertificates: List<X509Certificate> = emptyList()): ApkSignatureInfo {
        val schemes = mutableSetOf<ApkSigningScheme>()
        val parsedCerts = mutableListOf<ApkCertificate>()
        var isSignatureValid = false
        var verificationMessage = "Unsigned application package"

        // 1. Check v1 JAR signatures via ZipFile
        try {
            ZipFile(apkFile).use { zip ->
                val hasManifestMf = zip.getEntry("META-INF/MANIFEST.MF") != null
                var hasSignatureFile = false
                var hasSignatureBlock = false

                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name.uppercase()
                    if (name.startsWith("META-INF/") && name.endsWith(".SF")) {
                        hasSignatureFile = true
                    }
                    if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                        hasSignatureBlock = true
                    }
                }

                if (hasManifestMf && hasSignatureFile && hasSignatureBlock) {
                    schemes.add(ApkSigningScheme.V1_JAR)
                }
            }
        } catch (_: Exception) {
            // Ignore ZIP scan error
        }

        // 2. Check APK Signing Block (v2, v3, v3.1)
        try {
            RandomAccessFile(apkFile, "r").use { raf ->
                val fileLength = raf.length()
                val eocdOffset = findEocdOffset(raf, fileLength)
                if (eocdOffset >= 0) {
                    raf.seek(eocdOffset + 16)
                    val cdOffset = readUint32(raf)

                    if (cdOffset >= 24) {
                        raf.seek(cdOffset - 16)
                        val magicBuffer = ByteArray(16)
                        raf.readFully(magicBuffer)

                        if (magicBuffer.contentEquals(APK_SIG_BLOCK_MAGIC)) {
                            raf.seek(cdOffset - 24)
                            val blockSizeInFooter = readUint64(raf)
                            val blockStart = cdOffset - blockSizeInFooter - 8

                            if (blockStart in 0 until (cdOffset - 24)) {
                                raf.seek(blockStart)
                                val blockSizeInHeader = readUint64(raf)

                                if (blockSizeInHeader == blockSizeInFooter) {
                                    val blockPayloadSize = (blockSizeInFooter - 16 - 8).toInt()
                                    if (blockPayloadSize in 1..(10 * 1024 * 1024)) {
                                        val payloadBytes = ByteArray(blockPayloadSize)
                                        raf.readFully(payloadBytes)
                                        val payloadBuffer = ByteBuffer.wrap(payloadBytes).order(ByteOrder.LITTLE_ENDIAN)

                                        while (payloadBuffer.remaining() >= 12) {
                                            val idSize = payloadBuffer.long
                                            val id = payloadBuffer.int
                                            val dataSize = (idSize - 4).toInt()

                                            if (dataSize < 0 || dataSize > payloadBuffer.remaining()) {
                                                break
                                            }

                                            val dataBytes = ByteArray(dataSize)
                                            payloadBuffer.get(dataBytes)

                                            when (id) {
                                                SIGNATURE_SCHEME_V2_BLOCK_ID -> {
                                                    schemes.add(ApkSigningScheme.V2_APK)
                                                    extractCertificatesFromBlock(dataBytes, parsedCerts)
                                                }
                                                SIGNATURE_SCHEME_V3_BLOCK_ID -> {
                                                    schemes.add(ApkSigningScheme.V3_APK)
                                                    extractCertificatesFromBlock(dataBytes, parsedCerts)
                                                }
                                                SIGNATURE_SCHEME_V3_1_BLOCK_ID -> {
                                                    schemes.add(ApkSigningScheme.V3_1_APK)
                                                    extractCertificatesFromBlock(dataBytes, parsedCerts)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful handling of malformed signature blocks
        }

        // 3. Fallback or augment with system PackageManager parsed certificates
        if (parsedCerts.isEmpty() && fallbackCertificates.isNotEmpty()) {
            for (cert in fallbackCertificates) {
                parsedCerts.add(convertX509Certificate(cert))
            }
        }

        val isSigned = schemes.isNotEmpty() || parsedCerts.isNotEmpty()
        if (isSigned) {
            isSignatureValid = true
            val schemeNames = schemes.joinToString(", ") { it.title }.ifEmpty { "JAR / System" }
            verificationMessage = "Signed with $schemeNames (${parsedCerts.size} certificate(s) verified)"
        } else {
            verificationMessage = "Unsigned APK (no valid signing block or JAR signatures found)"
        }

        return ApkSignatureInfo(
            supportedSchemes = schemes,
            certificates = parsedCerts.distinctBy { it.sha256Fingerprint },
            isSignatureValid = isSignatureValid,
            verificationMessage = verificationMessage,
            isSigned = isSigned
        )
    }

    private fun extractCertificatesFromBlock(dataBytes: ByteArray, destination: MutableList<ApkCertificate>) {
        try {
            val certFactory = CertificateFactory.getInstance("X.509")
            val buffer = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN)

            if (buffer.remaining() < 4) return
            val signersLen = buffer.int
            if (signersLen <= 0 || signersLen > buffer.remaining()) return

            while (buffer.remaining() >= 4) {
                val signerLen = buffer.int
                if (signerLen <= 0 || signerLen > buffer.remaining()) break
                val signerLimit = buffer.position() + signerLen

                if (buffer.remaining() < 4) break
                val signedDataLen = buffer.int
                if (signedDataLen <= 0 || signedDataLen > buffer.remaining()) {
                    buffer.position(signerLimit)
                    continue
                }

                val signedDataLimit = buffer.position() + signedDataLen

                // Digests sequence
                if (buffer.remaining() < 4) break
                val digestsLen = buffer.int
                buffer.position((buffer.position() + digestsLen).coerceAtMost(signedDataLimit))

                // Certificates sequence
                if (buffer.remaining() >= 4) {
                    val certsLen = buffer.int
                    val certsLimit = (buffer.position() + certsLen).coerceAtMost(signedDataLimit)

                    while (buffer.position() < certsLimit && buffer.remaining() >= 4) {
                        val certLen = buffer.int
                        if (certLen <= 0 || certLen > buffer.remaining()) break
                        val certBytes = ByteArray(certLen)
                        buffer.get(certBytes)

                        try {
                            val x509 = certFactory.generateCertificate(ByteArrayInputStream(certBytes)) as? X509Certificate
                            if (x509 != null) {
                                destination.add(convertX509Certificate(x509))
                            }
                        } catch (_: Exception) {
                            // Skip invalid certificate chunk
                        }
                    }
                }

                buffer.position(signerLimit)
            }
        } catch (_: Exception) {
            // Ignore block parsing errors
        }
    }

    fun convertX509Certificate(cert: X509Certificate): ApkCertificate {
        val now = System.currentTimeMillis()
        val notBefore = cert.notBefore.time
        val notAfter = cert.notAfter.time
        val encoded = cert.encoded

        val sha256 = computeFingerprint(encoded, "SHA-256")
        val sha1 = computeFingerprint(encoded, "SHA-1")

        val isSelfSigned = try {
            cert.subjectX500Principal == cert.issuerX500Principal
        } catch (_: Exception) {
            false
        }

        val keySize = try {
            val key = cert.publicKey
            when (key) {
                is java.security.interfaces.RSAPublicKey -> key.modulus.bitLength()
                is java.security.interfaces.ECPublicKey -> key.params.curve.field.fieldSize
                else -> null
            }
        } catch (_: Exception) {
            null
        }

        return ApkCertificate(
            subjectDn = cert.subjectX500Principal?.name ?: "Unknown Subject",
            issuerDn = cert.issuerX500Principal?.name ?: "Unknown Issuer",
            serialNumber = cert.serialNumber?.toString(16)?.uppercase() ?: "Unknown",
            validFrom = notBefore,
            validTo = notAfter,
            isExpired = now > notAfter,
            isNotYetValid = now < notBefore,
            sha256Fingerprint = sha256,
            sha1Fingerprint = sha1,
            publicKeyAlgorithm = cert.publicKey?.algorithm ?: "Unknown",
            publicKeySize = keySize,
            isSelfSigned = isSelfSigned
        )
    }

    private fun computeFingerprint(data: ByteArray, algorithm: String): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm).digest(data)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            "Unavailable"
        }
    }

    private fun findEocdOffset(raf: RandomAccessFile, fileLength: Long): Long {
        if (fileLength < 22) return -1L
        val searchSize = minOf(fileLength, MAX_EOCD_SEARCH.toLong()).toInt()
        val buffer = ByteArray(searchSize)
        val searchStart = fileLength - searchSize
        raf.seek(searchStart)
        raf.readFully(buffer)

        for (i in searchSize - 22 downTo 0) {
            val magic = (buffer[i].toInt() and 0xFF) or
                    ((buffer[i + 1].toInt() and 0xFF) shl 8) or
                    ((buffer[i + 2].toInt() and 0xFF) shl 16) or
                    ((buffer[i + 3].toInt() and 0xFF) shl 24)
            if (magic == EOCD_MAGIC) {
                return searchStart + i
            }
        }
        return -1L
    }

    private fun readUint32(raf: RandomAccessFile): Long {
        val b0 = raf.read()
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        return (b0.toLong() and 0xFF) or
                ((b1.toLong() and 0xFF) shl 8) or
                ((b2.toLong() and 0xFF) shl 16) or
                ((b3.toLong() and 0xFF) shl 24)
    }

    private fun readUint64(raf: RandomAccessFile): Long {
        val bytes = ByteArray(8)
        raf.readFully(bytes)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return buffer.long
    }
}
