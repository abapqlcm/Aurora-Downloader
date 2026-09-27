package com.aurora.downloader.ui.screens.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notifDataStore: DataStore<Preferences> by preferencesDataStore(name = "notif_gate")

/**
 * Remembers the notification-permission decision so the gate appears once and
 * the user is not nagged on every launch.
 */
class NotificationPrefs(context: Context) {

    private object Keys {
        val DECIDED = booleanPreferencesKey("notif_decided")
        val GRANTED = booleanPreferencesKey("notif_granted")
    }

    private val ds = context.notifDataStore

    val decided: Flow<Boolean> = ds.data.map { it[Keys.DECIDED] ?: false }
    val granted: Flow<Boolean> = ds.data.map { it[Keys.GRANTED] ?: false }

    suspend fun markDecided(granted: Boolean) {
        ds.edit {
            it[Keys.DECIDED] = true
            it[Keys.GRANTED] = granted
        }
    }

    suspend fun reset() {
        ds.edit { it.clear() }
    }
}
