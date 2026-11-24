package com.devson.netmeter.features.apkanalyzer.data.parser

import com.devson.netmeter.features.apkanalyzer.domain.model.ApkManifestData
import com.devson.netmeter.features.apkanalyzer.domain.model.AxmlAttribute
import com.devson.netmeter.features.apkanalyzer.domain.model.AxmlElement
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Robust binary AndroidManifest.xml (AXML) chunk parser.
 * Safely parses string pools, resource maps, namespace and element chunks into structured nodes and XML text.
 */
class AxmlBinaryParser {

    companion object {
        private const val CHUNK_AXML_FILE = 0x00080003
        private const val CHUNK_STRING_POOL = 0x001C0001
        private const val CHUNK_RESOURCE_MAP = 0x00080180
        private const val CHUNK_START_NAMESPACE = 0x00100100
        private const val CHUNK_END_NAMESPACE = 0x00100101
        private const val CHUNK_START_ELEMENT = 0x00100102
        private const val CHUNK_END_ELEMENT = 0x00100103
        private const val CHUNK_CDATA = 0x00100104

        // Common Android resource IDs
        private val KNOWN_ATTR_RES_IDS = mapOf(
            0x01010000 to "theme",
            0x01010001 to "label",
            0x01010002 to "icon",
            0x01010003 to "name",
            0x01010006 to "permission",
            0x01010007 to "installLocation",
            0x0101000e to "enabled",
            0x01010010 to "exported",
            0x01010011 to "process",
            0x01010017 to "minSdkVersion",
            0x01010018 to "targetSdkVersion",
            0x01010020 to "versionCode",
            0x01010021 to "versionName",
            0x01010024 to "debuggable",
            0x0101002b to "value",
            0x01010272 to "testOnly",
            0x01010280 to "allowBackup",
            0x010104e7 to "extractNativeLibs",
            0x010104ea to "directBootAware",
            0x0101057a to "usesCleartextTraffic",
            0x0101058a to "appCategory"
        )
    }

    fun parse(bytes: ByteArray): ApkManifestData {
        if (bytes.size < 8) {
            return ApkManifestData(formattedXml = "<!-- Invalid or empty binary manifest -->")
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileType = buffer.int
        if (fileType != CHUNK_AXML_FILE && (fileType and 0xFFFF) != 0x0003) {
            // Might be plain text XML fallback
            val plainText = try {
                String(bytes, Charsets.UTF_8).trim()
            } catch (_: Exception) {
                ""
            }
            return if (plainText.startsWith("<?xml") || plainText.startsWith("<manifest")) {
                ApkManifestData(formattedXml = plainText)
            } else {
                ApkManifestData(formattedXml = "<!-- Unrecognized binary XML header: 0x${Integer.toHexString(fileType)} -->")
            }
        }

        buffer.int // fileSize

        var stringPool: List<String> = emptyList()
        var resourceMap: List<Int> = emptyList()

        val rootElements = mutableListOf<AxmlElement>()
        val elementStack = ArrayDeque<MutableElement>()
        val namespaces = mutableMapOf<String, String>() // uri to prefix
        val rawAttributesMap = mutableMapOf<String, String>()

        while (buffer.hasRemaining() && buffer.remaining() >= 8) {
            val chunkType = buffer.int
            val chunkSize = buffer.int

            if (chunkSize < 8 || chunkSize > buffer.remaining() + 8) {
                break
            }

            val nextChunkPosition = buffer.position() - 8 + chunkSize

            when (chunkType) {
                CHUNK_STRING_POOL -> {
                    stringPool = parseStringPool(buffer, chunkSize)
                }
                CHUNK_RESOURCE_MAP -> {
                    val count = (chunkSize - 8) / 4
                    val resIds = mutableListOf<Int>()
                    for (i in 0 until count) {
                        if (buffer.remaining() >= 4) {
                            resIds.add(buffer.int)
                        }
                    }
                    resourceMap = resIds
                }
                CHUNK_START_NAMESPACE -> {
                    buffer.int // lineNumber
                    buffer.int // comment
                    val prefixIdx = buffer.int
                    val uriIdx = buffer.int
                    val prefix = stringPool.getOrNull(prefixIdx).orEmpty()
                    val uri = stringPool.getOrNull(uriIdx).orEmpty()
                    if (uri.isNotEmpty()) {
                        namespaces[uri] = prefix.ifEmpty { "android" }
                    }
                }
                CHUNK_END_NAMESPACE -> {
                    // Namespace closed
                }
                CHUNK_START_ELEMENT -> {
                    buffer.int // lineNumber
                    buffer.int // comment
                    val nsIdx = buffer.int
                    val nameIdx = buffer.int
                    buffer.short // attrStart
                    buffer.short // attrSize
                    val attrCount = buffer.short.toInt() and 0xFFFF
                    buffer.short // idIndex
                    buffer.short // classIndex
                    buffer.short // styleIndex

                    val elemName = stringPool.getOrNull(nameIdx) ?: "unknown"
                    val attrs = mutableListOf<AxmlAttribute>()

                    for (i in 0 until attrCount) {
                        if (buffer.remaining() < 20) break
                        val attrNsIdx = buffer.int
                        val attrNameIdx = buffer.int
                        val attrRawValIdx = buffer.int
                        val typedValSize = buffer.short.toInt() and 0xFFFF
                        val typedValRes0 = buffer.get()
                        val typedValDataType = buffer.get().toInt() and 0xFF
                        val typedValData = buffer.int

                        var attrName = stringPool.getOrNull(attrNameIdx).orEmpty()
                        if (attrName.isEmpty() && attrNameIdx in resourceMap.indices) {
                            val resId = resourceMap[attrNameIdx]
                            attrName = KNOWN_ATTR_RES_IDS[resId] ?: "res_0x${Integer.toHexString(resId)}"
                        }

                        val attrNs = stringPool.getOrNull(attrNsIdx)
                        val attrVal = when (typedValDataType) {
                            0x03 -> stringPool.getOrNull(attrRawValIdx) ?: stringPool.getOrNull(typedValData) ?: ""
                            0x12 -> if (typedValData != 0) "true" else "false"
                            0x10 -> typedValData.toString()
                            0x11 -> "0x${Integer.toHexString(typedValData)}"
                            0x01 -> "@0x${Integer.toHexString(typedValData)}"
                            0x02 -> "?0x${Integer.toHexString(typedValData)}"
                            else -> stringPool.getOrNull(attrRawValIdx) ?: typedValData.toString()
                        }

                        val resId = if (attrNameIdx in resourceMap.indices) resourceMap[attrNameIdx] else null
                        attrs.add(AxmlAttribute(namespace = attrNs, name = attrName, value = attrVal, resourceId = resId))
                        rawAttributesMap["$elemName:$attrName"] = attrVal
                    }

                    val elem = MutableElement(name = elemName, attributes = attrs)
                    if (elementStack.isNotEmpty()) {
                        elementStack.last().children.add(elem)
                    } else {
                        rootElements.add(elem.toImmutable())
                    }
                    elementStack.addLast(elem)
                }
                CHUNK_END_ELEMENT -> {
                    if (elementStack.isNotEmpty()) {
                        val closed = elementStack.removeLast()
                        if (elementStack.isEmpty() && rootElements.none { it.name == closed.name }) {
                            rootElements.add(closed.toImmutable())
                        }
                    }
                }
                CHUNK_CDATA -> {
                    // CDATA chunk
                }
            }

            buffer.position(minOf(nextChunkPosition, buffer.limit()))
        }

        val root = rootElements.firstOrNull() ?: elementStack.firstOrNull()?.toImmutable()
        val xmlString = buildXmlString(root, namespaces)

        return ApkManifestData(
            formattedXml = xmlString,
            rootElement = root,
            rawAttributes = rawAttributesMap
        )
    }

    private fun parseStringPool(buffer: ByteBuffer, chunkSize: Int): List<String> {
        val poolStart = buffer.position() - 8
        val stringCount = buffer.int
        buffer.int // styleCount
        val flags = buffer.int
        val stringsStart = buffer.int
        buffer.int // stylesStart

        val isUtf8 = (flags and 0x0100) != 0
        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            offsets[i] = buffer.int
        }

        val stringDataAbsolute = poolStart + stringsStart
        val strings = ArrayList<String>(stringCount)

        for (i in 0 until stringCount) {
            val offset = offsets[i]
            val pos = stringDataAbsolute + offset
            if (pos >= buffer.limit()) {
                strings.add("")
                continue
            }
            buffer.position(pos)
            val str = if (isUtf8) {
                readUtf8String(buffer)
            } else {
                readUtf16String(buffer)
            }
            strings.add(str)
        }

        return strings
    }

    private fun readUtf8String(buffer: ByteBuffer): String {
        var charLen = buffer.get().toInt() and 0xFF
        if ((charLen and 0x80) != 0) {
            charLen = ((charLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        }
        var byteLen = buffer.get().toInt() and 0xFF
        if ((byteLen and 0x80) != 0) {
            byteLen = ((byteLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        }
        if (byteLen <= 0 || buffer.remaining() < byteLen) return ""
        val bytes = ByteArray(byteLen)
        buffer.get(bytes)
        return try {
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun readUtf16String(buffer: ByteBuffer): String {
        var charLen = buffer.short.toInt() and 0xFFFF
        if ((charLen and 0x8000) != 0) {
            charLen = ((charLen and 0x7FFF) shl 16) or (buffer.short.toInt() and 0xFFFF)
        }
        if (charLen <= 0 || buffer.remaining() < charLen * 2) return ""
        val chars = CharArray(charLen)
        for (i in 0 until charLen) {
            chars[i] = buffer.char
        }
        return String(chars)
    }

    private fun buildXmlString(element: AxmlElement?, namespaces: Map<String, String>): String {
        if (element == null) return "<!-- No manifest element found -->"
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        renderElement(sb, element, 0, isRoot = true)
        return sb.toString()
    }

    private fun renderElement(sb: StringBuilder, element: AxmlElement, indentLevel: Int, isRoot: Boolean = false) {
        val indent = "  ".repeat(indentLevel)
        sb.append(indent).append("<").append(element.name)

        if (isRoot) {
            sb.append("\n").append(indent).append("  xmlns:android=\"http://schemas.android.com/apk/res/android\"")
        }

        for (attr in element.attributes) {
            val prefix = if (attr.namespace?.contains("res/android") == true || attr.name in KNOWN_ATTR_RES_IDS.values) {
                "android:"
            } else ""
            sb.append("\n").append(indent).append("  ")
                .append(prefix).append(attr.name)
                .append("=\"").append(escapeXml(attr.value)).append("\"")
        }

        if (element.children.isEmpty()) {
            sb.append(" />\n")
        } else {
            sb.append(">\n")
            for (child in element.children) {
                renderElement(sb, child, indentLevel + 1)
            }
            sb.append(indent).append("</").append(element.name).append(">\n")
        }
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private class MutableElement(
        val name: String,
        val attributes: MutableList<AxmlAttribute> = mutableListOf(),
        val children: MutableList<MutableElement> = mutableListOf()
    ) {
        fun toImmutable(): AxmlElement {
            return AxmlElement(
                name = name,
                attributes = attributes.toList(),
                children = children.map { it.toImmutable() }
            )
        }
    }
}
