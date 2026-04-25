package com.secondmemory.background

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import java.io.IOException

/**
 * Maximum total number of attempts (initial + retries) allowed for background jobs.
 */
internal const val MAX_ATTEMPTS = 3

/**
 * Returns true when a background job failure looks transient and should be retried.
 */
internal fun shouldRetryWork(error: Throwable, runAttemptCount: Int): Boolean {
    // runAttemptCount starts at 0 for the first run. 
    // If runAttemptCount is 2, it means this is the 3rd attempt.
    if (runAttemptCount + 1 >= MAX_ATTEMPTS) return false

    val root = rootCause(error)

    if (root is IOException) return true

    if (root is GoogleJsonResponseException) {
        val code = root.statusCode
        if (code == 429 || code in 500..599) return true
    }

    val message = root.message.orEmpty().lowercase()
    if ("timeout" in message || "timed out" in message || "temporar" in message) {
        return true
    }

    return TRANSIENT_HTTP_CODE_REGEX.containsMatchIn(message)
}

/**
 * Walks the cause chain to the original throwable.
 */
private fun rootCause(error: Throwable): Throwable {
    var current = error
    while (current.cause != null && current.cause !== current) {
        current = current.cause!!
    }
    return current
}

private val TRANSIENT_HTTP_CODE_REGEX = Regex("\\b(429|5\\d\\d)\\b")
