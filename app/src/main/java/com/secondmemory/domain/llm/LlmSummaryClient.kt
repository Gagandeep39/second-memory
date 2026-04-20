package com.secondmemory.domain.llm

import com.secondmemory.domain.model.AIProvider

/**
 * Abstraction for generating markdown summaries from a day's raw JSON content.
 */
interface LlmSummaryClient {
    /**
     * Produces markdown summary content for the provided day and raw input payload.
     */
    suspend fun summarizeDay(
        dayKey: String,
        rawJson: String,
        provider: AIProvider,
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String
    ): String

    /**
     * Verifies that the provided AI configuration can reach the service successfully.
     */
    suspend fun testConnection(
        provider: AIProvider,
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String
    )

    /**
     * Fetches the list of available models for the given provider and configuration.
     */
    suspend fun fetchModels(
        provider: AIProvider,
        baseUrl: String,
        apiKey: String
    ): List<String>
}
