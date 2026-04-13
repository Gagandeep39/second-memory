package com.secondmemory.domain.model

/**
 * Represents one daily summary markdown file shown in Daily View.
 */
data class DailySummaryFile(
    val fileName: String,
    val dayKey: String,
    val preview: String,
)
