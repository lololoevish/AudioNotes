package com.audionotes.app.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Состояние процесса записи, доступное UI и сервису.
 *
 * Живёт в статическом объекте, потому что сервис и ViewModel — один процесс,
 * а связывать их через Binder ради MVP избыточно.
 */
data class RecordingState(
    val isRecording: Boolean = false,
    val elapsedSec: Long = 0,
    val transcript: String = "",
    val committedChars: Int = 0,
    val finished: Finished? = null,
    val error: String? = null,
    val warning: String? = null
) {
    data class Finished(
        val audioPath: String?,
        val transcript: String,
        val durationSec: Long
    )
}

object RecordingBus {

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    fun onRecordingStarted() {
        _state.value = RecordingState(isRecording = true)
    }

    fun onProgress(elapsedSec: Long, transcript: String, committedChars: Int) {
        _state.update {
            it.copy(
                isRecording = true,
                elapsedSec = elapsedSec,
                transcript = transcript,
                committedChars = committedChars
            )
        }
    }

    fun onFinished(finished: RecordingState.Finished) {
        _state.update {
            it.copy(
                isRecording = false,
                elapsedSec = finished.durationSec,
                transcript = finished.transcript,
                committedChars = finished.transcript.length,
                finished = finished,
                error = null
            )
        }
    }

    fun onError(message: String) {
        _state.update { it.copy(isRecording = false, error = message) }
    }

    /**
     * Предупреждение: запись продолжается, но что-то пошло не так
     * (например, распознаватель временно отвалился).
     */
    fun onWarning(message: String) {
        _state.update { it.copy(warning = message) }
    }

    fun consumeWarning(): String? {
        val message = _state.value.warning
        if (message != null) {
            _state.update { it.copy(warning = null) }
        }
        return message
    }

    /** Забирает результат остановки, чтобы он не обрабатывался дважды. */
    fun consumeFinished(): RecordingState.Finished? {
        val result = _state.value.finished
        if (result != null) {
            _state.update { it.copy(finished = null) }
        }
        return result
    }

    fun consumeError(): String? {
        val message = _state.value.error
        if (message != null) {
            _state.update { it.copy(error = null) }
        }
        return message
    }
}
