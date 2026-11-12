package com.audionotes.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Запись аудио в AAC/M4A. Формат рассчитан на длинные сессии (до нескольких часов).
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(): File {
        check(recorder == null) { "Recorder is already running" }

        val dir = File(context.filesDir, "audio").apply { mkdirs() }
        val file = File(dir, "lecture_${System.currentTimeMillis()}.m4a")

        val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        instance.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(128_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder = instance
        currentFile = file
        return file
    }

    /** Останавливает запись и возвращает длительность в миллисекундах. */
    fun stop(durationMs: Long): File? {
        val instance = recorder ?: return null
        recorder = null
        return try {
            instance.stop()
            currentFile
        } catch (e: RuntimeException) {
            // Файл слишком короткий или микрофон был недоступен
            runCatching { instance.reset() }
            currentFile?.delete()
            null
        } finally {
            runCatching { instance.release() }
            if (durationMs <= 0) Unit
        }
    }

    fun cancel() {
        val instance = recorder ?: return
        recorder = null
        runCatching { instance.stop() }
        runCatching { instance.release() }
        currentFile?.delete()
        currentFile = null
    }
}
