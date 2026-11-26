package com.audionotes.app.data.llm

import com.audionotes.app.core.ServiceLocator
import com.audionotes.app.data.model.LectureType
import com.audionotes.app.data.remote.LlmProvider
import com.audionotes.app.data.remote.PromptFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryGeneratorTest {

    private val generator = SummaryGenerator(
        api = object : com.audionotes.app.data.remote.GroqApi {
            override suspend fun createChatCompletion(
                request: com.audionotes.app.data.remote.ChatRequest
            ): com.audionotes.app.data.remote.ChatResponse =
                com.audionotes.app.data.remote.ChatResponse()

            override suspend fun listModels(): com.audionotes.app.data.remote.ModelsResponse =
                com.audionotes.app.data.remote.ModelsResponse()
        },
        provider = LlmProvider.MISTRAL,
        apiKey = "test-key",
        model = ""
    )

    @Test
    fun `short text is a single chunk`() {
        val text = "Короткая лекция про теорему Пифагора."
        val chunks = generator.chunk(text)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks.first())
    }

    @Test
    fun `long text is split into chunks within limit`() {
        val paragraph = "Теорема о производной. ".repeat(200) + "\n"
        val text = paragraph.repeat(6)
        val chunks = generator.chunk(text)

        assertTrue("Ожидалось несколько частей", chunks.size > 1)
        assertTrue(
            "Часть превышает лимит: ${chunks.maxOf { it.length }}",
            chunks.all { it.length <= SummaryGenerator.CHUNK_CHARS + 500 }
        )
        assertTrue("Текст потерян", chunks.joinToString("").contains("Теорема о производной"))
    }

    @Test
    fun `chunking keeps every non blank paragraph`() {
        val paragraphs = (1..120).map { "Абзац номер $it про квантовую механику." }
        val chunks = generator.chunk(paragraphs.joinToString("\n"))

        paragraphs.forEach { paragraph ->
            assertTrue(
                "Не найден абзац: $paragraph",
                chunks.any { it.contains(paragraph) }
            )
        }
    }

    @Test
    fun `prompts differ by lecture type and forbid tables`() {
        val exact = PromptFactory.systemPrompt(LectureType.EXACT_SCIENCES)
        val humanities = PromptFactory.systemPrompt(LectureType.HUMANITIES)

        assertTrue("Промпты должны отличаться", exact != humanities)
        assertTrue("Нужно требование LaTeX", exact.contains("LaTeX"))
        assertTrue("Запрет таблиц", exact.contains("таблицы"))
        assertTrue("Запрет вводных слов", humanities.contains("вводные слова"))
    }

    @Test
    fun `providers keep openai compatible base urls`() {
        LlmProvider.entries.forEach { provider ->
            assertTrue(
                "Некорректный базовый URL у ${provider.id}",
                provider.baseUrl.startsWith("http") && provider.baseUrl.endsWith("/")
            )
            // У своего сервера модель по умолчанию не задаётся: её выбирает пользователь.
            if (!provider.baseUrlRequired) {
                assertTrue("Пустая модель по умолчанию у ${provider.id}", provider.defaultModel.isNotBlank())
            }
        }
    }

    @Test
    fun `custom server address is normalized for retrofit`() {
        assertEquals(
            "http://192.168.1.10:11434/v1/",
            ServiceLocator.normalizeBaseUrl("192.168.1.10:11434")
        )
        assertEquals(
            "http://192.168.1.10:11434/v1/",
            ServiceLocator.normalizeBaseUrl("http://192.168.1.10:11434")
        )
        assertEquals(
            "https://example.com/api/v1/",
            ServiceLocator.normalizeBaseUrl("https://example.com/api/v1")
        )
        assertEquals("", ServiceLocator.normalizeBaseUrl("   "))
    }

    @Test
    fun `custom server does not require api key`() {
        assertTrue(
            "Своему серверу ключ не нужен",
            !LlmProvider.CUSTOM.apiKeyRequired
        )
        assertTrue(
            "Адрес своего сервера нужно ввести вручную",
            LlmProvider.CUSTOM.baseUrlRequired
        )
        assertTrue(
            "Свой сервер должен работать из России",
            LlmProvider.CUSTOM.worksFromRussia
        )
    }

    @Test
    fun `groq is marked as blocked from russia`() {
        assertTrue(
            "Groq должен быть помечен как недоступный из России",
            !LlmProvider.GROQ.worksFromRussia
        )
        assertTrue(
            "Провайдер по умолчанию должен работать из России",
            LlmProvider.default().worksFromRussia
        )
    }
}
