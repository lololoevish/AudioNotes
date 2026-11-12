package com.audionotes.app.data.model

/** Тип лекции — определяет системный промпт для LLM. */
enum class LectureType(val storageKey: String) {
    EXACT_SCIENCES("EXACT_SCIENCES"),
    HUMANITIES("HUMANITIES");

    companion object {
        fun fromKey(key: String?): LectureType =
            entries.firstOrNull { it.storageKey == key } ?: EXACT_SCIENCES
    }
}
