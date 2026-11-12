package com.audionotes.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "audionotes_settings")

/** Настройки приложения (тема). */
class SettingsRepository(private val context: Context) {

    private val darkThemeKey = booleanPreferencesKey("dark_theme")

    val darkTheme: Flow<Boolean> = context.dataStore.data.map { it[darkThemeKey] ?: false }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[darkThemeKey] = enabled }
    }
}
