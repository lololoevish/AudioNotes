package com.audionotes.app.data.remote

/**
 * Провайдеры LLM с OpenAI-совместимым API.
 *
 * Все перечисленные работают по протоколу `POST /chat/completions`, поэтому
 * меняется только базовый URL и модель.
 *
 * Проверено из России (октябрь 2026, IP московского провайдера):
 * Groq отдаёт `403 Forbidden` на edge, остальные — живой API (401 без ключа).
 */
enum class LlmProvider(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val freeTierNote: String,
    val consoleUrl: String,
    val worksFromRussia: Boolean
) {
    MISTRAL(
        id = "mistral",
        displayName = "Mistral AI",
        baseUrl = "https://api.mistral.ai/v1/",
        defaultModel = "mistral-small-latest",
        freeTierNote = "Бесплатный тариф Experimentations, карта не нужна",
        consoleUrl = "https://console.mistral.ai/",
        worksFromRussia = true
    ),

    SILICONFLOW(
        id = "siliconflow",
        displayName = "SiliconFlow",
        baseUrl = "https://api.siliconflow.cn/v1/",
        defaultModel = "Qwen/Qwen3-8B",
        freeTierNote = "Есть бесплатные модели с пометкой ¥0",
        consoleUrl = "https://cloud.siliconflow.cn/",
        worksFromRussia = true
    ),

    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1/",
        defaultModel = "deepseek-chat",
        freeTierNote = "Платный, но самый дешёвый из списка",
        consoleUrl = "https://platform.deepseek.com/",
        worksFromRussia = true
    ),

    GITHUB_MODELS(
        id = "github_models",
        displayName = "GitHub Models",
        baseUrl = "https://models.github.ai/inference/",
        defaultModel = "openai/gpt-4o-mini",
        freeTierNote = "Бесплатно по токену GitHub, есть лимиты в сутки",
        consoleUrl = "https://github.com/settings/tokens",
        worksFromRussia = true
    ),

    GROQ(
        id = "groq",
        displayName = "Groq",
        baseUrl = "https://api.groq.com/openai/v1/",
        defaultModel = "llama-3.3-70b-versatile",
        freeTierNote = "Бесплатный тариф, но из России отдаёт 403 — нужен VPN",
        consoleUrl = "https://console.groq.com/",
        worksFromRussia = false
    );

    companion object {
        val DEFAULT: LlmProvider = MISTRAL

        /** Провайдер по умолчанию: из local.properties, иначе — Mistral. */
        fun default(): LlmProvider =
            entries.firstOrNull { it.id == com.audionotes.app.BuildConfig.LLM_PROVIDER_ID }
                ?: DEFAULT

        fun fromId(id: String?): LlmProvider =
            entries.firstOrNull { it.id == id } ?: default()
    }
}
