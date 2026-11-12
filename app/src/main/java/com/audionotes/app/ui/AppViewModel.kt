package com.audionotes.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audionotes.app.asr.SpeechTranscriber
import com.audionotes.app.audio.AudioRecorder
import com.audionotes.app.core.ServiceLocator
import com.audionotes.app.data.RecordingRepository
import com.audionotes.app.data.llm.SummaryException
import com.audionotes.app.data.llm.SummaryGenerator
import com.audionotes.app.data.model.LectureType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

enum class ProcessingStep {
    TRANSCRIBING,
    AWAITING_DECISION,
    GENERATING,
    DONE
}

data class ProcessingState(
    val recordingId: String,
    val step: ProcessingStep,
    val partIndex: Int = 0,
    val partTotal: Int = 0,
    val error: String? = null
)

data class AppUiState(
    val isRecording: Boolean = false,
    val elapsedSec: Long = 0,
    val liveTranscript: String = "",
    val committedChars: Int = 0,
    val pendingAudioPath: String? = null,
    val pendingDurationSec: Long = 0,
    val processing: ProcessingState? = null,
    val completedRecordingId: String? = null,
    val notice: String? = null
)

/**
 * Общий ViewModel приложения: сессия записи, распознавание речи и генерация конспекта.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RecordingRepository =
        ServiceLocator.repository(application)

    private val generator: SummaryGenerator = ServiceLocator.summaryGenerator()

    private val recorder = AudioRecorder(application)

    private val transcriber = SpeechTranscriber(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val committed = StringBuilder()
    private var tickerJob: Job? = null
    private var pipelineJob: Job? = null

    init {
        transcriber.onText = { fragment ->
            committed.append(fragment).append(' ')
            _uiState.update {
                it.copy(
                    committedChars = committed.length,
                    liveTranscript = committed.toString()
                )
            }
        }
        transcriber.onPartial = { partial ->
            _uiState.update { it.copy(liveTranscript = committed.toString() + partial) }
        }
        transcriber.onFailure = { message ->
            _uiState.update { it.copy(notice = message) }
        }
    }

    // ------------------------------------------------------------- recording

    fun startRecording() {
        if (_uiState.value.isRecording) return
        committed.clear()
        val file = try {
            recorder.start()
        } catch (e: Exception) {
            _uiState.update { it.copy(notice = "Не удалось начать запись: ${e.message}") }
            return
        }
        transcriber.start()
        _uiState.update {
            it.copy(
                isRecording = true,
                elapsedSec = 0,
                liveTranscript = "",
                committedChars = 0,
                pendingAudioPath = null,
                pendingDurationSec = 0,
                notice = null
            )
        }
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _uiState.update { state -> state.copy(elapsedSec = state.elapsedSec + 1) }
            }
        }
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) return
        tickerJob?.cancel()
        transcriber.stop()

        val elapsed = _uiState.value.elapsedSec
        val file = recorder.stop(elapsed * 1000)
        if (file == null) {
            _uiState.update {
                it.copy(isRecording = false, notice = "Запись слишком короткая или микрофон недоступен")
            }
            return
        }
        _uiState.update {
            it.copy(
                isRecording = false,
                pendingAudioPath = file.absolutePath,
                pendingDurationSec = elapsed,
                liveTranscript = committed.toString()
            )
        }
    }

    fun discardPendingRecording() {
        _uiState.value.pendingAudioPath?.let { runCatching { File(it).delete() } }
        committed.clear()
        _uiState.update {
            it.copy(
                pendingAudioPath = null,
                pendingDurationSec = 0,
                liveTranscript = "",
                committedChars = 0
            )
        }
    }

    // ------------------------------------------------------------ processing

    fun onLectureTypeSelected(type: LectureType) {
        val path = _uiState.value.pendingAudioPath ?: return
        val duration = _uiState.value.pendingDurationSec
        val id = UUID.randomUUID().toString()

        viewModelScope.launch {
            val title = defaultTitle()
            repository.create(
                id = id,
                title = title,
                lectureType = type,
                audioFilePath = path,
                durationSec = duration
            )
            _uiState.update {
                it.copy(
                    pendingAudioPath = null,
                    pendingDurationSec = 0,
                    processing = ProcessingState(id, ProcessingStep.TRANSCRIBING)
                )
            }
            transcribe(id, type)
        }
    }

    private suspend fun transcribe(id: String, type: LectureType) {
        var transcript = committed.toString().trim()
        val entity = repository.get(id)

        if (transcript.isEmpty()) {
            val file = entity?.audioFilePath?.let { File(it) }
            val result = if (file != null && file.exists()) {
                transcribeFile(file)
            } else {
                Result.failure(SummaryException("Аудиофайл не найден"))
            }
            transcript = result.getOrElse { error ->
                fail(id, error.message ?: "Не удалось распознать речь")
                return
            }
        }

        repository.updateTranscript(id, transcript)
        repository.updateStatus(id, com.audionotes.app.data.model.RecordingStatus.TRANSCRIBING)
        _uiState.update { it.copy(processing = ProcessingState(id, ProcessingStep.AWAITING_DECISION)) }
    }

    private suspend fun transcribeFile(file: File): Result<String> =
        suspendCancellableCoroutine { continuation ->
            transcriber.transcribeFile(
                file = file,
                onResult = { text -> if (continuation.isActive) continuation.resume(Result.success(text)) },
                onError = { message -> if (continuation.isActive) continuation.resume(Result.failure(SummaryException(message))) }
            )
        }

    /** Запуск генерации конспекта по уже сохранённой расшифровке. */
    fun generate(id: String) {
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            val entity = repository.get(id) ?: run {
                fail(id, "Запись не найдена")
                return@launch
            }
            val type = entity.type
            _uiState.update { it.copy(processing = ProcessingState(id, ProcessingStep.GENERATING)) }
            repository.updateStatus(id, com.audionotes.app.data.model.RecordingStatus.GENERATING)

            try {
                val summary = generator.generate(entity.transcript, type) { stage ->
                    when (stage) {
                        is SummaryGenerator.Stage.Part ->
                            _uiState.update {
                                it.copy(
                                    processing = (it.processing ?: ProcessingState(id, ProcessingStep.GENERATING))
                                        .copy(partIndex = stage.index, partTotal = stage.total)
                                )
                            }
                        SummaryGenerator.Stage.Merging ->
                            _uiState.update {
                                it.copy(
                                    processing = (it.processing ?: ProcessingState(id, ProcessingStep.GENERATING))
                                        .copy(step = ProcessingStep.GENERATING, partIndex = 0, partTotal = 0)
                                )
                            }
                    }
                }
                repository.updateSummary(id, summary)
                _uiState.update {
                    it.copy(
                        processing = null,
                        completedRecordingId = id
                    )
                }
            } catch (e: SummaryException) {
                fail(id, e.message ?: "Ошибка генерации конспекта")
            } catch (e: Exception) {
                fail(id, e.message ?: "Неизвестная ошибка генерации")
            }
        }
    }

    /** Сохраняет отредактированную расшифровку и запускает генерацию. */
    fun saveTranscriptAndGenerate(id: String, transcript: String) {
        viewModelScope.launch {
            repository.updateTranscript(id, transcript)
            generate(id)
        }
    }

    fun retry(id: String) {
        viewModelScope.launch {
            val entity = repository.get(id) ?: return@launch
            repository.updateStatus(id, com.audionotes.app.data.model.RecordingStatus.TRANSCRIBING)
            _uiState.update { it.copy(processing = ProcessingState(id, ProcessingStep.GENERATING)) }
            generate(id)
        }
    }

    fun fail(id: String, message: String) {
        viewModelScope.launch {
            repository.updateStatus(id, com.audionotes.app.data.model.RecordingStatus.ERROR, message)
        }
        _uiState.update {
            it.copy(processing = (it.processing ?: ProcessingState(id, ProcessingStep.DONE)).copy(error = message))
        }
    }

    fun consumeCompleted() {
        _uiState.update { it.copy(completedRecordingId = null) }
    }

    fun consumeNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun recordingsFlow() = repository.observeAll()

    fun recordingFlow(id: String) = repository.observeById(id)

    fun deleteRecording(id: String) {
        viewModelScope.launch {
            repository.get(id)?.let { repository.delete(it) }
        }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        pipelineJob?.cancel()
        transcriber.stop()
        recorder.cancel()
        super.onCleared()
    }

    private fun defaultTitle(): String {
        val format = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("ru"))
        return "Лекция ${format.format(Date())}"
    }
}
