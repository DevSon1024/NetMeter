package com.devson.netmeter.core.data.speedtest

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.devson.netmeter.core.domain.model.SpeedTestRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface SpeedTestHistoryRepository {
    suspend fun saveTestResult(record: SpeedTestRecord): Long
    suspend fun getAllHistory(limit: Int = 50): List<SpeedTestRecord>
    fun getHistoryFlow(): Flow<List<SpeedTestRecord>>
    suspend fun clearHistory()
}

class SqliteSpeedTestHistoryRepository(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), SpeedTestHistoryRepository {

    private val _historyFlow = MutableStateFlow<List<SpeedTestRecord>>(emptyList())

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
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_NETWORK_TYPE TEXT NOT NULL,
                $COL_LATENCY_MS REAL NOT NULL,
                $COL_JITTER_MS REAL NOT NULL,
                $COL_DOWNLOAD_MBPS REAL NOT NULL,
                $COL_UPLOAD_MBPS REAL NOT NULL,
                $COL_TOTAL_BYTES INTEGER NOT NULL,
                $COL_SERVER_HOST TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    override suspend fun saveTestResult(record: SpeedTestRecord): Long = withContext(dispatcher) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TIMESTAMP, record.timestampMillis)
            put(COL_NETWORK_TYPE, record.networkType)
            put(COL_LATENCY_MS, record.latencyMs)
            put(COL_JITTER_MS, record.jitterMs)
            put(COL_DOWNLOAD_MBPS, record.downloadMbps)
            put(COL_UPLOAD_MBPS, record.uploadMbps)
            put(COL_TOTAL_BYTES, record.totalBytesConsumed)
            put(COL_SERVER_HOST, record.serverHost)
        }
        val insertedId = db.insert(TABLE_HISTORY, null, values)
        refreshCache()
        insertedId
    }

    override suspend fun getAllHistory(limit: Int): List<SpeedTestRecord> = withContext(dispatcher) {
        loadHistoryFromDb(limit)
    }

    override fun getHistoryFlow(): Flow<List<SpeedTestRecord>> = _historyFlow.asStateFlow()

    override suspend fun clearHistory() = withContext(dispatcher) {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, null, null)
        refreshCache()
    }

    private suspend fun refreshCache() {
        val items = loadHistoryFromDb(50)
        _historyFlow.value = items
    }

    private fun loadHistoryFromDb(limit: Int): List<SpeedTestRecord> {
        val db = readableDatabase
        val records = mutableListOf<SpeedTestRecord>()

        try {
            db.query(
                TABLE_HISTORY,
                null,
                null,
                null,
                null,
                null,
                "$COL_TIMESTAMP DESC",
                limit.toString()
            ).use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(COL_ID)
                val timeIdx = cursor.getColumnIndexOrThrow(COL_TIMESTAMP)
                val netIdx = cursor.getColumnIndexOrThrow(COL_NETWORK_TYPE)
                val latIdx = cursor.getColumnIndexOrThrow(COL_LATENCY_MS)
                val jitIdx = cursor.getColumnIndexOrThrow(COL_JITTER_MS)
                val downIdx = cursor.getColumnIndexOrThrow(COL_DOWNLOAD_MBPS)
                val upIdx = cursor.getColumnIndexOrThrow(COL_UPLOAD_MBPS)
                val bytesIdx = cursor.getColumnIndexOrThrow(COL_TOTAL_BYTES)
                val hostIdx = cursor.getColumnIndexOrThrow(COL_SERVER_HOST)

                while (cursor.moveToNext()) {
                    records.add(
                        SpeedTestRecord(
                            id = cursor.getLong(idIdx),
                            timestampMillis = cursor.getLong(timeIdx),
                            networkType = cursor.getString(netIdx),
                            latencyMs = cursor.getDouble(latIdx),
                            jitterMs = cursor.getDouble(jitIdx),
                            downloadMbps = cursor.getDouble(downIdx),
                            uploadMbps = cursor.getDouble(upIdx),
                            totalBytesConsumed = cursor.getLong(bytesIdx),
                            serverHost = cursor.getString(hostIdx)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Return collected records on read error
        }

        return records
    }

    companion object {
        const val DATABASE_NAME = "speed_test_history.db"
        const val DATABASE_VERSION = 1

        const val TABLE_HISTORY = "speed_test_history"
        const val COL_ID = "id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_NETWORK_TYPE = "network_type"
        const val COL_LATENCY_MS = "latency_ms"
        const val COL_JITTER_MS = "jitter_ms"
        const val COL_DOWNLOAD_MBPS = "download_mbps"
        const val COL_UPLOAD_MBPS = "upload_mbps"
        const val COL_TOTAL_BYTES = "total_bytes"
        const val COL_SERVER_HOST = "server_host"
    }
}
