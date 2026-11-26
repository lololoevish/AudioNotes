package com.audionotes.app.core

import android.content.Context
import com.audionotes.app.BuildConfig
import com.audionotes.app.data.RecordingRepository
import com.audionotes.app.data.llm.SummaryGenerator
import com.audionotes.app.data.local.AudioNotesDatabase
import com.audionotes.app.data.remote.GroqApi
import com.audionotes.app.data.remote.LlmProvider
import com.audionotes.app.settings.SettingsRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Простой контейнер зависимостей: для MVP без DI-фреймворка.
 */
object ServiceLocator {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .build()
    }

    fun repository(context: Context): RecordingRepository =
        RecordingRepository(AudioNotesDatabase.get(context).recordingDao())

    /**
     * Создаёт генератор конспекта для выбранного провайдера.
     * Ключ передаётся в заголовке Authorization, базовый URL берётся у провайдера.
     *
     * [endpoint] — для Cloudflare это Account ID (подставляется в шаблон URL),
     * для остальных — необязательное переопределение базового URL.
     */
    fun summaryGenerator(
        provider: LlmProvider,
        apiKey: String,
        model: String,
        endpoint: String = ""
    ): SummaryGenerator {
        val client = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                // У своего сервера ключ обычно не нужен — заголовок не мешает.
                if (apiKey.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $apiKey")
                }
                chain.proceed(builder.build())
            }
            .build()

        val api = Retrofit.Builder()
            .baseUrl(resolveBaseUrl(provider, endpoint))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GroqApi::class.java)

        return SummaryGenerator(api, provider, apiKey, model)
    }

    /**
     * Загружает список моделей с OpenAI-совместимого сервера.
     *
     * Нужен, чтобы узнать точное название модели (например, `qwen2.5:7b` у Ollama)
     * и не гадать его в настройках.
     */
    suspend fun listModels(endpoint: String, apiKey: String = ""): List<String> {
        val baseUrl = normalizeBaseUrl(endpoint)
        if (baseUrl.isEmpty()) throw IllegalArgumentException("Не указан адрес сервера")

        val client = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                if (apiKey.isNotBlank()) builder.addHeader("Authorization", "Bearer $apiKey")
                chain.proceed(builder.build())
            }
            .build()

        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GroqApi::class.java)

        val response = api.listModels()
        return response.data.map { it.displayName }.filter { it.isNotBlank() }.distinct()
    }

    /** Собирает рабочий базовый URL: с учётом Account ID или переопределения. */
    fun resolveBaseUrl(provider: LlmProvider, endpoint: String): String {
        val value = endpoint.trim()
        return when {
            value.isEmpty() -> provider.baseUrl
            provider.needsAccountId -> provider.baseUrl.replace("{account_id}", value)
            else -> normalizeBaseUrl(value)
        }
    }

    /**
     * Приводит адрес своего сервера к виду, пригодному для Retrofit:
     * добавляет схему, путь `/v1` (если путь не задан) и слэш на конце.
     *
     * Пример: `192.168.1.10:11434` → `http://192.168.1.10:11434/v1/`.
     */
    fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) url = "$url/"
        // Если путь не задан, OpenAI-совместимый API живёт на /v1.
        val path = url.substringAfter("://").substringAfter('/', "")
        return if (path.isEmpty()) "${url}v1/" else url
    }

    fun settings(context: Context): SettingsRepository = SettingsRepository(context)
}
