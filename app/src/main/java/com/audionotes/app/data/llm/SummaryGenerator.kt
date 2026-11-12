package com.audionotes.app.data.llm

import com.audionotes.app.BuildConfig
import com.audionotes.app.data.model.LectureType
import com.audionotes.app.data.remote.ChatMessage
import com.audionotes.app.data.remote.ChatRequest
import com.audionotes.app.data.remote.GroqApi
import com.audionotes.app.data.remote.PromptFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Ошибка генерации конспекта с понятной пользователю причиной. */
class SummaryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Генератор конспекта через Groq (OpenAI-совместимый API).
 *
 * Длинные лекции разбиваются на части, каждая обрабатывается отдельным запросом,
 * затем части объединяются финальным запросом (см. PRD, раздел 10).
 */
class SummaryGenerator(private val api: GroqApi) {

    suspend fun generate(
        transcript: String,
        type: LectureType,
        onStage: (Stage) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        if (BuildConfig.GROQ_API_KEY.isBlank() || BuildConfig.GROQ_API_KEY.contains("YOUR_")) {
            throw SummaryException("Не задан API-ключ Groq. Добавьте groq.api.key в local.properties.")
        }
        val text = transcript.trim()
        if (text.isEmpty()) throw SummaryException("Пустая расшифровка — нечего конспектировать.")

        val chunks = chunk(text)
        val parts = mutableListOf<String>()

        chunks.forEachIndexed { index, chunk ->
            onStage(Stage.Part(index + 1, chunks.size))
            parts += chat(
                system = PromptFactory.systemPrompt(type),
                user = PromptFactory.chunkPrompt(index + 1, chunks.size, chunk)
            )
        }

        if (parts.size == 1) return@withContext parts.first().trim()

        onStage(Stage.Merging)
        var merged = parts
        var groupSize = 4
        while (merged.size > 1 && merged.joinToString("\n").length > MERGE_INPUT_LIMIT) {
            val next = mutableListOf<String>()
            merged.chunked(groupSize).forEach { group ->
                next += if (group.size == 1) {
                    group.first()
                } else {
                    chat(
                        system = PromptFactory.MERGE_SYSTEM_PROMPT,
                        user = mergeUser(group, next.size + 1)
                    )
                }
            }
            merged = next
            groupSize = (groupSize + 2).coerceAtMost(8)
        }

        if (merged.size == 1) {
            merged.first().trim()
        } else {
            chat(
                system = PromptFactory.MERGE_SYSTEM_PROMPT,
                user = mergeUser(merged, 1)
            ).trim()
        }
    }

    private suspend fun chat(system: String, user: String): String {
        val request = ChatRequest(
            model = BuildConfig.GROQ_MODEL,
            messages = listOf(
                ChatMessage("system", system),
                ChatMessage("user", user)
            )
        )
        val response = try {
            api.createChatCompletion(request)
        } catch (e: IOException) {
            throw SummaryException("Ошибка сети при обращении к Groq API", e)
        } catch (e: retrofit2.HttpException) {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val detail = body?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            throw SummaryException("Groq API вернул ошибку ${e.code()}$detail", e)
        }
        response.error?.let { error ->
            throw SummaryException("Groq API: ${error.message ?: "неизвестная ошибка"}")
        }
        val content = response.choices.firstOrNull()?.message?.content
        if (content.isNullOrBlank()) throw SummaryException("Groq API вернул пустой ответ")
        return cleanUp(content)
    }

    /** Убирает markdown-заглушки и «водяные» вступления. */
    private fun cleanUp(content: String): String {
        var result = content.trim()
        val fences = listOf("```markdown", "```md", "```")
        for (fence in fences) {
            if (result.startsWith(fence)) {
                result = result.removePrefix(fence).trim()
                if (result.endsWith("```")) result = result.removeSuffix("```").trim()
                break
            }
        }
        return result
    }

    private fun mergeUser(group: List<String>, index: Int): String =
        "Объедини следующие фрагменты конспекта в один цельный Markdown-конспект " +
            "(группа $index). Не добавляй вводных слов и таблиц.\n\n" +
            group.joinToString("\n\n---\n\n")

    /** Делит текст на части по границам абзацев, не разрывая предложения. */
    internal fun chunk(text: String): List<String> {
        if (text.length <= CHUNK_CHARS) return listOf(text)

        val paragraphs = text.split("\n").filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (paragraph in paragraphs) {
            if (current.length + paragraph.length + 1 > CHUNK_CHARS && current.isNotEmpty()) {
                chunks += current.toString()
                current.clear()
            }
            if (paragraph.length > CHUNK_CHARS) {
                // Слишком длинный абзац — режем по предложениям
                var rest = paragraph
                while (rest.length > CHUNK_CHARS) {
                    var cut = rest.lastIndexOf(". ", CHUNK_CHARS)
                    if (cut <= 0) cut = CHUNK_CHARS
                    chunks += rest.substring(0, cut + 1)
                    rest = rest.substring(cut + 1)
                }
                if (rest.isNotBlank()) current.append(rest).append('\n')
            } else {
                current.append(paragraph).append('\n')
            }
        }
        if (current.isNotBlank()) chunks += current.toString()
        return chunks.filter { it.isNotBlank() }
    }

    sealed interface Stage {
        data class Part(val index: Int, val total: Int) : Stage
        data object Merging : Stage
    }

    companion object {
        /** ~1500 токенов за запрос — безопасный размер для Groq. */
        const val CHUNK_CHARS = 6_000

        /** Порог, после которого части объединяются итеративно. */
        const val MERGE_INPUT_LIMIT = 24_000
    }
}
