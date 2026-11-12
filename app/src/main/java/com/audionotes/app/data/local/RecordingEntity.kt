package com.audionotes.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.audionotes.app.data.model.LectureType
import com.audionotes.app.data.model.RecordingStatus

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val durationSec: Long,
    val lectureType: String,
    val audioFilePath: String,
    val transcript: String,
    val summaryMd: String,
    val status: String,
    val errorMessage: String?
) {
    val type: LectureType get() = LectureType.fromKey(lectureType)
    val state: RecordingStatus get() = RecordingStatus.fromKey(status)
}
