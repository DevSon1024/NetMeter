package com.devson.netmeter.features.apkanalyzer.domain.model

data class AxmlAttribute(
    val namespace: String?,
    val name: String,
    val value: String,
    val resourceId: Int? = null
)

data class AxmlElement(
    val name: String,
    val attributes: List<AxmlAttribute> = emptyList(),
    val children: List<AxmlElement> = emptyList()
)

data class ApkManifestData(
    val formattedXml: String,
    val rootElement: AxmlElement? = null,
    val rawAttributes: Map<String, String> = emptyMap()
)
