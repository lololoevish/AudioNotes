package com.audionotes.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.audionotes.app.BuildConfig
import com.audionotes.app.data.remote.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "audionotes_settings")

/** Настройки приложения: тема и параметры LLM-провайдера. */
class SettingsRepository(private val context: Context) {

    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val providerKey = stringPreferencesKey("llm_provider")
    private val apiKeyKey = stringPreferencesKey("llm_api_key")
    private val modelKey = stringPreferencesKey("llm_model")

    val darkTheme: Flow<Boolean> = context.dataStore.data.map { it[darkThemeKey] ?: false }

    /** Выбранный провайдер. */
    val provider: Flow<LlmProvider> = context.dataStore.data.map {
        LlmProvider.fromId(it[providerKey])
    }

    /** Ключ, введённый пользователем в настройках; иначе — из local.properties. */
    val apiKey: Flow<String> = context.dataStore.data.map {
        it[apiKeyKey].orEmpty().ifBlank { BuildConfig.LLM_API_KEY }
    }

    /** Модель; пусто — значит используется модель по умолчанию провайдера. */
    val model: Flow<String> = context.dataStore.data.map {
        it[modelKey].orEmpty().ifBlank { BuildConfig.LLM_MODEL }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[darkThemeKey] = enabled }
    }

    suspend fun setProvider(provider: LlmProvider) {
        context.dataStore.edit { it[providerKey] = provider.id }
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[apiKeyKey] = key.trim() }
    }

    suspend fun setModel(model: String) {
        context.dataStore.edit { it[modelKey] = model.trim() }
    }
}
