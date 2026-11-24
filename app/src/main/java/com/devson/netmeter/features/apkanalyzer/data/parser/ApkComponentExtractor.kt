package com.devson.netmeter.features.apkanalyzer.data.parser

import android.content.pm.PackageInfo
import android.os.Build
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkComponent
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkComponentType
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkComponentsSummary
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkIntentFilter
import com.devson.netmeter.features.apkanalyzer.domain.model.AxmlElement

/**
 * Extracts and normalizes declared Android application components from PackageInfo and binary XML.
 */
class ApkComponentExtractor {

    fun extract(packageInfo: PackageInfo?, manifestRoot: AxmlElement?): Pair<List<ApkComponent>, ApkComponentsSummary> {
        val components = mutableListOf<ApkComponent>()
        val packageName = packageInfo?.packageName.orEmpty()

        // 1. Map intent filters from manifest XML tree if available
        val intentFilterMap = mutableMapOf<String, MutableList<ApkIntentFilter>>()
        if (manifestRoot != null) {
            extractIntentFiltersFromXml(manifestRoot, intentFilterMap, packageName)
        }

        // 2. Extract Activities
        packageInfo?.activities?.forEach { act ->
            val className = act.name.orEmpty()
            val shortName = className.substringAfterLast('.')
            val intentFilters = intentFilterMap[className] ?: emptyList()
            components.add(
                ApkComponent(
                    name = shortName,
                    className = className,
                    type = ApkComponentType.ACTIVITY,
                    isExported = act.exported,
                    isEnabled = act.enabled,
                    permission = act.permission,
                    process = act.processName,
                    isDirectBootAware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) act.directBootAware else false,
                    intentFilters = intentFilters
                )
            )
        }

        // 3. Extract Services
        packageInfo?.services?.forEach { srv ->
            val className = srv.name.orEmpty()
            val shortName = className.substringAfterLast('.')
            val intentFilters = intentFilterMap[className] ?: emptyList()
            components.add(
                ApkComponent(
                    name = shortName,
                    className = className,
                    type = ApkComponentType.SERVICE,
                    isExported = srv.exported,
                    isEnabled = srv.enabled,
                    permission = srv.permission,
                    process = srv.processName,
                    isDirectBootAware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) srv.directBootAware else false,
                    intentFilters = intentFilters
                )
            )
        }

        // 4. Extract Broadcast Receivers
        packageInfo?.receivers?.forEach { rec ->
            val className = rec.name.orEmpty()
            val shortName = className.substringAfterLast('.')
            val intentFilters = intentFilterMap[className] ?: emptyList()
            components.add(
                ApkComponent(
                    name = shortName,
                    className = className,
                    type = ApkComponentType.RECEIVER,
                    isExported = rec.exported,
                    isEnabled = rec.enabled,
                    permission = rec.permission,
                    process = rec.processName,
                    isDirectBootAware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) rec.directBootAware else false,
                    intentFilters = intentFilters
                )
            )
        }

        // 5. Extract Content Providers
        packageInfo?.providers?.forEach { prov ->
            val className = prov.name.orEmpty()
            val shortName = className.substringAfterLast('.')
            val intentFilters = intentFilterMap[className] ?: emptyList()
            components.add(
                ApkComponent(
                    name = shortName,
                    className = className,
                    type = ApkComponentType.PROVIDER,
                    isExported = prov.exported,
                    isEnabled = prov.enabled,
                    permission = prov.readPermission ?: prov.writePermission,
                    process = prov.processName,
                    isDirectBootAware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) prov.directBootAware else false,
                    intentFilters = intentFilters
                )
            )
        }

        // 6. If PackageInfo was missing components (e.g. reflection / restricted environment), fallback to XML nodes
        if (components.isEmpty() && manifestRoot != null) {
            extractComponentsFromXml(manifestRoot, components, packageName)
        }

        val activitiesCount = components.count { it.type == ApkComponentType.ACTIVITY }
        val servicesCount = components.count { it.type == ApkComponentType.SERVICE }
        val receiversCount = components.count { it.type == ApkComponentType.RECEIVER }
        val providersCount = components.count { it.type == ApkComponentType.PROVIDER }

        val exportedCount = components.count { it.isExported }
        val exportedWithoutPermission = components.count { it.isExported && it.permission.isNullOrBlank() }

        val summary = ApkComponentsSummary(
            activitiesCount = activitiesCount,
            servicesCount = servicesCount,
            receiversCount = receiversCount,
            providersCount = providersCount,
            exportedCount = exportedCount,
            exportedWithoutPermissionCount = exportedWithoutPermission
        )

        return Pair(components, summary)
    }

    private fun extractIntentFiltersFromXml(
        element: AxmlElement,
        destination: MutableMap<String, MutableList<ApkIntentFilter>>,
        packageName: String
    ) {
        val appElem = findChild(element, "application") ?: element
        for (child in appElem.children) {
            if (child.name in setOf("activity", "service", "receiver", "provider", "activity-alias")) {
                var compName = child.attributes.find { it.name == "name" }?.value.orEmpty()
                if (compName.startsWith(".")) {
                    compName = "$packageName$compName"
                }

                val filters = mutableListOf<ApkIntentFilter>()
                for (filterElem in child.children.filter { it.name == "intent-filter" }) {
                    val actions = filterElem.children
                        .filter { it.name == "action" }
                        .mapNotNull { it.attributes.find { a -> a.name == "name" }?.value }
                    val categories = filterElem.children
                        .filter { it.name == "category" }
                        .mapNotNull { it.attributes.find { c -> c.name == "name" }?.value }
                    val dataSchemes = filterElem.children
                        .filter { it.name == "data" }
                        .mapNotNull { it.attributes.find { d -> d.name == "scheme" }?.value }

                    filters.add(ApkIntentFilter(actions, categories, dataSchemes))
                }
                if (filters.isNotEmpty()) {
                    destination.getOrPut(compName) { mutableListOf() }.addAll(filters)
                }
            }
        }
    }

    private fun extractComponentsFromXml(
        element: AxmlElement,
        destination: MutableList<ApkComponent>,
        packageName: String
    ) {
        val appElem = findChild(element, "application") ?: element
        for (child in appElem.children) {
            val type = when (child.name) {
                "activity", "activity-alias" -> ApkComponentType.ACTIVITY
                "service" -> ApkComponentType.SERVICE
                "receiver" -> ApkComponentType.RECEIVER
                "provider" -> ApkComponentType.PROVIDER
                else -> null
            } ?: continue

            var compName = child.attributes.find { it.name == "name" }?.value.orEmpty()
            if (compName.startsWith(".")) {
                compName = "$packageName$compName"
            }
            val shortName = compName.substringAfterLast('.')
            val isExported = child.attributes.find { it.name == "exported" }?.value?.equals("true", ignoreCase = true)
                ?: child.children.any { it.name == "intent-filter" }
            val isEnabled = child.attributes.find { it.name == "enabled" }?.value?.equals("false", ignoreCase = true) != true
            val permission = child.attributes.find { it.name == "permission" }?.value
            val process = child.attributes.find { it.name == "process" }?.value
            val directBoot = child.attributes.find { it.name == "directBootAware" }?.value?.equals("true", ignoreCase = true) == true

            destination.add(
                ApkComponent(
                    name = shortName,
                    className = compName,
                    type = type,
                    isExported = isExported,
                    isEnabled = isEnabled,
                    permission = permission,
                    process = process,
                    isDirectBootAware = directBoot,
                    intentFilters = emptyList()
                )
            )
        }
    }

    private fun findChild(element: AxmlElement, name: String): AxmlElement? {
        if (element.name == name) return element
        for (child in element.children) {
            val found = findChild(child, name)
            if (found != null) return found
        }
        return null
    }
}
