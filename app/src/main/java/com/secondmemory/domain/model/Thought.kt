package com.secondmemory.domain.model

data class Thought(
    val id: String,
    val timestampMillis: Long,
    val text: String,
    val source: ThoughtSource,
)

enum class ThoughtSource {
    SPEECH,
    MANUAL,
}
