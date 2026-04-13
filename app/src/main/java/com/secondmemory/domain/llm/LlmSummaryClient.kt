package com.secondmemory.domain.llm

/**
 * Abstraction for generating markdown summaries from a day's raw JSON content.
 */
interface LlmSummaryClient {
    /**
     * Produces markdown summary content for the provided day and raw input payload.
     */
    suspend fun summarizeDay(dayKey: String, rawJson: String, apiKey: String): String

    /**
     * Verifies that the provided Gemini API key can reach the service successfully.
     */
    suspend fun testConnection(apiKey: String)
}
