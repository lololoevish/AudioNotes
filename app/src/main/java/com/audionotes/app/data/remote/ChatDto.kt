package com.audionotes.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.2,
    @SerialName("max_tokens") val maxTokens: Int = 8192,
    @SerialName("top_p") val topP: Double = 0.9
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatResponse(
    val choices: List<ChatChoice> = emptyList(),
    val error: ApiError? = null
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
