package com.devson.netmeter.core.domain.repository

import com.devson.netmeter.core.domain.model.AppDetailInfo
import com.devson.netmeter.core.domain.model.InstalledApp

interface AppInfoRepository {
    suspend fun getInstalledApps(): Result<List<InstalledApp>>
    suspend fun getAppDetails(packageName: String): Result<AppDetailInfo>
}
