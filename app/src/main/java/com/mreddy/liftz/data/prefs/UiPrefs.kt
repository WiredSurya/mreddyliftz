package com.mreddy.liftz.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Light / dark / follow the phone. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.uiPrefsDataStore: androidx.datastore.core.DataStore<Preferences> by
    preferencesDataStore(name = "ui_prefs")

/**
 * UI chrome preferences — deliberately NOT in Room.
 *
 * The "Room is the only source of truth" rule in HANDOFF is about training and macro data: the
 * stuff that has to survive, export to JSON, and stay consistent with the widget. A theme choice
 * is none of those things. Keeping it in DataStore avoids a schema migration for a cosmetic
 * setting and keeps the exported JSON free of device-local display preferences.
 */
class UiPrefs(private val context: Context) {

    val themeMode: Flow<ThemeMode> = context.uiPrefsDataStore.data.map { prefs ->
        when (prefs[KEY_THEME_MODE]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.uiPrefsDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    /**
     * Whether to show the offline banner and pulsing indicator.
     *
     * Defaults to OFF, and that is deliberate rather than an oversight. The app currently has no
     * cloud features at all — it does not even hold the INTERNET permission — so telling someone
     * "you're offline" would be describing a state that changes nothing about what they can do.
     * The UI is built and wired so it is ready the day sync lands; until then it is opt-in from
     * Settings for anyone who wants to see it. Flip the default here when sync ships.
     */
    val showOfflineIndicator: Flow<Boolean> = context.uiPrefsDataStore.data.map { prefs ->
        prefs[KEY_SHOW_OFFLINE] ?: false
    }

    suspend fun setShowOfflineIndicator(show: Boolean) {
        context.uiPrefsDataStore.edit { it[KEY_SHOW_OFFLINE] = show }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_SHOW_OFFLINE = booleanPreferencesKey("show_offline_indicator")
    }
}

/* ------------------------------------------------------------------------------------------
 * SYNC STATE
 * ---------------------------------------------------------------------------------------- */

/**
 * Backup/restore bookkeeping. Kept in DataStore rather than Room for the same reason the theme
 * is: it is device-local operational state, not training data, and it must never end up inside an
 * exported snapshot (which would make every backup contain the history of previous backups).
 */
class SyncPrefs(private val context: Context) {

    fun status(
        backendName: String,
        isLocalOnly: Boolean
    ): Flow<com.mreddy.liftz.data.sync.SyncStatus> = context.uiPrefsDataStore.data.map { p ->
        com.mreddy.liftz.data.sync.SyncStatus(
            backendName = backendName,
            backendAvailable = true,
            lastBackupAtMs = p[KEY_LAST_BACKUP]?.takeIf { it > 0 },
            lastRestoreAtMs = p[KEY_LAST_RESTORE]?.takeIf { it > 0 },
            lastError = p[KEY_LAST_ERROR]?.takeIf { it.isNotBlank() },
            isCloud = !isLocalOnly
        )
    }

    suspend fun recordBackup(atMs: Long) = context.uiPrefsDataStore.edit {
        it[KEY_LAST_BACKUP] = atMs
        it[KEY_LAST_ERROR] = ""
    }

    suspend fun recordRestore(atMs: Long) = context.uiPrefsDataStore.edit {
        it[KEY_LAST_RESTORE] = atMs
        it[KEY_LAST_ERROR] = ""
    }

    suspend fun recordError(message: String) = context.uiPrefsDataStore.edit {
        it[KEY_LAST_ERROR] = message
    }

    suspend fun deviceId(): String? =
        context.uiPrefsDataStore.data.map { it[KEY_DEVICE_ID] }.first()

    suspend fun setDeviceId(id: String) = context.uiPrefsDataStore.edit {
        it[KEY_DEVICE_ID] = id
    }

    private companion object {
        val KEY_LAST_BACKUP = longPreferencesKey("sync_last_backup")
        val KEY_LAST_RESTORE = longPreferencesKey("sync_last_restore")
        val KEY_LAST_ERROR = stringPreferencesKey("sync_last_error")
        val KEY_DEVICE_ID = stringPreferencesKey("sync_device_id")
    }
}
