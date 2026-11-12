package com.audionotes.app.data.model

/** Статус обработки записи. */
enum class RecordingStatus(val storageKey: String) {
    RECORDING("RECORDING"),
    TRANSCRIBING("TRANSCRIBING"),
    GENERATING("GENERATING"),
    COMPLETED("COMPLETED"),
    ERROR("ERROR");

    companion object {
        fun fromKey(key: String?): RecordingStatus =
            entries.firstOrNull { it.storageKey == key } ?: RECORDING
    }
}
