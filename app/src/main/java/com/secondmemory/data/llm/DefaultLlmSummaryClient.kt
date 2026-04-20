package com.secondmemory.data.llm

import com.secondmemory.domain.llm.LlmSummaryClient
import com.secondmemory.domain.model.AIProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Default implementation of LlmSummaryClient supporting multiple providers.
 */
class DefaultLlmSummaryClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) : LlmSummaryClient {
    override suspend fun summarizeDay(
        dayKey: String,
        rawJson: String,
        provider: AIProvider,
        baseUrl: String,
        apiKey: String,
        model: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() && provider != AIProvider.CUSTOM) {
            throw IllegalStateException("API key is required for ${provider.displayName} summaries.")
        }

        val prompt = buildPrompt(dayKey = dayKey, rawJson = rawJson)
        val requestBody = buildRequestBody(provider, model, prompt)

        val url = when (provider) {
            AIProvider.GEMINI -> "$baseUrl/models/$model:generateContent?key=$apiKey"
            AIProvider.ANTHROPIC -> "$baseUrl/messages"
            else -> "$baseUrl/chat/completions"
        }

        val requestBuilder = Request.Builder().url(url)
        
        when (provider) {
            AIProvider.GEMINI -> { /* API key in URL */ }
            AIProvider.ANTHROPIC -> {
                requestBuilder.addHeader("x-api-key", apiKey)
                requestBuilder.addHeader("anthropic-version", "2023-06-01")
            }
            else -> {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
        }

        val request = requestBuilder
            .post(requestBody.toString().toRequestBody(JSON.toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("${provider.displayName} API failed with ${response.code}: $body")
        }

        val text = extractText(provider, body)

        if (text.isBlank()) {
            throw IllegalStateException("${provider.displayName} API returned an empty summary.")
        }

        text.trim()
    }

    override suspend fun testConnection(
        provider: AIProvider,
        baseUrl: String,
        apiKey: String,
        model: String
    ) = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() && provider != AIProvider.CUSTOM) {
            throw IllegalStateException("API key is required for ${provider.displayName}.")
        }

        val prompt = "Reply with the single word OK."
        val requestBody = buildRequestBody(provider, model, prompt)

        val url = when (provider) {
            AIProvider.GEMINI -> "$baseUrl/models/$model:generateContent?key=$apiKey"
            AIProvider.ANTHROPIC -> "$baseUrl/messages"
            else -> "$baseUrl/chat/completions"
        }

        val requestBuilder = Request.Builder().url(url)
        
        when (provider) {
            AIProvider.GEMINI -> { /* API key in URL */ }
            AIProvider.ANTHROPIC -> {
                requestBuilder.addHeader("x-api-key", apiKey)
                requestBuilder.addHeader("anthropic-version", "2023-06-01")
            }
            else -> {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
        }

        val request = requestBuilder
            .post(requestBody.toString().toRequestBody(JSON.toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("${provider.displayName} validation failed with ${response.code}: $body")
        }
    }

    override suspend fun fetchModels(
        provider: AIProvider,
        baseUrl: String,
        apiKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        val url = when (provider) {
            AIProvider.GEMINI -> "$baseUrl/models?key=$apiKey"
            else -> "$baseUrl/models"
        }

        val requestBuilder = Request.Builder().url(url)
        when (provider) {
            AIProvider.GEMINI -> { /* handled in URL */ }
            AIProvider.ANTHROPIC -> {
                requestBuilder.addHeader("x-api-key", apiKey)
                requestBuilder.addHeader("anthropic-version", "2023-06-01")
            }
            else -> {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
        }

        val request = requestBuilder.get().build()
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            return@withContext emptyList<String>()
        }

        val models = mutableListOf<String>()
        val root = JSONObject(body)
        
        if (provider == AIProvider.GEMINI) {
            val modelsArray = root.optJSONArray("models")
            if (modelsArray != null) {
                for (i in 0 until modelsArray.length()) {
                    val m = modelsArray.getJSONObject(i).optString("name")
                    // remove "models/" prefix
                    models.add(m.substringAfter("models/"))
                }
            }
        } else {
            val dataArray = root.optJSONArray("data")
            if (dataArray != null) {
                for (i in 0 until dataArray.length()) {
                    models.add(dataArray.getJSONObject(i).optString("id"))
                }
            }
        }
        models.sorted()
    }

    /**
     * Builds the JSON request body for the specific AI provider.
     */
    private fun buildRequestBody(provider: AIProvider, model: String, prompt: String): JSONObject {
        return when (provider) {
            AIProvider.GEMINI -> {
                JSONObject().put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put("text", prompt)
                            )
                        )
                    )
                )
            }
            AIProvider.ANTHROPIC -> {
                JSONObject()
                    .put("model", model)
                    .put("messages", JSONArray().put(
                        JSONObject().put("role", "user").put("content", prompt)
                    ))
                    .put("max_tokens", 4096)
            }
            else -> {
                // OpenAI compatible format
                JSONObject()
                    .put("model", model)
                    .put("messages", JSONArray().put(
                        JSONObject().put("role", "user").put("content", prompt)
                    ))
            }
        }
    }

    /**
     * Extracts the response text from the provider's JSON response body.
     */
    private fun extractText(provider: AIProvider, body: String): String {
        val root = JSONObject(body)
        return when (provider) {
            AIProvider.GEMINI -> {
                root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    .orEmpty()
            }
            AIProvider.ANTHROPIC -> {
                root.optJSONArray("content")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    .orEmpty()
            }
            else -> {
                root.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
            }
        }
    }

    /**
     * Builds a markdown-focused instruction prompt for daily summarization.
     */

    /**
     * The base system prompt template used for daily summarization.
     */
    var prompt = """
        You are generating a structured daily journal summary from raw thought logs. 

        Input: JSON containing timestamped thoughts captured throughout a single day. 

        Instructions: 
        - Return valid markdown only. 
        - Be concise but meaningful. Target ~150–300 words total. 
        - Remove noise, repetition, and low-value thoughts. 
        - Infer intent where needed, but do not hallucinate new events. 
        - Merge similar thoughts into a single idea. 
        - Preserve chronological flow where helpful. 

        Output format: 

        ## Summary of the day 
        Write a clear, narrative-style summary of the day as a cohesive story. Focus on key activities, themes, and mindset. 

        ## Achievements 
        List concrete things completed or meaningful progress made. 
        - Use bullet points 
        - Only include items with clear completion or progress 

        ## Things to do 
        List actionable follow-ups or pending tasks inferred from the thoughts. 
        - Keep each item short and specific 
        - No more than 10 items
    """.trimIndent()

    /**
     * Combines the system prompt with the specific day's data into a final prompt string.
     */
    private fun buildPrompt(dayKey: String, rawJson: String): String {
        return """
            $prompt
            Day Key: $dayKey

            Raw JSON:
            $rawJson
        """.trimIndent()
    }

    private companion object {
        const val JSON = "application/json; charset=utf-8"
    }
}
