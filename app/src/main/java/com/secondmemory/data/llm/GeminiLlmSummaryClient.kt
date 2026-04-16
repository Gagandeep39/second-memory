package com.secondmemory.data.llm

import com.secondmemory.domain.llm.LlmSummaryClient
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
 * Gemini REST API implementation of the summary client.
 */
class GeminiLlmSummaryClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) : LlmSummaryClient {
    override suspend fun summarizeDay(dayKey: String, rawJson: String, apiKey: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalStateException("Gemini API key is required for cloud summaries.")
        }

        val prompt = buildPrompt(dayKey = dayKey, rawJson = rawJson)
        val requestBody = JSONObject()
            .put(
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

        val request = Request.Builder()
            .url("${BASE_URL}?key=$apiKey")
            .post(requestBody.toString().toRequestBody(JSON.toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("Gemini API failed with ${response.code}: $body")
        }

        val root = JSONObject(body)
        val text = root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            .orEmpty()

        if (text.isBlank()) {
            throw IllegalStateException("Gemini API returned an empty summary.")
        }

        text.trim()
    }

    override suspend fun testConnection(apiKey: String) = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalStateException("Gemini API key is required.")
        }

        val requestBody = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", "Reply with the single word OK.")
                        )
                    )
                )
            )

        val request = Request.Builder()
            .url("${BASE_URL}?key=$apiKey")
            .post(requestBody.toString().toRequestBody(JSON.toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("Gemini API key validation failed with ${response.code}: $body")
        }
    }

    /**
     * Builds a markdown-focused instruction prompt for daily summarization.
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

    private fun buildPrompt(dayKey: String, rawJson: String): String {
        return """
            $prompt
            Day Key: $dayKey

            Raw JSON:
            $rawJson
        """.trimIndent()
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent"
        const val JSON = "application/json; charset=utf-8"
    }
}
