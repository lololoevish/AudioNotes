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
     */
    fun summaryGenerator(
        provider: LlmProvider,
        apiKey: String,
        model: String
    ): SummaryGenerator {
        val client = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                chain.proceed(request)
            }
            .build()

        val api = Retrofit.Builder()
            .baseUrl(provider.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GroqApi::class.java)

        return SummaryGenerator(api, provider, apiKey, model)
    }

    fun settings(context: Context): SettingsRepository = SettingsRepository(context)
}
