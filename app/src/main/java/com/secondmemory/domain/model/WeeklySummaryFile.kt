package com.secondmemory.domain.model

/**
 * Represents one weekly summary markdown file shown in Weekly View.
 */
data class WeeklySummaryFile(
    val fileName: String,
    val weekKey: String,
    val preview: String,
)
