package com.secondmemory.domain.model

/**
 * A single captured thought that belongs to a specific day file.
 */
data class Thought(
    val id: String,
    val timestampMillis: Long,
    val text: String,
    val source: ThoughtSource,
)

/**
 * Indicates how a thought was captured.
 */
enum class ThoughtSource {
    SPEECH,
    MANUAL,
}
