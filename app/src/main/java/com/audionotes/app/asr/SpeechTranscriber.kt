package com.audionotes.app.asr

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File

/**
 * Локальное распознавание речи на устройстве (без отправки аудио в сеть).
 *
 * Основной режим — непрерывное распознавание параллельно с записью.
 * Дополнительно поддерживается разбор уже готового аудиофайла
 * через [RecognizerIntent.EXTRA_AUDIO_SOURCE] (Android 12+).
 */
class SpeechTranscriber(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var restartsWithoutText = 0

    /** Финальный распознанный фрагмент. */
    var onText: ((String) -> Unit)? = null

    /** Текущий (частичный) фрагмент. */
    var onPartial: ((String) -> Unit)? = null

    /** Ошибка распознавания (текст для показа пользователю). */
    var onFailure: ((String) -> Unit)? = null

    val isOnDeviceAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    // ------------------------------------------------------------------ live

    fun start() {
        if (running) return
        running = true
        restartsWithoutText = 0
        handler.post { ensureRecognizer()?.startListening(buildIntent()) }
    }

    fun stop() {
        running = false
        handler.post {
            recognizer?.let {
                runCatching { it.stopListening() }
                runCatching { it.destroy() }
            }
            recognizer = null
        }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        val created = try {
            if (isOnDeviceAvailable) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: Exception) {
            onFailure?.invoke("SpeechRecognizer недоступен на этом устройстве")
            return null
        }
        created.setRecognitionListener(listener)
        recognizer = created
        return created
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15_000L)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (!running) return
            // Нет речи / пауза — это штатная ситуация, просто продолжаем слушать.
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                restartsWithoutText++
                if (restartsWithoutText > 20) {
                    onFailure?.invoke("Не удалось распознать речь во время записи")
                    running = false
                    return
                }
                restart()
                return
            }
            onFailure?.invoke(describeError(error))
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) {
                restartsWithoutText = 0
                onText?.invoke(text)
            } else {
                restartsWithoutText++
            }
            if (running) restart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) onPartial?.invoke(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun restart() {
        handler.postDelayed({
            if (!running) return@postDelayed
            val rec = ensureRecognizer() ?: return@postDelayed
            runCatching { rec.startListening(buildIntent()) }
        }, 300L)
    }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Микрофон занят другим приложением (ошибка аудио)"
        SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента распознавания"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Сетевая ошибка распознавания (доступен офлайн-режим on-device)"
        SpeechRecognizer.ERROR_NO_MATCH -> "Речь не распознана"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
        SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера распознавания"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не обнаружена"
        else -> "Ошибка распознавания: $error"
    }

    // ------------------------------------------------------------------ file

    /**
     * Распознаёт уже записанный файл. Работает не на всех устройствах —
     * зависит от реализации офлайн-распознавателя в системе.
     */
    fun transcribeFile(file: File, onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onError("Разбор аудиофайла поддерживается начиная с Android 12")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        }
        val rec = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } catch (e: Exception) {
            onError("Офлайн-распознавание недоступно на этом устройстве")
            return
        }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                runCatching { rec.destroy() }
                onError(describeError(error))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.joinToString(" ")
                    ?.trim()
                    .orEmpty()
                runCatching { rec.destroy() }
                if (text.isEmpty()) onError("Речь не распознана") else onResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        runCatching { rec.startListening(intent) }
            .onFailure {
                runCatching { rec.destroy() }
                onError("Устройство не поддерживает распознавание из файла")
            }
    }
}
