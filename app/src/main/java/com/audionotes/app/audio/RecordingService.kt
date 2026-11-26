package com.audionotes.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.audionotes.app.MainActivity
import com.audionotes.app.R
import com.audionotes.app.asr.SpeechTranscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground-сервис записи лекции.
 *
 * Нужен, чтобы запись продолжалась, когда приложение свёрнуто или экран погас —
 * иначе 5-часовая лекция (цель PRD) не выдержит сворачивания.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var recorder: AudioRecorder? = null
    private var transcriber: SpeechTranscriber? = null
    private var ticker: Job? = null
    private var committed = StringBuilder()
    private var elapsedSec = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        transcriber?.stop()
        recorder?.cancel()
        transcriber = null
        recorder = null
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- запись

    private fun startRecording() {
        if (recorder != null) return

        // startForeground обязан быть вызван сразу после startForegroundService.
        // На Android 14 он бросает SecurityException, если доступ к микрофону
        // отозван, — ловим, иначе приложение упадёт.
        try {
            startForegroundCompat(buildNotification(elapsedSec = 0, chars = 0))
        } catch (e: Exception) {
            RecordingBus.onError("Не удалось запустить запись в фоне: ${e.message ?: "нет доступа к микрофону"}")
            stopSelf()
            return
        }

        val audioRecorder = AudioRecorder(this)
        try {
            audioRecorder.start()
        } catch (e: Exception) {
            RecordingBus.onError("Не удалось начать запись: ${e.message}")
            stopForegroundCompat()
            stopSelf()
            return
        }

        recorder = audioRecorder
        committed = StringBuilder()
        elapsedSec = 0

        val speech = SpeechTranscriber(this)
        transcriber = speech
        speech.onText = { fragment ->
            committed.append(fragment).append(' ')
            publishProgress()
        }
        speech.onPartial = { partial ->
            publishProgress(partial = partial)
        }
        speech.onFailure = { message ->
            // Распознаватель временно отвалился — запись продолжается,
            // поэтому это предупреждение, а не ошибка.
            RecordingBus.onWarning(message)
        }
        speech.start()

        RecordingBus.onRecordingStarted()

        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                delay(1_000)
                elapsedSec++
                publishProgress()
            }
        }
    }

    private fun stopRecording() {
        val current = recorder
        if (current == null) {
            stopSelf()
            return
        }

        ticker?.cancel()
        ticker = null
        transcriber?.stop()

        val file = current.stop(elapsedSec * 1000)
        recorder = null
        transcriber = null

        if (file == null) {
            RecordingBus.onError("Запись слишком короткая или микрофон недоступен")
            stopForegroundCompat()
            stopSelf()
            return
        }

        RecordingBus.onFinished(
            RecordingState.Finished(
                audioPath = file.absolutePath,
                transcript = committed.toString().trim(),
                durationSec = elapsedSec
            )
        )

        stopForegroundCompat()
        stopSelf()
    }

    private fun publishProgress(partial: String = "") {
        val committedText = committed.toString()
        val transcript = committedText + partial
        RecordingBus.onProgress(
            elapsedSec = elapsedSec,
            transcript = transcript,
            committedChars = committedText.length
        )
        notificationManager()?.let { manager ->
            runCatching { manager.notify(NOTIFICATION_ID, buildNotification(elapsedSec, committedText.length)) }
        }
    }

    // ----------------------------------------------------------- уведомление

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_recording),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_recording_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(elapsedSec: Long, chars: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val time = formatTime(elapsedSec)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(getString(R.string.notification_recording_text, time, chars))
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_stat_record,
                getString(R.string.notification_stop),
                stopIntent
            )
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun notificationManager(): NotificationManagerCompat? =
        NotificationManagerCompat.from(this)

    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format("%02d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    companion object {
        const val ACTION_START = "com.audionotes.app.action.START_RECORDING"
        const val ACTION_STOP = "com.audionotes.app.action.STOP_RECORDING"

        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            // Сервис обычно уже работает — startService просто доставит команду.
            // Если сервис успели убить, а уведомление ещё висит, запуск из фона
            // бросит исключение — его перехватит вызывающий код.
            context.startService(intent)
        }
    }
}
