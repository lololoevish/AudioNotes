package com.audionotes.app.data

import com.audionotes.app.data.local.RecordingDao
import com.audionotes.app.data.local.RecordingEntity
import com.audionotes.app.data.model.LectureType
import com.audionotes.app.data.model.RecordingStatus
import kotlinx.coroutines.flow.Flow
import java.io.File

class RecordingRepository(private val dao: RecordingDao) {

    fun observeAll(): Flow<List<RecordingEntity>> = dao.observeAll()

    fun observeById(id: String): Flow<RecordingEntity?> = dao.observeById(id)

    suspend fun get(id: String): RecordingEntity? = dao.getById(id)

    suspend fun create(
        id: String,
        title: String,
        lectureType: LectureType,
        audioFilePath: String,
        durationSec: Long
    ): RecordingEntity {
        val entity = RecordingEntity(
            id = id,
            title = title,
            createdAt = System.currentTimeMillis(),
            durationSec = durationSec,
            lectureType = lectureType.storageKey,
            audioFilePath = audioFilePath,
            transcript = "",
            summaryMd = "",
            status = RecordingStatus.RECORDING.storageKey,
            errorMessage = null
        )
        dao.insert(entity)
        return entity
    }

    suspend fun updateTranscript(id: String, transcript: String) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(transcript = transcript, status = RecordingStatus.TRANSCRIBING.storageKey))
    }

    suspend fun updateStatus(id: String, status: RecordingStatus, error: String? = null) {
        dao.updateStatus(id, status.storageKey, error)
    }

    suspend fun updateSummary(id: String, summaryMd: String) {
        val entity = dao.getById(id) ?: return
        dao.update(
            entity.copy(
                summaryMd = summaryMd,
                status = RecordingStatus.COMPLETED.storageKey,
                errorMessage = null
            )
        )
    }

    suspend fun updateTitle(id: String, title: String) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(title = title))
    }

    suspend fun delete(entity: RecordingEntity) {
        dao.delete(entity)
        runCatching { File(entity.audioFilePath).delete() }
    }
}
