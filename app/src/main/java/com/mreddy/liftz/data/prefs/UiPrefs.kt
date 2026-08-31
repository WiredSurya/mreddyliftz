package com.mreddy.liftz.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
