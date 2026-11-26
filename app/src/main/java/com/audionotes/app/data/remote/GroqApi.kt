package com.audionotes.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface GroqApi {

    @POST("chat/completions")
    suspend fun createChatCompletion(@Body request: ChatRequest): ChatResponse

    /** Список моделей, доступных на сервере (Ollama, vLLM, LM Studio, TGI). */
    @GET("models")
    suspend fun listModels(): ModelsResponse
}
