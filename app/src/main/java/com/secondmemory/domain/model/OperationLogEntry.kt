package com.secondmemory.domain.model

/**
 * Immutable event item used by the operations log screen.
 */
data class OperationLogEntry(
    val id: Long,
    val timestampMillis: Long,
    val category: String,
    val action: String,
    val status: String,
    val details: String? = null,
    val source: String? = null,
)
