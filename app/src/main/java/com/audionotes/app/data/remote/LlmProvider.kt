package com.audionotes.app.data.remote

/**
 * Провайдеры LLM с OpenAI-совместимым API.
 *
 * Все перечисленные работают по протоколу `POST /chat/completions`, поэтому
 * меняется только базовый URL и модель.
 *
 * Доступность проверена из России (октябрь 2026, IP московского провайдера) —
 * запрос доходил до авторизации провайдера, то есть гео-блокировки не было.
 *
 * - [CLOUDFLARE] — бесплатно 10 000 нейронов в сутки, без карты.
 * - [SILICONFLOW] — часть моделей бесплатна (¥0), но регистрация может
 *   требовать номер телефона КНР.
 * - [GROQ] — бесплатный тариф есть, но IP из России отсекается (403).
 * - [MISTRAL], [DEEPSEEK] — не бесплатны, оставлены как запасные варианты.
 */
enum class LlmProvider(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val freeModels: List<String>,
    val freeTierNote: String,
    val consoleUrl: String,
    val worksFromRussia: Boolean,
    val needsAccountId: Boolean = false,
    /** Требуется ли API-ключ: у своего сервера он обычно не нужен. */
    val apiKeyRequired: Boolean = true,
    /** Требуется ли адрес сервера: у «своего сервера» его вводят вручную. */
    val baseUrlRequired: Boolean = false
) {
    CLOUDFLARE(
        id = "cloudflare",
        displayName = "Cloudflare Workers AI",
        baseUrl = "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/v1/",
        defaultModel = "@cf/meta/llama-3.1-8b-instruct-fast",
        freeModels = listOf(
            "@cf/meta/llama-3.1-8b-instruct-fast",
            "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
            "@cf/zai-org/glm-4.7-flash",
            "@cf/google/gemma-4-26b-a4b-it",
            "@cf/openai/gpt-oss-120b"
        ),
        freeTierNote = "Бесплатно 10 000 нейронов в сутки, карта не нужна",
        consoleUrl = "https://dash.cloudflare.com/",
        worksFromRussia = true,
        needsAccountId = true
    ),

    SILICONFLOW(
        id = "siliconflow",
        displayName = "SiliconFlow",
        baseUrl = "https://api.siliconflow.cn/v1/",
        defaultModel = "Qwen/Qwen3-8B",
        freeModels = listOf(
            "Qwen/Qwen3-8B",
            "Qwen/Qwen2.5-7B-Instruct",
            "THUDM/glm-4-9b-chat",
            "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B"
        ),
        freeTierNote = "Есть модели с ценой ¥0; регистрация может требовать номер КНР",
        consoleUrl = "https://cloud.siliconflow.cn/",
        worksFromRussia = true
    ),

    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1/",
        defaultModel = "deepseek-chat",
        freeModels = emptyList(),
        freeTierNote = "Не бесплатный, но самый дешёвый из списка",
        consoleUrl = "https://platform.deepseek.com/",
        worksFromRussia = true
    ),

    MISTRAL(
        id = "mistral",
        displayName = "Mistral AI",
        baseUrl = "https://api.mistral.ai/v1/",
        defaultModel = "mistral-small-latest",
        freeModels = emptyList(),
        freeTierNote = "Бесплатный тариф с лимитами; по отзывам бывают проблемы из РФ",
        consoleUrl = "https://console.mistral.ai/",
        worksFromRussia = true
    ),

    GROQ(
        id = "groq",
        displayName = "Groq",
        baseUrl = "https://api.groq.com/openai/v1/",
        defaultModel = "llama-3.3-70b-versatile",
        freeModels = emptyList(),
        freeTierNote = "Бесплатный тариф, но из России отдаёт 403 — нужен VPN",
        consoleUrl = "https://console.groq.com/",
        worksFromRussia = false
    ),

    /**
     * Собственный сервер с нейросетью: Ollama, vLLM, LM Studio, llama.cpp,
     * TGI и любой другой OpenAI-совместимый API.
     *
     * Адрес и название модели вводятся пользователем, ключ обычно не требуется.
     */
    CUSTOM(
        id = "custom",
        displayName = "Свой сервер (OpenAI-совместимый)",
        baseUrl = "http://127.0.0.1:11434/v1/",
        defaultModel = "",
        freeModels = emptyList(),
        freeTierNote = "Ollama, vLLM, LM Studio: адрес и модель вводятся вручную",
        consoleUrl = "https://ollama.com/library",
        worksFromRussia = true,
        apiKeyRequired = false,
        baseUrlRequired = true
    );

    companion object {
        val DEFAULT: LlmProvider = CLOUDFLARE

        /** Провайдер по умолчанию: из local.properties, иначе — Cloudflare. */
        fun default(): LlmProvider =
            entries.firstOrNull { it.id == com.audionotes.app.BuildConfig.LLM_PROVIDER_ID }
                ?: DEFAULT

        fun fromId(id: String?): LlmProvider =
            entries.firstOrNull { it.id == id } ?: default()
    }
}
