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
    private fun buildPrompt(dayKey: String, rawJson: String): String {
        return """
            Summarize the following day's raw thought JSON for date $dayKey.
            Return valid markdown only. 
            Don't actually include the hints. 
            Must be between 10-200 lines.
            Don't force add content to increase size and don't force remove things
            Include sections:
            - ## Summary of the day
            - ## Things to do (hint: Tasks that need to be done later. Keep it in short points)
            - ## Keywords (hint: Containing important keywords from the user content)

            Raw JSON:
            $rawJson
        """.trimIndent()
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent"
        const val JSON = "application/json; charset=utf-8"
    }
}
