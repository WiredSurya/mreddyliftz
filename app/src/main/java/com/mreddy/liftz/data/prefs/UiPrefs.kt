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
     * Defaults to ON now that cloud sync exists. It used to default to OFF, correctly: with no
     * network features at all, "you're offline" described a state that changed nothing. That is
     * no longer true — offline now means your logging still works but will not reach your other
     * devices until you reconnect, which is worth saying.
     */
    val showOfflineIndicator: Flow<Boolean> = context.uiPrefsDataStore.data.map { prefs ->
        prefs[KEY_SHOW_OFFLINE] ?: true
    }

    suspend fun setShowOfflineIndicator(show: Boolean) {
        context.uiPrefsDataStore.edit { it[KEY_SHOW_OFFLINE] = show }
    }

    /**
     * A version the user chose to skip, so "Not now" means not now rather than "ask me on every
     * single launch". Cleared automatically when a NEWER version than the skipped one appears.
     */
    val skippedUpdate: Flow<String?> = context.uiPrefsDataStore.data.map {
        it[KEY_SKIPPED_UPDATE]?.takeIf { v -> v.isNotBlank() }
    }

    suspend fun skipUpdate(version: String) = context.uiPrefsDataStore.edit {
        it[KEY_SKIPPED_UPDATE] = version
    }

    /** Epoch millis of the last GitHub check, so launches do not each cost a network round trip. */
    suspend fun lastUpdateCheck(): Long =
        context.uiPrefsDataStore.data.map { it[KEY_LAST_UPDATE_CHECK] ?: 0L }.first()

    suspend fun setLastUpdateCheck(atMs: Long) = context.uiPrefsDataStore.edit {
        it[KEY_LAST_UPDATE_CHECK] = atMs
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_SHOW_OFFLINE = booleanPreferencesKey("show_offline_indicator")
        val KEY_SKIPPED_UPDATE = stringPreferencesKey("skipped_update_version")
        val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
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

    /** Status that follows the chosen folder, so the UI updates the moment one is picked. */
    fun statusFlow(): Flow<com.mreddy.liftz.data.sync.SyncStatus> =
        context.uiPrefsDataStore.data.map { p ->
        val folder = p[KEY_BACKUP_FOLDER]?.takeIf { it.isNotBlank() }
        com.mreddy.liftz.data.sync.SyncStatus(
            backendName = if (folder != null) "your chosen folder" else "this device",
            backendAvailable = true,
            folderUri = folder,
            lastBackupAtMs = p[KEY_LAST_BACKUP]?.takeIf { it > 0 },
            lastRestoreAtMs = p[KEY_LAST_RESTORE]?.takeIf { it > 0 },
            lastError = p[KEY_LAST_ERROR]?.takeIf { it.isNotBlank() },
            isCloud = folder != null
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

    /**
     * Whether a signed-in account should sync to the cloud.
     *
     * Defaults to true: signing in is itself the opt-in, and an account that did not sync would
     * be a confusing thing to own. Turning it off keeps you signed in but sends the backup to
     * your chosen folder instead — which is the escape hatch for anyone who wants the account
     * without the upload.
     */
    val cloudSyncEnabled: Flow<Boolean> = context.uiPrefsDataStore.data.map {
        it[KEY_CLOUD_SYNC] ?: true
    }

    suspend fun cloudSyncEnabledOnce(): Boolean = cloudSyncEnabled.first()

    suspend fun setCloudSyncEnabled(enabled: Boolean) = context.uiPrefsDataStore.edit {
        it[KEY_CLOUD_SYNC] = enabled
    }

    /** SAF tree URI of the folder chosen for backups, or null if none has been picked. */
    val backupFolder: Flow<String?> = context.uiPrefsDataStore.data.map {
        it[KEY_BACKUP_FOLDER]?.takeIf { uri -> uri.isNotBlank() }
    }

    suspend fun backupFolderOnce(): String? = backupFolder.first()

    suspend fun setBackupFolder(uri: String?) = context.uiPrefsDataStore.edit {
        it[KEY_BACKUP_FOLDER] = uri.orEmpty()
    }

    private companion object {
        val KEY_LAST_BACKUP = longPreferencesKey("sync_last_backup")
        val KEY_LAST_RESTORE = longPreferencesKey("sync_last_restore")
        val KEY_LAST_ERROR = stringPreferencesKey("sync_last_error")
        val KEY_DEVICE_ID = stringPreferencesKey("sync_device_id")
        val KEY_BACKUP_FOLDER = stringPreferencesKey("sync_backup_folder")
        val KEY_CLOUD_SYNC = booleanPreferencesKey("sync_cloud_enabled")
    }
}
