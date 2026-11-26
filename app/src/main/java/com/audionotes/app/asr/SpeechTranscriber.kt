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
 * Распознавание речи штатными средствами телефона — без единого API-ключа.
 *
 * Используется тот же системный движок, на котором работают голосовой ввод
 * Gboard и Яндекс.Клавиатуры, то есть отдельная подписка или ключ не нужны.
 *
 * Режимы:
 * * [Mode.ON_DEVICE] — офлайн-модель на устройстве (Android 12+), аудио никуда не уходит.
 * * [Mode.SYSTEM] — системный распознаватель (тот же, что у клавиатур), если
 *   офлайн-модель не установлена или не поднимается.
 */
class SpeechTranscriber(private val context: Context) {

    enum class Mode { ON_DEVICE, SYSTEM }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var restartsWithoutText = 0

    /** Текущий режим распознавания. */
    var mode: Mode = Mode.SYSTEM
        private set

    /** Финальный распознанный фрагмент. */
    var onText: ((String) -> Unit)? = null

    /** Текущий (частичный) фрагмент. */
    var onPartial: ((String) -> Unit)? = null

    /** Ошибка распознавания (текст для показа пользователю). */
    var onFailure: ((String) -> Unit)? = null

    val isOnDeviceAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /** Доступен ли хоть какой-то распознаватель (офлайн или системный). */
    val isAnyRecognizerAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    // ------------------------------------------------------------------ live

    fun start() {
        if (running) return
        running = true
        restartsWithoutText = 0
        handler.post {
            val rec = ensureRecognizer()
            if (rec == null) {
                running = false
                return@post
            }
            runCatching { rec.startListening(buildIntent()) }
                .onFailure { restart() }
        }
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

    /**
     * Создаёт распознаватель: сначала пробуем офлайн-модель, при неудаче —
     * системный движок (он же голосовой ввод Gboard / Яндекс.Клавиатуры).
     * Ни один из вариантов не требует ключа.
     */
    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }

        if (isOnDeviceAvailable) {
            val onDevice = try {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } catch (e: Exception) {
                null
            }
            if (onDevice != null) {
                onDevice.setRecognitionListener(listener)
                recognizer = onDevice
                mode = Mode.ON_DEVICE
                return onDevice
            }
        }

        val system = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            null
        }
        if (system != null) {
            system.setRecognitionListener(listener)
            recognizer = system
            mode = Mode.SYSTEM
            return system
        }

        onFailure?.invoke(
            "На устройстве нет доступного распознавателя речи. " +
                "Установите Google Speech Services или включите голосовой ввод в клавиатуре."
        )
        return null
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

            // Отказ в доступе к микрофону — восстановиться нельзя.
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                onFailure?.invoke(describeError(error))
                running = false
                return
            }

            // Остальные ошибки (микрофон занят, распознаватель busy, сбой сервиса,
            // нет речи) — временные: продолжаем слушать, иначе расшифровка
            // оборвётся на середине лекции.
            restartsWithoutText++
            if (restartsWithoutText > 40) {
                onFailure?.invoke("Не удалось распознать речь во время записи")
                running = false
                return
            }
            if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                onFailure?.invoke(describeError(error))
            }
            restart()
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
        val descriptor = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            onError("Не удалось открыть аудиофайл")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, descriptor)
        }
        val rec = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } catch (e: Exception) {
            runCatching { descriptor.close() }
            onError("Офлайн-распознавание недоступно на этом устройстве")
            return
        }
        // Дескриптор закрываем вместе с распознавателем, иначе утекает файловый дескриптор.
        val release = {
            runCatching { rec.destroy() }
            runCatching { descriptor.close() }
        }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                release()
                onError(describeError(error))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.joinToString(" ")
                    ?.trim()
                    .orEmpty()
                release()
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
