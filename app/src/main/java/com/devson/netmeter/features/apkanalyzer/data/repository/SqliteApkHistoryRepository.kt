package com.devson.netmeter.features.apkanalyzer.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkHistoryItem
import com.devson.netmeter.features.apkanalyzer.domain.repository.ApkHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SqliteApkHistoryRepository(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), ApkHistoryRepository {

    private val _historyFlow = MutableStateFlow<List<ApkHistoryItem>>(emptyList())

    init {
        CoroutineScope(dispatcher).launch {
            refreshCache()
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_HISTORY (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FILE_NAME TEXT NOT NULL,
                $COL_PACKAGE_NAME TEXT NOT NULL,
                $COL_APP_NAME TEXT NOT NULL,
                $COL_VERSION_NAME TEXT,
                $COL_VERSION_CODE INTEGER NOT NULL,
                $COL_FILE_SIZE_BYTES INTEGER NOT NULL,
                $COL_ANALYZED_AT INTEGER NOT NULL,
                $COL_TOTAL_PERMISSIONS INTEGER NOT NULL,
                $COL_DANGEROUS_PERMISSIONS INTEGER NOT NULL,
                $COL_EXPORTED_COMPONENTS INTEGER NOT NULL,
                $COL_SECURITY_WARNINGS INTEGER NOT NULL,
                $COL_HIGH_RISK_FINDINGS INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    override suspend fun saveHistory(item: ApkHistoryItem): Long = withContext(dispatcher) {
        val values = ContentValues().apply {
            put(COL_FILE_NAME, item.fileName)
            put(COL_PACKAGE_NAME, item.packageName)
            put(COL_APP_NAME, item.appName)
            put(COL_VERSION_NAME, item.versionName)
            put(COL_VERSION_CODE, item.versionCode)
            put(COL_FILE_SIZE_BYTES, item.fileSizeBytes)
            put(COL_ANALYZED_AT, item.analyzedAt)
            put(COL_TOTAL_PERMISSIONS, item.totalPermissions)
            put(COL_DANGEROUS_PERMISSIONS, item.dangerousPermissions)
            put(COL_EXPORTED_COMPONENTS, item.exportedComponents)
            put(COL_SECURITY_WARNINGS, item.securityWarnings)
            put(COL_HIGH_RISK_FINDINGS, item.highRiskFindings)
        }

        val id = writableDatabase.insert(TABLE_HISTORY, null, values)
        refreshCache()
        id
    }

    override suspend fun getAllHistory(): List<ApkHistoryItem> = withContext(dispatcher) {
        queryHistory()
    }

    override fun getHistoryFlow(): Flow<List<ApkHistoryItem>> = _historyFlow.asStateFlow()

    override suspend fun deleteHistoryItem(id: Long) = withContext(dispatcher) {
        writableDatabase.delete(TABLE_HISTORY, "$COL_ID = ?", arrayOf(id.toString()))
        refreshCache()
        Unit
    }

    override suspend fun clearHistory() = withContext(dispatcher) {
        writableDatabase.delete(TABLE_HISTORY, null, null)
        refreshCache()
        Unit
    }

    private suspend fun refreshCache() {
        val list = queryHistory()
        _historyFlow.value = list
    }

    private fun queryHistory(): List<ApkHistoryItem> {
        val items = mutableListOf<ApkHistoryItem>()
        val cursor = readableDatabase.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COL_ANALYZED_AT DESC",
            "100"
        )
        cursor.use { c ->
            val idxId = c.getColumnIndexOrThrow(COL_ID)
            val idxFileName = c.getColumnIndexOrThrow(COL_FILE_NAME)
            val idxPkg = c.getColumnIndexOrThrow(COL_PACKAGE_NAME)
            val idxAppName = c.getColumnIndexOrThrow(COL_APP_NAME)
            val idxVerName = c.getColumnIndexOrThrow(COL_VERSION_NAME)
            val idxVerCode = c.getColumnIndexOrThrow(COL_VERSION_CODE)
            val idxSize = c.getColumnIndexOrThrow(COL_FILE_SIZE_BYTES)
            val idxTime = c.getColumnIndexOrThrow(COL_ANALYZED_AT)
            val idxTotalPerms = c.getColumnIndexOrThrow(COL_TOTAL_PERMISSIONS)
            val idxDangPerms = c.getColumnIndexOrThrow(COL_DANGEROUS_PERMISSIONS)
            val idxExpComp = c.getColumnIndexOrThrow(COL_EXPORTED_COMPONENTS)
            val idxSecWarn = c.getColumnIndexOrThrow(COL_SECURITY_WARNINGS)
            val idxHighRisk = c.getColumnIndexOrThrow(COL_HIGH_RISK_FINDINGS)

            while (c.moveToNext()) {
                items.add(
                    ApkHistoryItem(
                        id = c.getLong(idxId),
                        fileName = c.getString(idxFileName),
                        packageName = c.getString(idxPkg),
                        appName = c.getString(idxAppName),
                        versionName = c.getString(idxVerName),
                        versionCode = c.getLong(idxVerCode),
                        fileSizeBytes = c.getLong(idxSize),
                        analyzedAt = c.getLong(idxTime),
                        totalPermissions = c.getInt(idxTotalPerms),
                        dangerousPermissions = c.getInt(idxDangPerms),
                        exportedComponents = c.getInt(idxExpComp),
                        securityWarnings = c.getInt(idxSecWarn),
                        highRiskFindings = c.getInt(idxHighRisk)
                    )
                )
            }
        }
        return items
    }

    companion object {
        const val DATABASE_NAME = "apk_analyzer_history.db"
        const val DATABASE_VERSION = 1
        const val TABLE_HISTORY = "apk_analysis_history"

        const val COL_ID = "id"
        const val COL_FILE_NAME = "file_name"
        const val COL_PACKAGE_NAME = "package_name"
        const val COL_APP_NAME = "app_name"
        const val COL_VERSION_NAME = "version_name"
        const val COL_VERSION_CODE = "version_code"
        const val COL_FILE_SIZE_BYTES = "file_size_bytes"
        const val COL_ANALYZED_AT = "analyzed_at"
        const val COL_TOTAL_PERMISSIONS = "total_permissions"
        const val COL_DANGEROUS_PERMISSIONS = "dangerous_permissions"
        const val COL_EXPORTED_COMPONENTS = "exported_components"
        const val COL_SECURITY_WARNINGS = "security_warnings"
        const val COL_HIGH_RISK_FINDINGS = "high_risk_findings"
    }
}
