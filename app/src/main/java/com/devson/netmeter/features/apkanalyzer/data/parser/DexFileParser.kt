package com.devson.netmeter.features.apkanalyzer.data.parser

import com.devson.netmeter.features.apkanalyzer.domain.model.DexInfo
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lightweight DEX file header and structure analyzer.
 * Extracts DEX version, method count, class count, field count and class definitions
 * without executing or dynamically loading bytecode.
 */
class DexFileParser {

    companion object {
        private const val DEX_HEADER_MIN_SIZE = 112
        private const val MAX_SAMPLE_CLASSES = 150
        private const val ENDIAN_CONSTANT = 0x12345678
        private const val REVERSE_ENDIAN_CONSTANT = 0x78563412
    }

    fun parse(fileName: String, inputStream: InputStream, fileSizeBytes: Long): DexInfo {
        // Read at least first 1 MB for header and ID tables, or entire stream up to safe limit
        val headerBytes = ByteArray(minOf(fileSizeBytes.toInt().coerceAtLeast(DEX_HEADER_MIN_SIZE), 2 * 1024 * 1024))
        var totalRead = 0
        while (totalRead < headerBytes.size) {
            val count = inputStream.read(headerBytes, totalRead, headerBytes.size - totalRead)
            if (count < 0) break
            totalRead += count
        }

        if (totalRead < DEX_HEADER_MIN_SIZE) {
            return DexInfo(
                fileName = fileName,
                version = "Unknown",
                sizeBytes = fileSizeBytes,
                classCount = 0,
                methodCount = 0,
                fieldCount = 0,
                sampleClasses = emptyList()
            )
        }

        val buffer = ByteBuffer.wrap(headerBytes, 0, totalRead).order(ByteOrder.LITTLE_ENDIAN)

        // Check magic: "dex\n" (0x64 0x65 0x78 0x0A)
        val magic0 = buffer.get()
        val magic1 = buffer.get()
        val magic2 = buffer.get()
        val magic3 = buffer.get()

        val isDex = magic0 == 'd'.code.toByte() &&
                magic1 == 'e'.code.toByte() &&
                magic2 == 'x'.code.toByte() &&
                magic3 == 0x0A.toByte()

        if (!isDex) {
            return DexInfo(
                fileName = fileName,
                version = "Invalid Magic",
                sizeBytes = fileSizeBytes,
                classCount = 0,
                methodCount = 0,
                fieldCount = 0,
                sampleClasses = emptyList()
            )
        }

        val v1 = buffer.get().toInt().toChar()
        val v2 = buffer.get().toInt().toChar()
        val v3 = buffer.get().toInt().toChar()
        buffer.get() // null terminator
        val versionStr = "$v1$v2$v3"

        buffer.int // checksum
        // 20 bytes signature
        for (i in 0 until 20) buffer.get()

        val fileSize = buffer.int
        val headerSize = buffer.int
        val endianTag = buffer.int

        if (endianTag == REVERSE_ENDIAN_CONSTANT) {
            buffer.order(ByteOrder.BIG_ENDIAN)
        }

        buffer.int // link_size
        buffer.int // link_off
        buffer.int // map_off
        val stringIdsSize = buffer.int
        val stringIdsOff = buffer.int
        val typeIdsSize = buffer.int
        val typeIdsOff = buffer.int
        val protoIdsSize = buffer.int
        val protoIdsOff = buffer.int
        val fieldIdsSize = buffer.int
        val fieldIdsOff = buffer.int
        val methodIdsSize = buffer.int
        val methodIdsOff = buffer.int
        val classDefsSize = buffer.int
        val classDefsOff = buffer.int

        val sampleClasses = mutableListOf<String>()

        try {
            // Attempt to resolve sample class names if tables are within read buffer
            if (classDefsOff in 0 until totalRead &&
                typeIdsOff in 0 until totalRead &&
                stringIdsOff in 0 until totalRead
            ) {
                val countToRead = minOf(classDefsSize, MAX_SAMPLE_CLASSES)
                for (i in 0 until countToRead) {
                    val classDefPos = classDefsOff + i * 32
                    if (classDefPos + 4 > totalRead) break
                    buffer.position(classDefPos)
                    val classIdx = buffer.int // class_idx in type_ids

                    if (classIdx in 0 until typeIdsSize) {
                        val typePos = typeIdsOff + classIdx * 4
                        if (typePos + 4 <= totalRead) {
                            buffer.position(typePos)
                            val descriptorIdx = buffer.int // descriptor_idx in string_ids

                            if (descriptorIdx in 0 until stringIdsSize) {
                                val stringIdPos = stringIdsOff + descriptorIdx * 4
                                if (stringIdPos + 4 <= totalRead) {
                                    buffer.position(stringIdPos)
                                    val stringDataOff = buffer.int
                                    if (stringDataOff in 0 until totalRead) {
                                        buffer.position(stringDataOff)
                                        val className = readUleb128AndMutf8(buffer, totalRead)
                                        if (className.isNotEmpty()) {
                                            sampleClasses.add(formatClassName(className))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback for partial buffer
        }

        return DexInfo(
            fileName = fileName,
            version = versionStr,
            sizeBytes = if (fileSizeBytes > 0) fileSizeBytes else fileSize.toLong() and 0xFFFFFFFFL,
            classCount = classDefsSize.coerceAtLeast(0),
            methodCount = methodIdsSize.coerceAtLeast(0),
            fieldCount = fieldIdsSize.coerceAtLeast(0),
            sampleClasses = sampleClasses
        )
    }

    private fun readUleb128AndMutf8(buffer: ByteBuffer, limit: Int): String {
        // Read ULEB128 utf16_size
        var result = 0
        var shift = 0
        while (buffer.hasRemaining()) {
            val byte = buffer.get().toInt()
            result = result or ((byte and 0x7F) shl shift)
            if ((byte and 0x80) == 0) break
            shift += 7
            if (shift > 28) break
        }

        val sb = StringBuilder()
        while (buffer.hasRemaining() && buffer.position() < limit) {
            val byte = buffer.get().toInt() and 0xFF
            if (byte == 0) break
            sb.append(byte.toChar())
            if (sb.length > 256) break
        }
        return sb.toString()
    }

    private fun formatClassName(rawDescriptor: String): String {
        val clean = if (rawDescriptor.startsWith("L") && rawDescriptor.endsWith(";")) {
            rawDescriptor.substring(1, rawDescriptor.length - 1)
        } else {
            rawDescriptor
        }
        return clean.replace('/', '.')
    }
}
