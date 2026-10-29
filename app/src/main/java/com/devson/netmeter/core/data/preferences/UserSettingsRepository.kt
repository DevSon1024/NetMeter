package com.devson.netmeter.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.devson.netmeter.core.theme.ThemeMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "netmeter_user_settings")

/**
 * Immutable domain model representing user-configurable application preferences.
 */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val quotaEnabled: Boolean = false,
    val mobileQuotaBytes: Long = 0L,
    val warningThresholdPercent: Int = 80
) {
    val mobileQuotaMb: Long
        get() = mobileQuotaBytes / (1024L * 1024L)

    val mobileQuotaGb: Double
        get() = mobileQuotaBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)

    fun isThresholdTriggered(usedBytes: Long): Boolean {
        if (!quotaEnabled || mobileQuotaBytes <= 0L) return false
        val thresholdBytes = (mobileQuotaBytes * warningThresholdPercent) / 100L
        return usedBytes >= thresholdBytes
    }
}

/**
 * Interface contract for persisting and retrieving user preferences.
 */
interface UserSettingsRepository {
    val settingsFlow: Flow<UserSettings>
    suspend fun setThemeMode(themeMode: ThemeMode)
    suspend fun setQuotaSettings(enabled: Boolean, quotaBytes: Long, warningThresholdPercent: Int)
}

/**
 * Production DataStore-backed repository implementation.
 */
class DataStoreUserSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : UserSettingsRepository {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val QUOTA_ENABLED = booleanPreferencesKey("mobile_quota_enabled")
        val MOBILE_QUOTA_BYTES = longPreferencesKey("mobile_quota_bytes")
        val WARNING_THRESHOLD_PERCENT = intPreferencesKey("warning_threshold_percent")
    }

    override val settingsFlow: Flow<UserSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeModeName = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
            val themeMode = try {
                ThemeMode.valueOf(themeModeName)
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            }

            val quotaEnabled = preferences[PreferencesKeys.QUOTA_ENABLED] ?: false
            val quotaBytes = (preferences[PreferencesKeys.MOBILE_QUOTA_BYTES] ?: 0L).coerceAtLeast(0L)
            val threshold = (preferences[PreferencesKeys.WARNING_THRESHOLD_PERCENT] ?: 80).coerceIn(1, 100)

            UserSettings(
                themeMode = themeMode,
                quotaEnabled = quotaEnabled,
                mobileQuotaBytes = quotaBytes,
                warningThresholdPercent = threshold
            )
        }
        .flowOn(dispatcher)

    override suspend fun setThemeMode(themeMode: ThemeMode): Unit = withContext(dispatcher) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    override suspend fun setQuotaSettings(
        enabled: Boolean,
        quotaBytes: Long,
        warningThresholdPercent: Int
    ): Unit = withContext(dispatcher) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.QUOTA_ENABLED] = enabled
            preferences[PreferencesKeys.MOBILE_QUOTA_BYTES] = quotaBytes.coerceAtLeast(0L)
            preferences[PreferencesKeys.WARNING_THRESHOLD_PERCENT] = warningThresholdPercent.coerceIn(1, 100)
        }
    }
}
