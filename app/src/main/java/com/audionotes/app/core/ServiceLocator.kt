package com.audionotes.app.core

import android.content.Context
import com.audionotes.app.BuildConfig
import com.audionotes.app.data.RecordingRepository
import com.audionotes.app.data.llm.SummaryGenerator
import com.audionotes.app.data.local.AudioNotesDatabase
import com.audionotes.app.data.remote.ChatRequest
import com.audionotes.app.data.remote.ChatResponse
import com.audionotes.app.data.remote.GroqApi
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

    private val groqApi: GroqApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.GROQ_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GroqApi::class.java)
    }

    fun repository(context: Context): RecordingRepository =
        RecordingRepository(AudioNotesDatabase.get(context).recordingDao())

    fun summaryGenerator(): SummaryGenerator = SummaryGenerator(groqApi)

    fun settings(context: Context): SettingsRepository = SettingsRepository(context)
}
