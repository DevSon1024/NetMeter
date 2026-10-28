package com.devson.netmeter.core.data.snapshot

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.devson.netmeter.core.domain.model.DailyDataStatus
import com.devson.netmeter.core.domain.model.DailyUsageRecord
import com.devson.netmeter.core.domain.model.HistoryDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface HistorySnapshotStore {
    suspend fun getSnapshot(dateString: String): DailyUsageRecord?
    suspend fun saveSnapshot(record: DailyUsageRecord)
    suspend fun getAllSnapshots(): List<DailyUsageRecord>
    suspend fun clearSnapshots()
}

class SqliteHistorySnapshotStore(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), HistorySnapshotStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SNAPSHOTS (
                $COL_DATE_KEY TEXT PRIMARY KEY,
                $COL_DISPLAY_LABEL TEXT NOT NULL,
                $COL_SHORT_LABEL TEXT NOT NULL,
                $COL_START_MILLIS INTEGER NOT NULL,
                $COL_END_MILLIS INTEGER NOT NULL,
                $COL_RX_BYTES INTEGER NOT NULL,
                $COL_TX_BYTES INTEGER NOT NULL,
                $COL_WIFI_RX_BYTES INTEGER NOT NULL,
                $COL_WIFI_TX_BYTES INTEGER NOT NULL,
                $COL_MOBILE_RX_BYTES INTEGER NOT NULL,
                $COL_MOBILE_TX_BYTES INTEGER NOT NULL,
                $COL_STATUS TEXT NOT NULL,
                $COL_CAPTURED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SNAPSHOTS")
        onCreate(db)
    }

    override suspend fun getSnapshot(dateString: String): DailyUsageRecord? = withContext(dispatcher) {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SNAPSHOTS,
            null,
            "$COL_DATE_KEY = ?",
            arrayOf(dateString),
            null,
            null,
            null
        )

        cursor.use {
            if (it.moveToFirst()) {
                val start = it.getLong(it.getColumnIndexOrThrow(COL_START_MILLIS))
                val end = it.getLong(it.getColumnIndexOrThrow(COL_END_MILLIS))
                val rx = it.getLong(it.getColumnIndexOrThrow(COL_RX_BYTES))
                val tx = it.getLong(it.getColumnIndexOrThrow(COL_TX_BYTES))
                val wifiRx = it.getLong(it.getColumnIndexOrThrow(COL_WIFI_RX_BYTES))
                val wifiTx = it.getLong(it.getColumnIndexOrThrow(COL_WIFI_TX_BYTES))
                val mobileRx = it.getLong(it.getColumnIndexOrThrow(COL_MOBILE_RX_BYTES))
                val mobileTx = it.getLong(it.getColumnIndexOrThrow(COL_MOBILE_TX_BYTES))
                val display = it.getString(it.getColumnIndexOrThrow(COL_DISPLAY_LABEL))
                val short = it.getString(it.getColumnIndexOrThrow(COL_SHORT_LABEL))
                val statusStr = it.getString(it.getColumnIndexOrThrow(COL_STATUS))
                val status = try {
                    DailyDataStatus.valueOf(statusStr)
                } catch (_: Exception) {
                    DailyDataStatus.COMPLETE
                }

                DailyUsageRecord(
                    dateString = dateString,
                    displayLabel = display,
                    shortLabel = short,
                    startMillis = start,
                    endMillis = end,
                    rxBytes = rx,
                    txBytes = tx,
                    wifiRxBytes = wifiRx,
                    wifiTxBytes = wifiTx,
                    mobileRxBytes = mobileRx,
                    mobileTxBytes = mobileTx,
                    status = status,
                    dataSource = HistoryDataSource.LOCAL_SNAPSHOT
                )
            } else {
                null
            }
        }
    }

    override suspend fun saveSnapshot(record: DailyUsageRecord): Unit = withContext(dispatcher) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_DATE_KEY, record.dateString)
            put(COL_DISPLAY_LABEL, record.displayLabel)
            put(COL_SHORT_LABEL, record.shortLabel)
            put(COL_START_MILLIS, record.startMillis)
            put(COL_END_MILLIS, record.endMillis)
            put(COL_RX_BYTES, record.rxBytes)
            put(COL_TX_BYTES, record.txBytes)
            put(COL_WIFI_RX_BYTES, record.wifiRxBytes)
            put(COL_WIFI_TX_BYTES, record.wifiTxBytes)
            put(COL_MOBILE_RX_BYTES, record.mobileRxBytes)
            put(COL_MOBILE_TX_BYTES, record.mobileTxBytes)
            put(COL_STATUS, record.status.name)
            put(COL_CAPTURED_AT, System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_SNAPSHOTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override suspend fun getAllSnapshots(): List<DailyUsageRecord> = withContext(dispatcher) {
        val records = mutableListOf<DailyUsageRecord>()
        val db = readableDatabase
        val cursor = db.query(TABLE_SNAPSHOTS, null, null, null, null, null, "$COL_START_MILLIS ASC")
        cursor.use {
            while (it.moveToNext()) {
                val date = it.getString(it.getColumnIndexOrThrow(COL_DATE_KEY))
                val start = it.getLong(it.getColumnIndexOrThrow(COL_START_MILLIS))
                val end = it.getLong(it.getColumnIndexOrThrow(COL_END_MILLIS))
                val rx = it.getLong(it.getColumnIndexOrThrow(COL_RX_BYTES))
                val tx = it.getLong(it.getColumnIndexOrThrow(COL_TX_BYTES))
                val wifiRx = it.getLong(it.getColumnIndexOrThrow(COL_WIFI_RX_BYTES))
                val wifiTx = it.getLong(it.getColumnIndexOrThrow(COL_WIFI_TX_BYTES))
                val mobileRx = it.getLong(it.getColumnIndexOrThrow(COL_MOBILE_RX_BYTES))
                val mobileTx = it.getLong(it.getColumnIndexOrThrow(COL_MOBILE_TX_BYTES))
                val display = it.getString(it.getColumnIndexOrThrow(COL_DISPLAY_LABEL))
                val short = it.getString(it.getColumnIndexOrThrow(COL_SHORT_LABEL))
                val statusStr = it.getString(it.getColumnIndexOrThrow(COL_STATUS))
                val status = try {
                    DailyDataStatus.valueOf(statusStr)
                } catch (_: Exception) {
                    DailyDataStatus.COMPLETE
                }

                records.add(
                    DailyUsageRecord(
                        dateString = date,
                        displayLabel = display,
                        shortLabel = short,
                        startMillis = start,
                        endMillis = end,
                        rxBytes = rx,
                        txBytes = tx,
                        wifiRxBytes = wifiRx,
                        wifiTxBytes = wifiTx,
                        mobileRxBytes = mobileRx,
                        mobileTxBytes = mobileTx,
                        status = status,
                        dataSource = HistoryDataSource.LOCAL_SNAPSHOT
                    )
                )
            }
        }
        records
    }

    override suspend fun clearSnapshots(): Unit = withContext(dispatcher) {
        writableDatabase.delete(TABLE_SNAPSHOTS, null, null)
    }

    companion object {
        private const val DATABASE_NAME = "netmeter_history.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_SNAPSHOTS = "history_snapshots"

        private const val COL_DATE_KEY = "date_key"
        private const val COL_DISPLAY_LABEL = "display_label"
        private const val COL_SHORT_LABEL = "short_label"
        private const val COL_START_MILLIS = "start_millis"
        private const val COL_END_MILLIS = "end_millis"
        private const val COL_RX_BYTES = "rx_bytes"
        private const val COL_TX_BYTES = "tx_bytes"
        private const val COL_WIFI_RX_BYTES = "wifi_rx_bytes"
        private const val COL_WIFI_TX_BYTES = "wifi_tx_bytes"
        private const val COL_MOBILE_RX_BYTES = "mobile_rx_bytes"
        private const val COL_MOBILE_TX_BYTES = "mobile_tx_bytes"
        private const val COL_STATUS = "status"
        private const val COL_CAPTURED_AT = "captured_at"
    }
}
