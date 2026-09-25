package com.aurora.downloader.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * User-facing preferences. Concurrency is deliberately fixed (not adaptive):
 * per-connection rate limits on real servers make raising/lowering the part
 * count mid-download counterproductive and can trip anti-abuse blocks.
 */
data class AuroraSettings(
    val maxConcurrentDownloads: Int = 3,
    val partsPerDownload: Int = 8,
    val speedLimitEnabled: Boolean = false,
    val speedLimitKBps: Int = 0,
    val wifiOnly: Boolean = false,
    val customUserAgent: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

class SettingsRepository(private val context: Context) {

    private object Keys {
        val CONCURRENT = intPreferencesKey("max_concurrent_downloads")
        val PARTS = intPreferencesKey("parts_per_download")
        val LIMIT_ENABLED = booleanPreferencesKey("speed_limit_enabled")
        val LIMIT_KBPS = intPreferencesKey("speed_limit_kbps")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val THEME = intPreferencesKey("theme_mode")
    }

    val flow: Flow<AuroraSettings> = context.dataStore.data.map { p ->
        AuroraSettings(
            maxConcurrentDownloads = p[Keys.CONCURRENT] ?: 3,
            partsPerDownload = p[Keys.PARTS] ?: 8,
            speedLimitEnabled = p[Keys.LIMIT_ENABLED] ?: false,
            speedLimitKBps = p[Keys.LIMIT_KBPS] ?: 0,
            wifiOnly = p[Keys.WIFI_ONLY] ?: false,
            themeMode = ThemeMode.entries.getOrElse(p[Keys.THEME] ?: 2) { ThemeMode.SYSTEM }
        )
    }

    suspend fun setConcurrent(value: Int) = context.dataStore.edit { it[Keys.CONCURRENT] = value }
    suspend fun setParts(value: Int) = context.dataStore.edit { it[Keys.PARTS] = value }
    suspend fun setSpeedLimit(enabled: Boolean, kbps: Int) = context.dataStore.edit {
        it[Keys.LIMIT_ENABLED] = enabled
        it[Keys.LIMIT_KBPS] = kbps
    }
    suspend fun setWifiOnly(value: Boolean) = context.dataStore.edit { it[Keys.WIFI_ONLY] = value }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.ordinal }
}
