package com.audionotes.app.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File

/**
 * Кнопка прослушивания аудиофайла записи.
 *
 * Файл читается через MediaPlayer: `prepareAsync()` — чтобы не блокировать
 * интерфейс на длинных записях. Поддерживает паузу и перемотку в начало
 * по окончании проигрывания.
 */
@Composable
fun AudioPlaybackControl(
    audioPath: String,
    label: String,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {}
) {
    var player by remember(audioPath) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(audioPath) { mutableStateOf(false) }
    var isPreparing by remember(audioPath) { mutableStateOf(false) }
    var positionMs by remember(audioPath) { mutableLongStateOf(0L) }
    var durationMs by remember(audioPath) { mutableLongStateOf(0L) }

    DisposableEffect(audioPath) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    // Обновляем позицию воспроизведения, пока играет.
    LaunchedEffect(isPlaying, player) {
        while (isPlaying) {
            positionMs = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
            delay(300)
        }
    }

    fun toggle() {
        val current = player
        if (current == null) {
            val file = File(audioPath)
            if (!file.exists()) {
                onError("Аудиофайл не найден")
                return
            }
            isPreparing = true
            val created = MediaPlayer()
            try {
                created.setDataSource(audioPath)
                created.setOnPreparedListener { prepared ->
                    isPreparing = false
                    durationMs = prepared.duration.toLong().coerceAtLeast(0L)
                    runCatching { prepared.start() }
                    isPlaying = true
                }
                created.setOnCompletionListener { finished ->
                    isPlaying = false
                    positionMs = 0L
                    runCatching { finished.seekTo(0) }
                }
                created.setOnErrorListener { failed, _, _ ->
                    isPreparing = false
                    isPlaying = false
                    runCatching { failed.release() }
                    player = null
                    onError("Не удалось воспроизвести запись")
                    true
                }
                created.prepareAsync()
                player = created
            } catch (e: Exception) {
                isPreparing = false
                runCatching { created.release() }
                player = null
                onError("Не удалось открыть аудиофайл: ${e.message ?: "неизвестная ошибка"}")
            }
        } else if (isPlaying) {
            runCatching { current.pause() }
            isPlaying = false
        } else {
            runCatching { current.start() }
                .onFailure { onError("Не удалось продолжить воспроизведение") }
            isPlaying = true
        }
    }

    OutlinedButton(
        onClick = { toggle() },
        enabled = !isPreparing,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(text = label)
            if (isPlaying || isPreparing) {
                Text(
                    text = " · ${formatPlaybackTime(positionMs, durationMs)}",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private fun formatPlaybackTime(positionMs: Long, durationMs: Long): String {
    val totalSec = durationMs / 1000
    val curSec = positionMs / 1000
    fun fmt(value: Long): String {
        val m = value / 60
        val s = value % 60
        return String.format("%02d:%02d", m, s)
    }
    return if (totalSec > 0) "${fmt(curSec)} / ${fmt(totalSec)}" else fmt(curSec)
}
