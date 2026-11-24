package com.devson.netmeter.features.apkanalyzer.domain.repository

import com.devson.netmeter.features.apkanalyzer.domain.model.ApkHistoryItem
import kotlinx.coroutines.flow.Flow

interface ApkHistoryRepository {
    suspend fun saveHistory(item: ApkHistoryItem): Long
    suspend fun getAllHistory(): List<ApkHistoryItem>
    fun getHistoryFlow(): Flow<List<ApkHistoryItem>>
    suspend fun deleteHistoryItem(id: Long)
    suspend fun clearHistory()
}
